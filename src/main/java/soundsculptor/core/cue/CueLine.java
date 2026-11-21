package soundsculptor.core.cue;

/**
 * One visible line in the cue box (PRD FR-7.3 to FR-7.5). The HUD draws it as "(muted)", a muted-speaker icon, the
 * text in italics, then "×count" when {@code count > 1}, with the arrow on the side the sound came from.
 *
 * @param positional false for a sound that plays relative to the listener: no arrow, never out of range
 * @param x          where the latest repeat played
 * @param range      the sound's vanilla hearing range in blocks, infinite when distance does not matter
 * @param ageMs      time since the latest repeat; the line fades out as this approaches {@link CueBox#DISPLAY_MS}
 * @param <T>        the line text
 */
public record CueLine<T>(T text, int count, boolean positional, double x, double y, double z, float range, long ageMs) {
	/** 0 when the line is new, 1 when it is about to disappear; for fading like vanilla subtitles. */
	public double fadeFraction() {
		return Math.min(1.0, ageMs / (double) CueBox.DISPLAY_MS);
	}

	/**
	 * Whether a listener at {@code (lx, ly, lz)} is still in range, like vanilla's per-frame subtitle check (strictly
	 * closer than the range). Always true for a sound without a position or with an infinite range.
	 */
	public boolean audibleFrom(double lx, double ly, double lz) {
		if (!positional || Float.isInfinite(range)) {
			return true;
		}
		double dx = x - lx;
		double dy = y - ly;
		double dz = z - lz;
		double r = range;
		return dx * dx + dy * dy + dz * dz < r * r;
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
