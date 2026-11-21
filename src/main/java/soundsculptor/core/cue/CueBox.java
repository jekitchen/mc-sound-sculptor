package soundsculptor.core.cue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The lines in the muted-sound cue box (PRD FR-7.3 to FR-7.5): at most {@link #MAX_LINES}, a repeat of the same text
 * merges into its line with a count, and lines disappear {@link #DISPLAY_MS} after their latest repeat. When a new
 * line arrives and the box is full, the line repeated longest ago is dropped.
 *
 * <p>Each line keeps where its latest repeat played and the sound's vanilla hearing range, so the HUD can work out the
 * arrow and the range check every frame from where the listener is then, exactly as vanilla subtitles do (FR-7.1,
 * FR-7.4). The text is anything with {@code equals}: the HUD uses Minecraft's subtitle component, so a cue costs no
 * new text, and tests use strings.
 *
 * <p>{@link #add} runs for every muted sound with a cue, which can be hundreds a second in a farm, so it allocates
 * nothing. Not thread-safe: add and read on the render thread.
 *
 * @param <T> the line text
 */
public final class CueBox<T> {
	public static final int MAX_LINES = 5;
	/** Like vanilla subtitles, a line fades out after 3 seconds. */
	public static final long DISPLAY_MS = 3000;

	private final List<Slot<T>> slots = new ArrayList<>(MAX_LINES);

	public CueBox() {
		for (int i = 0; i < MAX_LINES; i++) {
			slots.add(new Slot<>());
		}
	}

	/**
	 * Adds one cue: merges with a visible line with the same text, or takes a free or the oldest slot. The line then
	 * points at this play.
	 *
	 * @param positional false for a sound that plays relative to the listener (music, UI): it never gets an arrow and
	 *                   is never out of range
	 * @param range      the sound's vanilla hearing range in blocks; infinite when distance does not matter
	 */
	public void add(T text, boolean positional, double x, double y, double z, float range, long nowMs) {
		for (int i = 0; i < MAX_LINES; i++) {
			Slot<T> slot = slots.get(i);
			if (slot.visible(nowMs) && (slot.text == text || slot.text.equals(text))) {
				slot.count++;
				slot.at(positional, x, y, z, range, nowMs);
				return;
			}
		}
		// A free (or faded) slot if there is one, otherwise the line repeated longest ago.
		Slot<T> target = null;
		for (int i = 0; i < MAX_LINES; i++) {
			Slot<T> slot = slots.get(i);
			if (!slot.visible(nowMs)) {
				target = slot;
				break;
			}
			if (target == null || slot.lastMs < target.lastMs) {
				target = slot;
			}
		}
		target.text = text;
		target.count = 1;
		target.at(positional, x, y, z, range, nowMs);
	}

	/** Visible lines, newest first. Called once per frame by the HUD; may allocate. */
	public List<CueLine<T>> visibleLines(long nowMs) {
		List<CueLine<T>> lines = new ArrayList<>(MAX_LINES);
		for (Slot<T> slot : slots) {
			if (slot.visible(nowMs)) {
				lines.add(new CueLine<>(slot.text, slot.count, slot.positional, slot.x, slot.y, slot.z, slot.range,
						nowMs - slot.lastMs));
			}
		}
		lines.sort(Comparator.comparingLong(CueLine::ageMs));
		return lines;
	}

	/** Whether any line is visible; cheap, for the HUD to skip a frame with nothing to draw. */
	public boolean isEmpty(long nowMs) {
		for (int i = 0; i < MAX_LINES; i++) {
			if (slots.get(i).visible(nowMs)) {
				return false;
			}
		}
		return true;
	}

	/** Removes every line, e.g. when the master switch is turned off or the world changes. */
	public void clear() {
		for (Slot<T> slot : slots) {
			slot.text = null;
		}
	}

	private static final class Slot<T> {
		T text;
		int count;
		boolean positional;
		double x;
		double y;
		double z;
		float range;
		long lastMs;

		void at(boolean isPositional, double px, double py, double pz, float hearingRange, long nowMs) {
			positional = isPositional;
			x = px;
			y = py;
			z = pz;
			range = hearingRange;
			lastMs = nowMs;
		}

		boolean visible(long nowMs) {
			return text != null && nowMs - lastMs < DISPLAY_MS;
		}
	}
}
