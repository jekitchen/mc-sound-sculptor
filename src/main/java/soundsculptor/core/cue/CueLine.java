package soundsculptor.core.cue;

/**
 * One line the cue box shows this frame (PRD FR-7.3 to FR-7.5). The HUD draws it as "(muted)", a muted-speaker icon,
 * the text in italics, then "×count" when {@code count > 1}, with the arrow on the side the sound came from.
 *
 * @param positional false for a sound that plays relative to the listener: no arrow
 * @param x          where the line's best play is (vanilla's best subtitle candidate for the listener now)
 * @param fade       0 at full brightness, 1 at the end of the display time, from that play ({@link CueBox})
 * @param <T>        the line text
 */
public record CueLine<T>(T text, int count, boolean positional, double x, double y, double z, double fade) {
	/** 0 when the line is new, 1 when it is about to disappear; for fading like vanilla subtitles. */
	public double fadeFraction() {
		return fade;
	}

	/**
	 * The arrow for a listener at {@code l} facing {@code forward}, with {@code right} to its right (both unit vectors),
	 * as vanilla subtitles work it out (FR-7.4). No arrow for a sound without a position.
	 */
	public CueDirection direction(double lx, double ly, double lz, double fx, double fy, double fz, double rx, double ry, double rz) {
		if (!positional) {
			return CueDirection.NON_POSITIONAL;
		}
		return CueDirection.from(x - lx, y - ly, z - lz, fx, fy, fz, rx, ry, rz);
	}
}
