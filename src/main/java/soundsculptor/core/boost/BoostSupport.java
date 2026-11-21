package soundsculptor.core.boost;

import soundsculptor.core.profile.Profile;

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

	/** For tests: back to "not known yet". */
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
	 * Whether the boost dialog must be shown before a change that leaves {@code after} using a level above 100% anywhere
	 * (setting one, attaching a set that has one, importing or copying one in, FR-5.4). Asked once per profile: never
	 * again once it confirmed. A library set with a boosted entry asks again in each profile it is attached in, because
	 * each profile keeps its own confirmation.
	 */
	public static boolean needsConfirm(Profile after, soundsculptor.core.sets.SetLibrary library) {
		return !after.boostConfirmed() && after.hasBoost(library);
	}
}
