package soundsculptor.core.cue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The lines in the muted-sound cue box (PRD FR-7.1, FR-7.3 to FR-7.5), kept the way vanilla's subtitle overlay keeps
 * its lines ({@code SubtitleOverlay$Subtitle} in 26.3), with a count and a cap of {@link #MAX_LINES} on top:
 * <ul>
 *   <li>A line is one text. It remembers its recent plays: where and when, up to {@link #PLAYS_PER_LINE}; a play at a
 *   place it already has replaces that one, as vanilla's {@code refresh} does. Plays older than the display time are
 *   forgotten, and a line with none left is gone (its count starts again).</li>
 *   <li>Every frame ({@link #lines}) each line picks vanilla's best play for where the listener is now: its only play,
 *   or else the most recent one in range (the farther one on a tie). That play decides the range check (a line whose
 *   play is out of range is hidden, not shown), the arrow and the fade.</li>
 *   <li>The display time is 3 seconds times vanilla's Notification Time option, as for subtitles. A looping sound's
 *   line stays at full brightness for its first half second, then fades to 75/255, as vanilla's does.</li>
 *   <li>A new text takes a free slot; if there is none, a hidden line's slot (the one played longest ago), so a new
 *   cue never pushes out a line that is shown while a hidden one keeps its slot; only then the shown line played
 *   longest ago.</li>
 * </ul>
 *
 * <p>The text is anything with {@code equals}: the HUD uses Minecraft's subtitle component, so a cue costs no new
 * text, and tests use strings. {@link #add} runs for every muted sound with a cue, which can be hundreds a second in a
 * farm, so it allocates nothing. Not thread-safe: add and read on the render thread.
 *
 * @param <T> the line text
 */
public final class CueBox<T> {
	public static final int MAX_LINES = 5;
	/** Like vanilla subtitles, a line lasts 3 seconds at Notification Time 1. */
	public static final long DISPLAY_MS = 3000;
	/** How many recent plays a line remembers; the oldest is forgotten first. */
	public static final int PLAYS_PER_LINE = 8;
	/** A looping sound's line stays at full brightness this long, as vanilla's does. */
	static final long LOOPING_FULL_BRIGHTNESS_MS = 500;

	private final List<Slot<T>> slots = new ArrayList<>(MAX_LINES);

	public CueBox() {
		for (int i = 0; i < MAX_LINES; i++) {
			slots.add(new Slot<>());
		}
	}

	/**
	 * Adds one play: to the line with the same text, or as a new line.
	 *
	 * @param positional false for a sound that plays relative to the listener (music, UI): it never gets an arrow and
	 *                   is never out of range
	 * @param looping    whether the sound loops; only a new line takes it, as vanilla's does
	 * @param range      the sound's vanilla hearing range in blocks; infinite when distance does not matter. Only a new
	 *                   line takes it, as vanilla's does
	 * @param timeScale  vanilla's Notification Time option (1 = 3 seconds)
	 */
	public void add(T text, boolean positional, boolean looping, double x, double y, double z, float range, long nowMs,
			double timeScale) {
		double window = DISPLAY_MS * timeScale;
		for (int i = 0; i < MAX_LINES; i++) {
			Slot<T> slot = slots.get(i);
			if (slot.live(nowMs, window) && (slot.text == text || slot.text.equals(text))) {
				slot.count++;
				slot.play(x, y, z, nowMs);
				return;
			}
		}
		Slot<T> target = null;
		for (int i = 0; i < MAX_LINES && target == null; i++) {
			if (!slots.get(i).live(nowMs, window)) {
				target = slots.get(i);
			}
		}
		if (target == null) {
			target = oldest(true);
		}
		if (target == null) {
			target = oldest(false);
		}
		target.start(text, positional, looping, range, nowMs);
		target.play(x, y, z, nowMs);
	}

	/** The live line played longest ago, among the hidden ones only or among all; {@code null} if there is none. */
	private Slot<T> oldest(boolean hiddenOnly) {
		Slot<T> found = null;
		for (int i = 0; i < MAX_LINES; i++) {
			Slot<T> slot = slots.get(i);
			if ((!hiddenOnly || slot.hidden) && (found == null || slot.latestMs < found.latestMs)) {
				found = slot;
			}
		}
		return found;
	}

	/**
	 * The lines to show now for a listener at {@code (lx, ly, lz)}, oldest line first (the bottom row, as vanilla stacks
	 * them). Forgets plays older than the display time and marks lines out of range as hidden. Called once per frame by
	 * the HUD; may allocate.
	 *
	 * @param timeScale vanilla's Notification Time option (1 = 3 seconds)
	 */
	public List<CueLine<T>> lines(long nowMs, double timeScale, double lx, double ly, double lz) {
		double window = DISPLAY_MS * timeScale;
		List<CueLine<T>> lines = new ArrayList<>(MAX_LINES);
		List<Slot<T>> shown = new ArrayList<>(MAX_LINES);
		for (Slot<T> slot : slots) {
			if (!slot.live(nowMs, window)) {
				continue;
			}
			slot.purge(nowMs, window);
			int best = slot.best(lx, ly, lz);
			slot.hidden = best < 0 || !slot.audible(best, lx, ly, lz);
			if (!slot.hidden) {
				lines.add(new CueLine<>(slot.text, slot.count, slot.positional, slot.xs[best], slot.ys[best], slot.zs[best],
						slot.fade(best, nowMs, timeScale)));
				shown.add(slot);
			}
		}
		// Oldest line first: sort by when each line appeared.
		List<Integer> order = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			order.add(i);
		}
		order.sort(Comparator.comparingLong(i -> shown.get(i).createdMs));
		List<CueLine<T>> sorted = new ArrayList<>(lines.size());
		for (int i : order) {
			sorted.add(lines.get(i));
		}
		return sorted;
	}

	/** Whether no line is live (shown or hidden); cheap, for the HUD to skip a frame with nothing to draw. */
	public boolean isEmpty(long nowMs, double timeScale) {
		double window = DISPLAY_MS * timeScale;
		for (int i = 0; i < MAX_LINES; i++) {
			if (slots.get(i).live(nowMs, window)) {
				return false;
			}
		}
		return true;
	}

	/** Removes every line, e.g. when the master switch is turned off or the world changes. */
	public void clear() {
		for (Slot<T> slot : slots) {
			slot.text = null;
			slot.size = 0;
			slot.hidden = false;
		}
	}

	private static final class Slot<T> {
		T text;
		int count;
		boolean positional;
		boolean looping;
		float range;
		long createdMs;
		long latestMs;
		/** Out of range at the last {@link CueBox#lines}; such a line gives up its slot first. */
		boolean hidden;
		/** The recent plays, {@code size} of them, in no particular order. */
		final double[] xs = new double[PLAYS_PER_LINE];
		final double[] ys = new double[PLAYS_PER_LINE];
		final double[] zs = new double[PLAYS_PER_LINE];
		final long[] times = new long[PLAYS_PER_LINE];
		int size;

		void start(T newText, boolean isPositional, boolean isLooping, float hearingRange, long nowMs) {
			text = newText;
			count = 1;
			positional = isPositional;
			looping = isLooping;
			range = hearingRange;
			createdMs = nowMs;
			hidden = false;
			size = 0;
		}

		/** A play: replaces one at the same place (vanilla's {@code refresh}), or the oldest when full. */
		void play(double x, double y, double z, long nowMs) {
			int at = -1;
			for (int i = 0; i < size; i++) {
				if (xs[i] == x && ys[i] == y && zs[i] == z) {
					at = i;
					break;
				}
			}
			if (at < 0 && size < PLAYS_PER_LINE) {
				at = size++;
			} else if (at < 0) {
				at = 0;
				for (int i = 1; i < size; i++) {
					if (times[i] < times[at]) {
						at = i;
					}
				}
			}
			xs[at] = x;
			ys[at] = y;
			zs[at] = z;
			times[at] = nowMs;
			latestMs = nowMs;
		}

		/** Whether a play is still within the display time, as vanilla's {@code purgeOldInstances} keeps it. */
		boolean live(long nowMs, double window) {
			return text != null && size > 0 && nowMs - latestMs <= window;
		}

		/** Forgets plays older than the display time. */
		void purge(long nowMs, double window) {
			int kept = 0;
			for (int i = 0; i < size; i++) {
				if (nowMs - times[i] <= window) {
					xs[kept] = xs[i];
					ys[kept] = ys[i];
					zs[kept] = zs[i];
					times[kept] = times[i];
					kept++;
				}
			}
			size = kept;
		}

		/** Vanilla's {@code getBestSubtitleCandidate}: the only play, or the latest in range (farther on a tie); -1 if none. */
		int best(double lx, double ly, double lz) {
			if (size == 1) {
				return 0;
			}
			int best = -1;
			double bestDistanceSq = 0;
			for (int i = 0; i < size; i++) {
				double d = distanceSq(i, lx, ly, lz);
				if (!inRange(d)) {
					continue;
				}
				if (best < 0 || times[i] > times[best] || (times[i] == times[best] && d > bestDistanceSq)) {
					best = i;
					bestDistanceSq = d;
				}
			}
			return best;
		}

		/** Vanilla's {@code isAudibleFrom}: infinite range, or the best play strictly in range. */
		boolean audible(int best, double lx, double ly, double lz) {
			return !positional || Float.isInfinite(range) || inRange(distanceSq(best, lx, ly, lz));
		}

		private boolean inRange(double distanceSq) {
			if (!positional || Float.isInfinite(range)) {
				return true;
			}
			double r = range;
			return distanceSq < r * r;
		}

		private double distanceSq(int i, double lx, double ly, double lz) {
			double dx = xs[i] - lx;
			double dy = ys[i] - ly;
			double dz = zs[i] - lz;
			return dx * dx + dy * dy + dz * dz;
		}

		/** Vanilla's brightness curve as a fraction: 0 at full brightness, 1 at the end (75/255). */
		double fade(int best, long nowMs, double timeScale) {
			long full = looping ? LOOPING_FULL_BRIGHTNESS_MS : 0;
			double elapsed = Math.max(0, nowMs - times[best] - full);
			double span = (DISPLAY_MS - full) * timeScale;
			return span <= 0 ? 1 : Math.min(1, elapsed / span);
		}
	}
}
