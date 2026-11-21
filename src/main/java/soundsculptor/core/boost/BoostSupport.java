package soundsculptor.core.boost;

import java.util.Map;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetLibrary;

/**
 * Whether a level above 100% may play louder than vanilla (PRD FR-5.4, section 7.2). Two things must hold:
 * <ul>
 *   <li>the audio device honours a raised per-source gain cap ({@code AL_MAX_GAIN} 2.0). The sound hook checks this on
 *   the sound thread each time vanilla creates a channel and reports it here; until the first channel nothing is known,
 *   and boost is allowed (a device that ignores the cap then plays at 100% anyway);</li>
 *   <li>the player confirmed the boost dialog in this profile ({@link Profile#boostConfirmed()}).</li>
 * </ul>
 * Otherwise levels above 100% play at 100%. Thread-safe: the device state is written on the sound thread and read on
 * the game thread.
 */
public final class BoostSupport {
	/** What the audio device said when Sound Sculptor raised a channel's gain cap. */
	public enum Device {
		/** No channel was created yet. */
		UNKNOWN,
		/** The device kept a gain cap of 2.0. */
		SUPPORTED,
		/** The device refused or changed the raised cap; boost is off. */
		UNSUPPORTED
	}

	private static volatile Device device = Device.UNKNOWN;

	private BoostSupport() {
	}

	public static Device device() {
		return device;
	}

	/** Called by the sound hook after it raised a new channel's gain cap. Returns whether the state changed. */
	public static boolean report(boolean supported) {
		Device now = supported ? Device.SUPPORTED : Device.UNSUPPORTED;
		Device before = device;
		device = now;
		return before != now;
	}

	/** Back to "not known yet": the sound engine opened an audio device again (a reload or a new device); also tests. */
	public static void reset() {
		device = Device.UNKNOWN;
	}

	/** Whether the audio device can play above 100% (or nothing is known yet). */
	public static boolean deviceAllows() {
		return device != Device.UNSUPPORTED;
	}

	/** Whether levels above 100% play louder in this profile: the device allows it and the profile confirmed boost. */
	public static boolean allows(Profile profile) {
		return deviceAllows() && profile.boostConfirmed();
	}

	/**
	 * Whether {@code profile} has a level above 100% it never confirmed (a linked set made louder in another world, or
	 * edited outside a world): it plays at 100%, and the Sound Sculptor screen asks once when it opens there. Never on a
	 * device that cannot boost.
	 */
	public static boolean hasUnconfirmedBoost(Profile profile, SetLibrary library) {
		return deviceAllows() && !profile.boostConfirmed() && profile.hasBoost(library);
	}

	/**
	 * Whether the boost dialog must be shown before a change from {@code before} to {@code after} (FR-5.4): the profile
	 * has not confirmed boost, the device can boost, and the change adds boost ({@link #addsBoost}). A change that keeps,
	 * lowers or removes a boost, or touches something else (renaming a set, attaching a quiet one), never asks, even in a
	 * profile that already has an unconfirmed boost. Each profile keeps its own confirmation, so a library set with a
	 * boosted entry asks again in each profile it is attached in.
	 */
	public static boolean needsConfirm(Profile before, SetLibrary libraryBefore, Profile after, SetLibrary libraryAfter) {
		return deviceAllows() && !after.boostConfirmed() && addsBoost(before.boostedUses(libraryBefore), after.boostedUses(libraryAfter));
	}

	/**
	 * Whether a change adds boost: some use of a level above 100% ({@link Profile#boostedUses}) is new, or plays louder
	 * than before.
	 */
	public static boolean addsBoost(Map<String, Integer> before, Map<String, Integer> after) {
		for (Map.Entry<String, Integer> use : after.entrySet()) {
			if (raises(before.get(use.getKey()), use.getValue())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether setting one entry's level from {@code before} to {@code after} adds boost: the new level is above 100%, and
	 * the old one was not set, 100% or less, or lower. For the Level pop-ups and the entry editor; the profile's state
	 * (confirmed, device) is checked by the caller.
	 */
	public static boolean raises(Integer before, Integer after) {
		return after != null && after > 100 && (before == null || before <= 100 || after > before);
	}
}
