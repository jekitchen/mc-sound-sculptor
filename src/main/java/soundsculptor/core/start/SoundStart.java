package soundsculptor.core.start;

/**
 * What the sound hook knows about one sound start, as input to {@link SoundStartDecider#start}. Mutable, so the hook
 * fills one instance per sound and allocates nothing (PRD 7.4). Call {@link #reset} before filling it.
 */
public final class SoundStart {
	/** The sound's ID, e.g. {@code minecraft:entity.villager.ambient}; reuse one string per ID. */
	public String soundId;
	/** Whether the sound has a world position (not relative to the listener); only then does the range gate apply. */
	public boolean positional;
	/**
	 * Whether vanilla loops the sound for as long as its source exists (minecart, bee, biome ambience): a muted loop
	 * starts silently so it can come back when the player moves or edits a set (FR-8.8).
	 */
	public boolean looping;
	/** Whether the player's feet block is known; if not, only Everywhere applies (FR-8.4). */
	public boolean feetKnown;
	/** The block the local player's feet are in, which decides the zones (FR-8.4). */
	public int feetX;
	public int feetY;
	public int feetZ;
	/** Vanilla hearing range from the sound's own, un-boosted volume ({@code SoundRange.vanillaRange}). */
	public float range;
	/** Squared distance from the listener to the sound; ignored for non-positional sounds. */
	public double distanceSq;
	/** The current server or world (FR-11.1). */
	public String worldKey;
	/** The current dimension, e.g. {@code minecraft:overworld}. */
	public String dimension;
	/** The sound's position: only per-source repeat limits use it (FR-6.3). */
	public double x;
	public double y;
	public double z;
	/** Whether the sound has a vanilla subtitle, the text of its cue (FR-7.2). */
	public boolean hasSubtitle;
	/** Whether Bypass Sound Sculptor is on (FR-13.1). */
	public boolean bypass;
	/** Real time in milliseconds, from a clock that never goes backwards; repeat limits count with it (FR-6.2). */
	public long nowMs;

	public SoundStart() {
		reset();
	}

	/** Back to a non-positional sound with no ID and no world. */
	public void reset() {
		soundId = null;
		positional = false;
		looping = false;
		feetKnown = false;
		feetX = 0;
		feetY = 0;
		feetZ = 0;
		range = Float.POSITIVE_INFINITY;
		distanceSq = 0;
		worldKey = "";
		dimension = "";
		x = 0;
		y = 0;
		z = 0;
		hasSubtitle = false;
		bypass = false;
		nowMs = 0;
	}
}
