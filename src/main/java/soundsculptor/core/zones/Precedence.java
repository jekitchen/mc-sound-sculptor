package soundsculptor.core.zones;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import soundsculptor.core.diag.MemoryEstimate;
import soundsculptor.core.sets.CacheBudget;
import soundsculptor.core.sets.EntryResolution;
import soundsculptor.core.sets.EntryResolver;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetLibrary;

/**
 * Combines Everywhere and the zones into one decision per sound (PRD FR-9), separately for loudness, limit and cue:
 * <ol>
 *   <li>Where you are: the zones containing the player's feet block, smallest first ({@link WhereYouAre}), then
 *       Everywhere. The sound's own position plays no part (FR-8.7).</li>
 *   <li>For each property, the first place in that order whose attached sets set it for the sound wins (FR-4.5 within
 *       a place).</li>
 *   <li>A property nothing sets keeps vanilla behavior (100%, no limit, cue off).</li>
 * </ol>
 *
 * <p>An immutable snapshot of one profile and the set library: build a new one when a set, an attachment or a place
 * changes (PRD 7.4); the sets are flattened into one {@link EntryResolver} per place here, never per sound. {@link #decide} allocates nothing
 * once each sound ID has been seen.
 */
public final class Precedence {
	/** Most zones a profile can hold (FR-8.9). */
	public static final int MAX_ZONES = 64;
	/**
	 * Sound IDs all zones' rule lists cache together (PRD 9.1 memory budget): about 1 MB when full. Without it, 64 zones
	 * could each cache 4,096 IDs, about 32 MB. Past the cap a zone answers the same, just by matching patterns again.
	 */
	public static final int ZONE_CACHE_IDS = 8192;
	/**
	 * Sound IDs whose resolutions in every list are kept together in one row (see {@code rows}): at most about 0.3 MB
	 * with 64 zones. A real session hears a few hundred IDs; past the cap a sound is resolved list by list, as before.
	 */
	public static final int ROW_CACHE_IDS = 1024;

	private final EntryResolver everywhere;
	private final Zone[] zones;
	private final EntryResolver[] zoneResolvers;
	private final double[] volumes;

	/**
	 * Per sound ID, what every place resolves it to (Everywhere first, then each zone): one lookup per sound instead of
	 * one per place. Measured in game, the per-list lookups were most of a sound start's cost with 64 zones (M8). Capped
	 * like the resolvers' own caches, because servers can send arbitrary IDs.
	 */
	private final Map<String, EntryResolution[]> rows = new ConcurrentHashMap<>();
	/** Sounds decided from {@link #rows}; each answers every list from the cache. Plain field: one thread. */
	private long rowHits;

	/**
	 * @param everywhere the sets attached to Everywhere
	 * @param library    the library the places' set IDs refer to; attachments to sets it does not have are skipped
	 */
	public Precedence(PlaceSets everywhere, List<Zone> zones, SetLibrary library) {
		if (zones.size() > MAX_ZONES) {
			throw new IllegalArgumentException(zones.size() + " zones is more than the maximum " + MAX_ZONES);
		}
		this.everywhere = EntryResolver.forPlace(everywhere, library, null);
		this.zones = zones.toArray(new Zone[0]);
		this.zoneResolvers = new EntryResolver[this.zones.length];
		this.volumes = new double[this.zones.length];
		CacheBudget zoneCaches = new CacheBudget(ZONE_CACHE_IDS);
		for (int i = 0; i < this.zones.length; i++) {
			zoneResolvers[i] = EntryResolver.forPlace(this.zones[i].sets(), library, zoneCaches);
			volumes[i] = this.zones[i].shape().volume();
		}
	}

	/**
	 * Decides one sound for where the player is and writes the result into {@code out}.
	 *
	 * @param where worked out for this snapshot ({@link WhereYouAre#update} with this {@code Precedence})
	 * @throws IllegalArgumentException if {@code where} belongs to another snapshot
	 */
	public void decide(String soundId, WhereYouAre where, SoundDecision out) {
		if (where.precedence() != this) {
			throw new IllegalArgumentException("where you are was worked out for another profile snapshot");
		}
		out.reset();
		EntryResolution[] row = row(soundId);
		EntryResolution g = row[0];
		EntryResolution loud = null;
		EntryResolution limit = null;
		EntryResolution cue = null;
		int loudIdx = -1;
		int limitIdx = -1;
		int cueIdx = -1;
		int n = where.count();
		for (int k = 0; k < n && (loud == null || limit == null || cue == null); k++) {
			int i = where.zone(k);
			EntryResolution r = row[i + 1];
			if (r == EntryResolution.NONE) {
				continue;
			}
			if (loud == null && r.loudness() != null) {
				loud = r;
				loudIdx = i;
			}
			if (limit == null && r.limit() != null) {
				limit = r;
				limitIdx = i;
			}
			if (cue == null && r.cue() != null) {
				cue = r;
				cueIdx = i;
			}
		}

		if (loud != null) {
			out.volumePercent = loud.effectiveVolumePercent();
			out.loudnessRule = loud.loudness();
			out.loudnessZone = zones[loudIdx];
		} else {
			out.volumePercent = g.effectiveVolumePercent();
			out.loudnessRule = g.loudness();
		}
		if (limit != null) {
			out.limitRule = limit.limit();
			out.limitSet = limit.limitSet();
			out.limitZone = zones[limitIdx];
		} else {
			out.limitRule = g.limit();
			out.limitSet = g.limitSet();
		}
		out.limit = out.limitRule == null ? null : out.limitRule.limit();
		if (cue != null) {
			out.cueRule = cue.cue();
			out.cueZone = zones[cueIdx];
			out.cueOn = cue.cueOn();
		} else {
			out.cueRule = g.cue();
			out.cueOn = g.cueOn();
		}
	}

	/** The cached resolutions of every list for this sound ID; computed on first sight. */
	private EntryResolution[] row(String soundId) {
		EntryResolution[] row = rows.get(soundId);
		if (row != null) {
			rowHits++;
		} else {
			row = new EntryResolution[zones.length + 1];
			resolve(soundId, row);
			if (rows.size() < ROW_CACHE_IDS) {
				rows.put(soundId, row);
			}
		}
		return row;
	}

	/**
	 * Fills {@code out} with what each place resolves {@code soundId} to: index 0 is Everywhere, index {@code i + 1} is
	 * zone {@code i}. {@link #decide} does this once per sound ID and keeps the row; tests and the memory budget call it
	 * directly.
	 *
	 * @param out at least {@link #zoneCount()} + 1 long
	 */
	public void resolve(String soundId, EntryResolution[] out) {
		out[0] = everywhere.resolve(soundId);
		for (int i = 0; i < zones.length; i++) {
			out[i + 1] = zoneResolvers[i].resolve(soundId);
		}
	}

	/** Cache hits across Everywhere and every zone (FR-13.3). */
	public long cacheHits() {
		// A row hit answers Everywhere and every zone at once.
		long total = everywhere.cacheHits() + rowHits * (zones.length + 1);
		for (EntryResolver r : zoneResolvers) {
			total += r.cacheHits();
		}
		return total;
	}

	/** Cache misses across Everywhere and every zone. */
	public long cacheMisses() {
		long total = everywhere.cacheMisses();
		for (EntryResolver r : zoneResolvers) {
			total += r.cacheMisses();
		}
		return total;
	}

	/**
	 * Estimated bytes of Everywhere's and every zone's per-ID caches, the per-ID rows and the zone volumes, for the
	 * Diagnostics memory readout (PRD 9.1).
	 * The sets and zones belong to the profile and the library and are not counted. Walks the caches: call it through
	 * {@code MemoryMeter}, not per sound.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(5 * MemoryEstimate.REFERENCE)
				+ MemoryEstimate.array(MemoryEstimate.REFERENCE, zoneResolvers.length) + MemoryEstimate.array(8, volumes.length)
				+ MemoryEstimate.CONCURRENT_HASH_MAP + MemoryEstimate.hashTable(rows.size())
				+ rows.size() * (MemoryEstimate.HASH_NODE + MemoryEstimate.array(MemoryEstimate.REFERENCE, zones.length + 1))
				+ everywhere.estimatedBytes();
		for (EntryResolver r : zoneResolvers) {
			bytes += r.estimatedBytes();
		}
		return bytes;
	}

	/**
	 * Sound Sets applying in some place ("Sound Sets active", FR-13.3): enabled attachments to sets that exist, Quick
	 * changes included, counted once per place they are attached to.
	 */
	public int activeSetCount() {
		int count = everywhere.layers().size();
		for (EntryResolver r : zoneResolvers) {
			count += r.layers().size();
		}
		return count;
	}

	/** Enabled entries in those sets ("entries active", FR-13.3), counted once per place. */
	public int activeEntryCount() {
		int count = everywhere.ruleCount();
		for (EntryResolver r : zoneResolvers) {
			count += r.ruleCount();
		}
		return count;
	}

	/** Zones in the profile, enabled or not. */
	public int zoneCount() {
		return zones.length;
	}

	/** Everywhere's resolver, its attached sets flattened in order, for the sound trace (FR-14). */
	public EntryResolver everywhere() {
		return everywhere;
	}

	/** Zones in list order, for the sound trace. */
	public List<Zone> zones() {
		return List.of(zones);
	}

	/** Resolver for zone {@code i}'s attached sets. */
	public EntryResolver zoneResolver(int i) {
		return zoneResolvers[i];
	}

	/** Zone {@code i} in list order. */
	public Zone zoneAt(int i) {
		return zones[i];
	}

	/** Shape volume of zone {@code i}, which puts the zones you stand in smallest first (FR-9 step 1). */
	public double zoneVolume(int i) {
		return volumes[i];
	}
}
