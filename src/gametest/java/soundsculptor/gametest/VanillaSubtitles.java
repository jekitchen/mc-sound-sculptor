package soundsculptor.gametest;

import java.util.ArrayList;
import java.util.List;

/**
 * What vanilla's subtitle overlay drew in its last frame, recorded by {@code SubtitleOverlayRecorder} (game tests only):
 * each row's text and its arrow, so a test can compare the cue box with what vanilla really shows (FR-7.4).
 */
public final class VanillaSubtitles {
	/** One subtitle row: its text, and "<", ">" or "" for its arrow. */
	public record Row(String text, String arrow) {
	}

	private static List<Row> drawing = new ArrayList<>();
	private static List<Row> lastFrame = List.of();
	private static String pendingArrow = "";

	private VanillaSubtitles() {
	}

	/** The rows of vanilla's last drawn frame. Render thread. */
	public static List<Row> lastFrame() {
		return lastFrame;
	}

	public static void startFrame() {
		lastFrame = List.copyOf(drawing);
		drawing = new ArrayList<>();
		pendingArrow = "";
	}

	public static void arrow(String arrow) {
		pendingArrow = arrow;
	}

	public static void text(String text) {
		drawing.add(new Row(text, pendingArrow));
		pendingArrow = "";
	}
}
