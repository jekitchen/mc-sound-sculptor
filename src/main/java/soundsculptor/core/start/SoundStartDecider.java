package soundsculptor.core.start;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soundsculptor.core.SoundRange;
import soundsculptor.core.cue.CueRules;
import soundsculptor.core.cue.CueResolution;
import soundsculptor.core.cue.CueResolver;
import soundsculptor.core.cue.CueSettings;
import soundsculptor.core.limit.RepeatLimiter;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.sets.EntryResolver;
import soundsculptor.core.sets.Layer;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.WhereYouAre;
import soundsculptor.core.zones.Zone;

/**
 * Decides one sound start from end to end (PRD section 7 flowchart), so the sound hook makes one call:
 * <ol>
 *   <li><b>Range gate:</b> whether the listener is in vanilla hearing range, from the un-boosted volume
 *       ({@link SoundRange}). Out-of-range sounds are still given their volume, but they never use up a repeat limit,
 *       never show a cue and are not recorded for the trace: not heard, not logged.</li>
 *   <li><b>Decide:</b> where the player stands ({@link WhereYouAre}, worked out again only when the feet block, world,
 *       dimension or profile changed), Everywhere, zones and precedence ({@link Precedence}), then the winning entry's
 *       repeat limit ({@link RepeatLimiter}). Every sound is in scope: music, UI and Voice/Speech too (section 5).</li>
 *   <li><b>Drop</b> (over the limit, FR-6.8: status Limited, no cue), <b>mute</b> (status Muted, cue if due, FR-7.1)
 *       or <b>play</b> at 0 to 200% (status Boosted above 100%, FR-5.4). A muted sound does not start, except a
 *       looping one, which starts silently so it can come back (FR-8.8).</li>
 *   <li><b>Record</b> the play for the sound trace ({@link LastPlays}, FR-14.6).</li>
 * </ol>
 * Bypass and the time before a profile is loaded leave the sound as vanilla plays it.
 *
 * <p>Each entry has its own repeat-limit counter in each place (FR-6.7, owner decision 2026-11-11): the key is the
 * place, the set and the entry ID ({@link #limiterKey}), because entry IDs are unique only within one set and one set
 * can be attached to many places. A set and its duplicate share entry instances, so the set ID comes from the
 * resolver's layers, never from the entry. Counters survive saves that keep the entry in that set and place.
 *
 * <p>Allocates nothing once each sound ID and entry has been seen. Not thread-safe: call everything on the thread that
 * starts sounds.
 */
public final class SoundStartDecider {
	/** The place part of an Everywhere entry's limiter key. */
	private static final String EVERYWHERE_KEY = "everywhere";
	/** Separates the place, set and entry IDs in a limiter key. IDs never contain a line break. */
	private static final char KEY_SEPARATOR = '\n';

	private final LastPlays lastPlays;
	private final RepeatLimiter limiter = new RepeatLimiter();
	private final SoundDecision decision = new SoundDecision();
	private final WhereYouAre where = new WhereYouAre();
	private Precedence precedence;
	/** Everywhere's limiter keys: by set ID, then by entry (an entry is unique within its set). */
	private Map<String, Map<SetEntry, String>> everywhereKeys = Map.of();
	private Map<Zone, Map<String, Map<SetEntry, String>>> zoneKeys = Map.of();
	/** The cue settings where the player stands ({@link #cues}), and the snapshot and list they were worked out for. */
	private CueResolution cues = CueResolution.DEFAULT;
	private Precedence cuesFor;
	private long cuesVersion;
	private boolean boostSupported = true;

	/** @param lastPlays where each sound's last heard play is recorded for its trace */
	public SoundStartDecider(LastPlays lastPlays) {
		this.lastPlays = lastPlays;
	}

	/**
	 * Uses a new profile snapshot from now on, e.g. after the player saves. Repeat-limit counters of entries that are
	 * still in the same set and place keep counting; the rest are forgotten.
	 */
	public void setProfile(Precedence precedence) {
		Set<String> keys = new HashSet<>();
		Map<String, Map<SetEntry, String>> everywhere = keysOf(null, precedence.everywhere(), keys);
		Map<Zone, Map<String, Map<SetEntry, String>>> zones = new IdentityHashMap<>();
		List<Zone> list = precedence.zones();
		for (int i = 0; i < list.size(); i++) {
			Zone zone = list.get(i);
			zones.put(zone, keysOf(zone.id(), precedence.zoneResolver(i), keys));
		}
		limiter.retain(keys);
		this.everywhereKeys = everywhere;
		this.zoneKeys = zones;
		this.precedence = precedence;
		where.updateNowhere(precedence);
	}

	/**
	 * The player's feet are in block {@code (bx, by, bz)} (FR-8.4). Called by every sound start and by the client
	 * tick; cheap when nothing changed. Needs a profile ({@link #setProfile}).
	 *
	 * @return whether where you are changed
	 */
	public boolean locate(String worldKey, String dimension, int bx, int by, int bz) {
		return where.update(precedence, worldKey, dimension, bx, by, bz);
	}

	/** No player position is known: only Everywhere applies. */
	public boolean locateNowhere() {
		return where.updateNowhere(precedence);
	}

	/** Where the player is, for the current profile; shared with the re-decision of playing loops (FR-8.8). */
	public WhereYouAre where() {
		return where;
	}

	/** The current profile snapshot, or {@code null} before one is set. */
	public Precedence precedence() {
		return precedence;
	}

	/** One place's limiter keys, by set ID and then by entry; every key is also added to {@code all}. */
	private static Map<String, Map<SetEntry, String>> keysOf(String zoneId, EntryResolver place, Set<String> all) {
		Map<String, Map<SetEntry, String>> bySet = new HashMap<>();
		for (Layer layer : place.layers()) {
			Map<SetEntry, String> entries = bySet.computeIfAbsent(layer.setId(), id -> new IdentityHashMap<>());
			for (SetEntry entry : layer.entries()) {
				if (entry.enabled()) {
					String key = limiterKey(zoneId, layer.setId(), entry.id());
					entries.put(entry, key);
					all.add(key);
				}
			}
		}
		return bySet;
	}

	/**
	 * The cue settings where the player stands now (FR-7, decided 2026-11-21): each from the smallest zone there that sets
	 * it, or Everywhere ({@link CueResolver}). Worked out again only when the profile snapshot or the list of zones the
	 * player stands in changed, so a sound start or a frame just reads it; allocates only then.
	 */
	public CueResolution cues() {
		Precedence p = precedence;
		if (p == null) {
			return CueResolution.DEFAULT;
		}
		if (p != cuesFor || where.version() != cuesVersion || where.precedence() != p) {
			if (where.precedence() != p) {
				where.updateNowhere(p);
			}
			cues = CueResolver.resolve(p, where);
			cuesFor = p;
			cuesVersion = where.version();
		}
		return cues;
	}

	/** False when boost is off or the audio device ignores a raised gain cap: volumes then stop at 100% (section 7.2). */
	public void setBoostSupported(boolean supported) {
		this.boostSupported = supported;
	}

	/**
	 * The repeat-limit counter key of an entry in one place: the place, the set and the entry ID. Each place has its
	 * own counters for a shared set (owner decision 2026-11-11). For {@link RepeatLimiter#droppedCount} in Diagnostics.
	 *
	 * @param zoneIdOrNull the zone's ID, or {@code null} for Everywhere
	 */
	public static String limiterKey(String zoneIdOrNull, String setId, String entryId) {
		return (zoneIdOrNull == null ? EVERYWHERE_KEY : zoneIdOrNull) + KEY_SEPARATOR + setId + KEY_SEPARATOR + entryId;
	}

	/** The repeat-limit counters, for Diagnostics. */
	public RepeatLimiter limiter() {
		return limiter;
	}

	/**
	 * Estimated bytes of the repeat-limit counters and the current profile's per-ID caches (Everywhere and every zone),
	 * for the Diagnostics memory readout (PRD 9.1): {@code meter.add("Rules and limits", decider::estimatedBytes)}.
	 * Walks them, so let {@code MemoryMeter} call it, not every sound.
	 */
	public long estimatedBytes() {
		Precedence p = precedence;
		return limiter.estimatedBytes() + (p == null ? 0 : p.estimatedBytes());
	}

	/** Decides {@code in} and writes what to do into {@code out}. */
	public void start(SoundStart in, SoundStartResult out) {
		out.reset();
		boolean audible = !in.positional || SoundRange.isAudible(in.range, in.distanceSq);
		out.audible = audible;
		Precedence p = precedence;
		if (p == null) {
			return;
		}
		if (in.bypass) {
			if (audible) {
				record(in, 100, false, false);
			}
			return;
		}

		if (in.feetKnown) {
			where.update(p, in.worldKey, in.dimension, in.feetX, in.feetY, in.feetZ);
		} else {
			where.updateNowhere(p);
		}
		SoundDecision d = decision;
		p.decide(in.soundId, where, d);
		float volume = d.volumePercent();
		out.handled = true;
		out.decision = d;
		out.volumePercent = volume;

		if (audible && d.limit() != null && !limiter.tryStart(keyOf(d.limitZone(), d.limitSet(), d.limitRule()), d.limit(),
				in.x, in.y, in.z, in.nowMs)) {
			out.status = PlayStatus.LIMITED;
			out.factor = 0;
			out.cancel = true;
			out.zone = d.limitZone();
			record(in, volume, true, false);
			return;
		}

		out.zone = d.loudnessZone();
		if (volume <= 0) {
			out.status = PlayStatus.MUTED;
			out.factor = 0;
			out.cancel = !in.looping;
			// FR-7.2, FR-7.7: "Show muted-sound cues" and "Cue sounds without subtitles" as they are where the player stands
			// as the sound starts (FR-8.8 timing).
			CueSettings settings = cues().settings();
			out.cue = CueRules.shouldCue(in.hasSubtitle, volume, d.loudnessRule() != null, d.cueOn(), audible, settings);
			out.cueUsesSoundId = out.cue && !in.hasSubtitle;
		} else {
			float capped = Math.min(volume, boostSupported ? 200F : 100F);
			out.factor = capped / 100F;
			out.status = capped > 100 ? PlayStatus.BOOSTED : PlayStatus.PLAYING;
		}
		if (audible) {
			record(in, volume, false, out.cue);
		}
	}

	private String keyOf(Zone zone, String setId, SetEntry entry) {
		Map<String, Map<SetEntry, String>> place = zone == null ? everywhereKeys : zoneKeys.get(zone);
		Map<SetEntry, String> keys = place == null ? null : place.get(setId);
		String key = keys == null ? null : keys.get(entry);
		// Not in the snapshot's maps can only mean an entry from another snapshot: key it the same way (allocates).
		return key != null ? key : limiterKey(zone == null ? null : zone.id(), setId, entry.id());
	}

	private void record(SoundStart in, float volume, boolean dropped, boolean cueShown) {
		lastPlays.record(in.soundId, in.feetKnown, in.worldKey, in.dimension, in.feetX, in.feetY, in.feetZ, volume, dropped,
				cueShown);
	}
}
