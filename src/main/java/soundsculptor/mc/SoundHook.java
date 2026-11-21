package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.boost.BoostSupport;
import soundsculptor.core.boost.Gain;
import soundsculptor.core.diag.ErrorBudget;
import soundsculptor.core.diag.TimingWindow;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.start.SoundStart;
import soundsculptor.core.start.SoundStartDecider;
import soundsculptor.core.start.SoundStartResult;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.WhereYouAre;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.SoundRange;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Decides what happens to every sound start (PRD section 7 flowchart) and reports it to observers.
 *
 * <ol>
 *   <li>Range: computes the vanilla hearing range from the sound's own volume (M1).</li>
 *   <li>Decide: with Bypass on, everything plays as vanilla; otherwise the Sound Sets of Everywhere and of the zones
 *   the player's feet are in decide it (FR-8.4, FR-9). Every sound is in scope, music, UI and Voice/Speech included
 *   (section 5); vanilla's sliders apply first. Outside a world nothing is decided.</li>
 *   <li>Cue: a muted sound in range whose deciding entry has Cue on is shown in the cue box (FR-7.1).</li>
 *   <li>Report: observers (the recently played log, the dev log) see the sound with its status. The sound's last play
 *   is kept for its trace (FR-14.6), and the time all this took feeds Diagnostics (FR-13.3).</li>
 * </ol>
 *
 * <p>Volume (FR-5.3): final loudness is vanilla loudness (including the Master and category sliders) times the rule
 * volume. The factor is applied where vanilla calculates a sound's volume: once at the start, and again whenever
 * vanilla recalculates it (moving sounds every tick, and after a slider change). A volume of 0% is then skipped by
 * vanilla like a zero slider.
 *
 * <p>Fail open (FR-13.2): if anything throws, the sound plays as vanilla, the error is logged once, and more than 10
 * errors in a minute turn on Bypass and show a notice asking the player to report the bug.
 */
public final class SoundHook {
	private static final List<SoundObserver> OBSERVERS = new CopyOnWriteArrayList<>();
	private static final SoundObservation OBSERVATION = new SoundObservation();
	private static final ErrorBudget ERRORS = new ErrorBudget();
	/** Sound ID strings, so a known sound builds no new string on the hot path (PRD 7.4). */
	private static final Map<Identifier, String> ID_STRINGS = new HashMap<>();
	/** The volume factor decided by the last {@link #onSoundStart}, used once by {@link #applyStartVolume}. */
	private static float startFactor = 1.0F;
	/** The sound {@link #onSoundStart} just decided, until {@link #applyStartVolume} tracks it. */
	private static SoundInstance pendingInstance;
	private static String pendingId;
	private static boolean pendingTracked;
	/**
	 * The factor the sound is tracked with: the decided one, 0 for a muted loop. Only the start uses {@link #SILENT},
	 * because vanilla skips a sound that starts at exactly 0; tracking 0 keeps it silent when vanilla recalculates it,
	 * so its subtitle is not refreshed either.
	 */
	private static float pendingFactor = 1.0F;
	/** Whether vanilla may notify its subtitle listeners for the sound {@link #onSoundStart} just decided. */
	private static boolean notifySubtitles = true;
	/** Start volume factor of a muted looping sound, which may come back while it plays: inaudible, but not zero. */
	static final float SILENT = 1.0E-6F;
	private static final SoundStart START = new SoundStart();
	private static final SoundStartResult RESULT = new SoundStartResult();
	private static Precedence deciderProfile;
	/** The world or server the repeat-limit counters count for. */
	private static String deciderKey = "";
	private static float lastAppliedFactor = 1.0F;
	private static float lastStartFactor = 1.0F;
	private static final TimingWindow SOUND_TIMING = new TimingWindow();
	private static final LastPlays LAST_PLAYS = new LastPlays();
	private static final SoundStartDecider DECIDER = new SoundStartDecider(LAST_PLAYS);

	private SoundHook() {
	}

	public static void addObserver(SoundObserver observer) {
		OBSERVERS.add(observer);
	}

	public static void removeObserver(SoundObserver observer) {
		OBSERVERS.remove(observer);
	}

	/**
	 * Called from {@code SoundEngine.play} after the sound is resolved, before vanilla notifies the subtitle listeners
	 * and calculates its volume.
	 * Runs on the thread that started the sound (normally the render thread).
	 *
	 * @return true if the sound must not start (muted)
	 */
	public static boolean onSoundStart(SoundInstance instance) {
		long started = System.nanoTime();
		startFactor = 1.0F;
		pendingInstance = null;
		notifySubtitles = true;
		try {
			return handle(instance);
		} catch (RuntimeException | LinkageError e) {
			startFactor = 1.0F;
			pendingInstance = null;
			notifySubtitles = true;
			failOpen(e);
			return false;
		} finally {
			SOUND_TIMING.add(System.nanoTime() - started, Util.getMillis());
		}
	}

	private static boolean handle(SoundInstance instance) {
		Minecraft minecraft = Minecraft.getInstance();
		Sound sound = instance.getSound();
		boolean relative = instance.isRelative();
		boolean noAttenuation = instance.getAttenuation() == SoundInstance.Attenuation.NONE;
		float volume = instance.getVolume();
		float range = SoundRange.vanillaRange(volume, sound.getAttenuationDistance(), relative, noAttenuation);

		double distanceSq = 0.0;
		if (!relative) {
			Vec3 listener = minecraft.getSoundManager().getListenerTransform().position();
			double dx = instance.getX() - listener.x();
			double dy = instance.getY() - listener.y();
			double dz = instance.getZ() - listener.z();
			distanceSq = dx * dx + dy * dy + dz * dz;
		}

		Identifier id = instance.getIdentifier();
		String idString = idString(id);

		// Decide: where you are, the sets, zones and precedence (core SoundStartDecider). Outside a world nothing is decided.
		SoundStartResult result = null;
		Component subtitle = null;
		boolean loaded = sync(minecraft);
		if (loaded) {
			SoundStart in = START;
			in.reset();
			in.soundId = idString;
			in.positional = !relative;
			in.looping = instance.isLooping();
			WhereYouAre where = DECIDER.where();
			in.feetKnown = where.known();
			in.feetX = where.x();
			in.feetY = where.y();
			in.feetZ = where.z();
			in.range = range;
			in.distanceSq = distanceSq;
			in.worldKey = ProfileManager.key();
			in.dimension = WorldSession.dimension(minecraft);
			in.x = instance.getX();
			in.y = instance.getY();
			in.z = instance.getZ();
			WeighedSoundEvents events = minecraft.getSoundManager().getSoundEvent(id);
			subtitle = events == null ? null : events.getSubtitle();
			in.hasSubtitle = subtitle != null;
			in.bypass = ProfileManager.isBypassed();
			in.nowMs = Util.getMillis();
			result = RESULT;
			DECIDER.start(in, result);
			if (result.cue()) {
				// FR-7: a muted sound in range whose entry has Cue on; the cue box shows its subtitle, or its ID (FR-7.2).
				CueOverlay.add(result.cueUsesSoundId() ? null : subtitle, idString, !relative, in.looping, instance.getX(),
						instance.getY(), instance.getZ(), range, in.nowMs);
			}
		}

		PlayStatus status = result == null ? PlayStatus.PLAYING : result.status();
		boolean handled = result != null && result.handled();
		// A sound Sound Sculptor silences (muted, 0% or dropped) gets no vanilla subtitle; its cue replaces it (FR-7).
		notifySubtitles = !(handled && result.factor() <= 0);
		if (loaded && !(handled && result.cancel())) {
			// Every sound that starts in a world is tracked, so a loop can follow the player and Bypass can reset it
			// (FR-8.8, FR-13.1). A muted loop starts silently: vanilla skips a sound whose volume is exactly 0.
			pendingFactor = handled ? result.factor() : 1.0F;
			startFactor = pendingFactor > 0 ? pendingFactor : SILENT;
			pendingInstance = instance;
			pendingId = idString;
			pendingTracked = true;
		}

		SoundObservation o = OBSERVATION;
		o.id = id;
		o.idString = idString;
		o.category = instance.getSource();
		o.x = instance.getX();
		o.y = instance.getY();
		o.z = instance.getZ();
		o.volume = volume;
		o.range = range;
		o.relative = relative;
		o.distance = Math.sqrt(distanceSq);
		o.audible = SoundRange.isAudible(range, distanceSq);
		o.status = status;
		o.zoneName = handled ? result.zoneName() : null;
		for (SoundObserver observer : OBSERVERS) {
			observer.onSound(o);
		}
		return handled && result.cancel();
	}

	/**
	 * Makes the decider use the loaded profile and where the player's feet are (FR-8.4): the player's own block
	 * position, not the camera's. Cheap when nothing changed; called by every sound start and every client tick.
	 *
	 * @return whether a profile is loaded (in a world); if not, nothing is decided
	 */
	static boolean sync(Minecraft minecraft) {
		if (!ProfileManager.isLoaded()) {
			return false;
		}
		String key = ProfileManager.key();
		if (!java.util.Objects.equals(key, deciderKey)) {
			// Another world or server: its places count afresh (FR-6.4). An Everywhere entry of a library set has the same
			// counter key in every world, so without this a few plays could carry over from the world just left.
			DECIDER.limiter().clear();
			deciderKey = key;
		}
		// FR-5.4: above 100% plays louder only if the audio device allows it and this profile confirmed boost.
		DECIDER.setBoostSupported(BoostSupport.allows(ProfileManager.profile()));
		Precedence precedence = ProfileManager.precedence();
		if (precedence != deciderProfile) {
			DECIDER.setProfile(precedence);
			deciderProfile = precedence;
		}
		// The cue settings (FR-7) belong to the places: the decider works them out from where the player stands.
		LocalPlayer player = minecraft.player;
		if (player == null) {
			DECIDER.locateNowhere();
		} else {
			BlockPos feet = player.blockPosition();
			DECIDER.locate(ProfileManager.key(), WorldSession.dimension(minecraft), feet.getX(), feet.getY(), feet.getZ());
		}
		return true;
	}

	/** The profile snapshot the decider uses; valid after {@link #sync} returned true. */
	static Precedence precedence() {
		return deciderProfile;
	}

	/** Where the player stands now, for the trace of a sound not heard yet (FR-14.1). Render thread only. */
	public static soundsculptor.core.trace.Standing standingNow() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!sync(minecraft) || !DECIDER.where().known()) {
			return soundsculptor.core.trace.Standing.NOWHERE;
		}
		WhereYouAre where = DECIDER.where();
		return new soundsculptor.core.trace.Standing(true, ProfileManager.key(), WorldSession.dimension(minecraft), where.x(),
				where.y(), where.z());
	}

	/**
	 * The cue settings where the player stands now, and the place that decides each (FR-7): from the smallest zone there
	 * that sets it, or Everywhere. Everywhere's defaults outside a world. Worked out again only when the profile or the
	 * zones the player stands in change, so the cue box can read it every frame. Render thread only.
	 */
	public static soundsculptor.core.cue.CueResolution cuesNow() {
		if (!sync(Minecraft.getInstance())) {
			return soundsculptor.core.cue.CueResolution.DEFAULT;
		}
		return DECIDER.cues();
	}

	/** Where the player is, for {@link #precedence()} (FR-8.4). Render thread only. */
	public static WhereYouAre where() {
		return DECIDER.where();
	}

	private static String idString(Identifier id) {
		String idString = ID_STRINGS.get(id);
		if (idString == null) {
			idString = id.toString();
			ID_STRINGS.put(id, idString);
		}
		return idString;
	}

	/**
	 * Called from {@code SoundEngine.play} just before it notifies the subtitle listeners, for the sound
	 * {@link #onSoundStart} just decided: false when Sound Sculptor silences it, so vanilla's subtitle box does not show
	 * a muted sound (decided 2026-11-21; the cue box shows it when its entry has Cue on). Vanilla's own zero sliders are
	 * not affected.
	 */
	public static boolean notifiesSubtitles() {
		return notifySubtitles;
	}

	/**
	 * Called from {@code SoundEngine.play} with vanilla's volume for the sound that {@link #onSoundStart} just
	 * handled. Returns the volume with the rule's factor applied.
	 */
	public static float applyStartVolume(float vanillaVolume) {
		float factor = startFactor;
		SoundInstance instance = pendingInstance;
		startFactor = 1.0F;
		pendingInstance = null;
		lastAppliedFactor = factor;
		lastStartFactor = factor;
		// FR-5.6: a boosted gain never goes above 2.0. Only the channel's gain changes: the hearing range was already
		// worked out from the instance's own volume (Sound.getAttenuationDistance, before this), FR-5.5.
		float gain = Gain.apply(vanillaVolume, factor);
		if (instance != null && pendingTracked) {
			try {
				PlayingChannels.get().start(instance, pendingId, vanillaVolume, pendingFactor);
			} catch (RuntimeException | LinkageError e) {
				failOpen(e);
			}
		}
		return gain;
	}

	/**
	 * Called whenever vanilla recalculates a playing sound's volume (every tick for moving sounds, and after a
	 * slider change). A tracked sound keeps its own factor, so a muted sound stays silent and a one-shot keeps what
	 * it was decided with at its start (FR-8.8). An untracked sound (it started before the profile loaded, or the
	 * table was full) plays as vanilla: it was never decided, and re-deciding it as the player walks would break FR-8.8.
	 */
	public static float applyVolume(SoundInstance instance, float vanillaVolume) {
		try {
			float tracked = PlayingChannels.get().recalculated(instance, vanillaVolume);
			if (!Float.isNaN(tracked)) {
				lastAppliedFactor = vanillaVolume == 0 ? 1.0F : tracked / vanillaVolume;
				return tracked;
			}
			lastAppliedFactor = 1.0F;
			// Not decided: vanilla's gain, at most 1.0 as with OpenAL's default cap (every source's cap is raised to 2.0).
			return Gain.apply(vanillaVolume, 1.0F);
		} catch (RuntimeException | LinkageError e) {
			failOpen(e);
			return Gain.apply(vanillaVolume, 1.0F);
		}
	}

	/** The repeat-limit counters, for the Diagnostics memory readout. */
	public static soundsculptor.core.limit.RepeatLimiter limiter() {
		return DECIDER.limiter();
	}

	/** Time spent per sound start, for Diagnostics (FR-13.3). Render thread only. */
	public static TimingWindow soundTiming() {
		return SOUND_TIMING;
	}

	/** Each sound's last play, for the "Why?" panel (FR-14.6). Render thread only. */
	public static LastPlays lastPlays() {
		return LAST_PLAYS;
	}

	/**
	 * The factor applied to the most recent sound start (1 = unchanged), for tests. Unlike
	 * {@link #lastAppliedFactor()}, per-tick recalculations of other playing sounds do not change it.
	 */
	public static float lastStartFactor() {
		return lastStartFactor;
	}

	/** The factor most recently applied to a volume (1 = unchanged), for tests. */
	public static float lastAppliedFactor() {
		return lastAppliedFactor;
	}

	private static void failOpen(Throwable error) {
		StackTraceElement[] trace = error.getStackTrace();
		String key = error.getClass().getName() + " at " + (trace.length > 0 ? trace[0] : "?");
		if (ERRORS.firstTime(key)) {
			SoundSculptorClient.LOGGER.error("Sound Sculptor could not handle a sound; it plays as in vanilla", error);
		}
		if (ERRORS.recordError(Util.getMillis())) {
			ProfileManager.setBypass(true);
			SoundSculptorClient.LOGGER.error("Sound Sculptor hit more than {} errors in a minute and turned on Bypass", ErrorBudget.MAX_ERRORS_PER_MINUTE);
			Notices.show(Component.translatable("soundsculptor.notice.errors.title"),
					Component.translatable("soundsculptor.notice.errors.message"));
		}
	}

	/** Called when the player turns Bypass off after it was on. */
	static void resetErrorBudget() {
		ERRORS.reset();
	}

	/** Errors handled this session (for diagnostics and tests). */
	public static long errorCount() {
		return ERRORS.totalErrors();
	}
}
