package soundsculptor.core.playing;

import soundsculptor.core.boost.Gain;
import soundsculptor.core.diag.MemoryEstimate;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.WhereYouAre;

/**
 * The sounds playing right now, and the volume factor each one plays at (PRD FR-8.8, FR-13.1). Nothing here runs every
 * tick: a sound keeps the factor it was decided with at its start, except on three events.
 * <ul>
 *   <li><b>Where you are or the profile changed</b> (you walked into or out of a zone, or a set, entry or zone was
 *       edited): every <em>looping</em> sound is decided again; one-shots and music tracks play on as they started.</li>
 *   <li><b>Bypass turned on:</b> every sound goes back to vanilla volume (factor 1), silent loops included.</li>
 *   <li><b>Bypass turned off:</b> loops are decided again; everything else stays at vanilla until it ends.</li>
 * </ul>
 *
 * <p>How the sound hook uses it, all on the game thread:
 * <ol>
 *   <li>When a sound starts while a profile is loaded, {@link #start} it with its handle, whether it loops, vanilla's
 *       gain and the factor it was started with. A sound that does not start (dropped, or a muted one-shot) is never
 *       tracked.</li>
 *   <li>When vanilla recalculates a playing sound's volume (a slider change, a moving sound), use
 *       {@link #recalculated}: vanilla's new gain times the sound's own factor.</li>
 *   <li>Each client tick, after working out where the player is, call {@link #update}. It does nothing unless one of
 *       the events above happened, and calls the listener only for sounds whose gain changed. Never stop a sound
 *       because its gain became 0: a muted loop plays on silently.</li>
 *   <li>{@link #stop} a sound when its channel stops.</li>
 * </ol>
 *
 * <p>Only the volume is re-decided. Repeat limits are decided at start only, and cues are shown at start only.
 *
 * <p>Allocates nothing once created. Not thread-safe.
 */
public final class PlayingSounds {
	/**
	 * Most sounds tracked at once. Vanilla plays at most 247 normal and 8 streaming channels together, so 512 leaves
	 * room for audio mods that raise the channel count. When the table is full, further sounds simply play as they
	 * started and are not re-decided (fail open).
	 */
	public static final int CAPACITY = 512;

	/** Gain changes smaller than this are not reported: nobody can hear them, and each report is an OpenAL call. */
	static final float GAIN_EPSILON = 1e-4F;

	/** Receives a sound's new gain. */
	@FunctionalInterface
	public interface GainListener {
		void gainChanged(long handle, float gain);
	}

	private static final int SLOTS = CAPACITY * 2; // hash slots, a power of two at most half full
	private static final int EMPTY = -1;

	// Dense arrays of tracked sounds, indexes 0 to size - 1.
	private final long[] handles = new long[CAPACITY];
	private final String[] soundIds = new String[CAPACITY];
	private final boolean[] looping = new boolean[CAPACITY];
	private final float[] vanillaGains = new float[CAPACITY];
	private final float[] factors = new float[CAPACITY];
	private final float[] gains = new float[CAPACITY];
	private int size;

	// Open-addressing hash from handle to dense index.
	private final long[] slotHandles = new long[SLOTS];
	private final int[] slotIndexes = new int[SLOTS];

	private final SoundDecision scratch = new SoundDecision();
	private long rejected;
	private long decisions;

	// The inputs of the last update: re-decide only when one changed.
	private Precedence lastPrecedence;
	private long lastWhereVersion = -1;
	private boolean lastBypassed;

	public PlayingSounds() {
		java.util.Arrays.fill(slotIndexes, EMPTY);
	}

	/**
	 * Starts tracking a playing sound.
	 *
	 * @param handle      the sound hook's ID for the playing channel; unique among playing sounds
	 * @param looping     whether vanilla loops it ({@code SoundInstance.isLooping}); only loops are re-decided
	 * @param vanillaGain vanilla's gain for the channel, including the Master and category sliders
	 * @param factor      what Sound Sculptor multiplied that gain by at the start (1 when it changed nothing)
	 * @return false if the table is full or the handle is already tracked; the sound then just plays on unchanged
	 */
	public boolean start(long handle, String soundId, boolean looping, float vanillaGain, float factor) {
		if (size == CAPACITY || indexOf(handle) != EMPTY) {
			rejected++;
			return false;
		}
		int i = size++;
		handles[i] = handle;
		soundIds[i] = soundId;
		this.looping[i] = looping;
		vanillaGains[i] = vanillaGain;
		factors[i] = factor;
		gains[i] = Gain.apply(vanillaGain, factor);
		putSlot(handle, i);
		return true;
	}

	/**
	 * Vanilla recalculated a playing sound's volume (a slider change, a moving sound). Returns the gain to give it:
	 * vanilla's new gain times the sound's own factor, or {@code NaN} if the sound is not tracked.
	 */
	public float recalculated(long handle, float vanillaGain) {
		int i = indexOf(handle);
		if (i == EMPTY) {
			return Float.NaN;
		}
		vanillaGains[i] = vanillaGain;
		gains[i] = Gain.apply(vanillaGain, factors[i]);
		return gains[i];
	}

	/** The channel stopped. Ignored for a sound that is not tracked. */
	public void stop(long handle) {
		int i = indexOf(handle);
		if (i == EMPTY) {
			return;
		}
		removeSlot(handle);
		int last = --size;
		if (i != last) {
			handles[i] = handles[last];
			soundIds[i] = soundIds[last];
			looping[i] = looping[last];
			vanillaGains[i] = vanillaGains[last];
			factors[i] = factors[last];
			gains[i] = gains[last];
			slotIndexes[findSlot(handles[i])] = i;
		}
		soundIds[last] = null;
	}

	/** Stops tracking everything, for example when leaving a world. */
	public void clear() {
		for (int i = 0; i < size; i++) {
			soundIds[i] = null;
		}
		size = 0;
		java.util.Arrays.fill(slotIndexes, EMPTY);
		lastPrecedence = null;
		lastWhereVersion = -1;
		lastBypassed = false;
	}

	/**
	 * Applies whatever changed since the last call (see the class comment) and reports the sounds whose gain changed.
	 * With Bypass on, {@code precedence} and {@code where} are not used and may be {@code null}.
	 *
	 * @param where          where the player is, worked out for {@code precedence}
	 * @param bypassed       whether Bypass Sound Sculptor is on
	 * @param boostSupported whether factors above 1 can play (FR-5.4: the device allows it and the profile confirmed it);
	 *                       otherwise loops stop at 100%. Gains are capped at 2.0 either way (FR-5.6)
	 * @return how many sounds changed gain
	 */
	public int update(Precedence precedence, WhereYouAre where, boolean bypassed, boolean boostSupported,
			GainListener listener) {
		if (bypassed) {
			if (lastBypassed) {
				return 0;
			}
			lastBypassed = true;
			lastPrecedence = null;
			int changed = 0;
			for (int i = 0; i < size; i++) {
				if (setFactor(i, 1F, listener)) {
					changed++;
				}
			}
			return changed;
		}
		if (!lastBypassed && precedence == lastPrecedence && where.version() == lastWhereVersion) {
			return 0;
		}
		lastBypassed = false;
		lastPrecedence = precedence;
		lastWhereVersion = where.version();
		int changed = 0;
		SoundDecision out = scratch;
		float cap = boostSupported ? 200F : 100F;
		for (int i = 0; i < size; i++) {
			if (!looping[i]) {
				continue;
			}
			decisions++;
			precedence.decide(soundIds[i], where, out);
			if (setFactor(i, Math.min(out.volumePercent(), cap) / 100F, listener)) {
				changed++;
			}
		}
		return changed;
	}

	private boolean setFactor(int i, float factor, GainListener listener) {
		factors[i] = factor;
		float gain = Gain.apply(vanillaGains[i], factor);
		if (Math.abs(gain - gains[i]) < GAIN_EPSILON) {
			return false;
		}
		gains[i] = gain;
		listener.gainChanged(handles[i], gain);
		return true;
	}

	/** The gain last given to a tracked sound, or {@code NaN} if it is not tracked. */
	public float gain(long handle) {
		int i = indexOf(handle);
		return i == EMPTY ? Float.NaN : gains[i];
	}

	public boolean isTracked(long handle) {
		return indexOf(handle) != EMPTY;
	}

	/** Sounds tracked now. */
	public int size() {
		return size;
	}

	/**
	 * Estimated bytes this table keeps, for the Diagnostics memory readout (PRD 9.1). The arrays have a fixed size;
	 * it walks the tracked IDs, so call it on demand through {@code MemoryMeter}, not per tick.
	 */
	public long estimatedBytes() {
		long bytes = MemoryEstimate.object(11 * MemoryEstimate.REFERENCE + 4 + 3 * 8 + 1)
				+ MemoryEstimate.object(10 * MemoryEstimate.REFERENCE + 8 * 4)
				+ MemoryEstimate.array(8, CAPACITY) + MemoryEstimate.array(MemoryEstimate.REFERENCE, CAPACITY)
				+ MemoryEstimate.array(1, CAPACITY) + 3 * MemoryEstimate.array(4, CAPACITY)
				+ MemoryEstimate.array(8, SLOTS) + MemoryEstimate.array(4, SLOTS);
		for (int i = 0; i < size; i++) {
			bytes += MemoryEstimate.string(soundIds[i]);
		}
		return bytes;
	}

	/** Sounds not tracked because the table was full (or a handle was started twice), for diagnostics. */
	public long rejectedCount() {
		return rejected;
	}

	/** Loops re-decided so far, for diagnostics. */
	public long decisionCount() {
		return decisions;
	}

	// --- handle hash (linear probing, backward-shift deletion) ---

	private static int home(long handle) {
		long h = handle * 0x9E3779B97F4A7C15L;
		return (int) (h >>> 40) & (SLOTS - 1);
	}

	/** The hash slot holding {@code handle}, or {@code EMPTY}. */
	private int findSlot(long handle) {
		for (int s = home(handle);; s = (s + 1) & (SLOTS - 1)) {
			if (slotIndexes[s] == EMPTY) {
				return EMPTY;
			}
			if (slotHandles[s] == handle) {
				return s;
			}
		}
	}

	private int indexOf(long handle) {
		int s = findSlot(handle);
		return s == EMPTY ? EMPTY : slotIndexes[s];
	}

	private void putSlot(long handle, int index) {
		int s = home(handle);
		while (slotIndexes[s] != EMPTY) {
			s = (s + 1) & (SLOTS - 1);
		}
		slotHandles[s] = handle;
		slotIndexes[s] = index;
	}

	private void removeSlot(long handle) {
		int hole = findSlot(handle);
		slotIndexes[hole] = EMPTY;
		for (int s = (hole + 1) & (SLOTS - 1); slotIndexes[s] != EMPTY; s = (s + 1) & (SLOTS - 1)) {
			int want = home(slotHandles[s]);
			// Move the entry back into the hole if the hole lies between its home slot and where it sits now.
			if (((s - want) & (SLOTS - 1)) >= ((s - hole) & (SLOTS - 1))) {
				slotHandles[hole] = slotHandles[s];
				slotIndexes[hole] = slotIndexes[s];
				slotIndexes[s] = EMPTY;
				hole = s;
			}
		}
	}
}
