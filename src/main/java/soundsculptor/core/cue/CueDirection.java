package soundsculptor.core.cue;

/**
 * The left/right arrow on a cue line, worked out exactly like vanilla subtitles (PRD FR-7.4): no arrow when the
 * source is clearly in front of the listener, otherwise {@code <} or {@code >} by side. Never a distance or anything
 * finer than vanilla shows.
 *
 * <p>Vanilla's rule ({@code SubtitleOverlay.extractRenderState} in 26.3): with {@code d} the normalised direction from
 * the listener to the sound, {@code forward = d · listenerForward} and {@code right = d · listenerRight}; no arrow if
 * {@code forward > 0.5}, else {@code >} if {@code right > 0} and {@code <} if {@code right < 0}. Vanilla works this
 * out every frame from where the listener is then, and so does the cue box.
 */
public enum CueDirection {
	NONE,
	LEFT,
	RIGHT;

	/** Sounds without a world position never get an arrow. */
	public static final CueDirection NON_POSITIONAL = NONE;

	private static final double IN_FRONT = 0.5;
	/** {@code Vec3.normalize} gives the zero vector below this length. */
	private static final double MIN_LENGTH = 1.0E-5F;

	public static CueDirection of(double rightDot, double forwardDot) {
		if (forwardDot > IN_FRONT) {
			return NONE;
		}
		if (rightDot > 0) {
			return RIGHT;
		}
		return rightDot < 0 ? LEFT : NONE;
	}

	/**
	 * The arrow for a sound at offset {@code (dx, dy, dz)} from the listener, which faces {@code forward} with
	 * {@code right} to its right (unit vectors). The offset is normalised as vanilla's {@code Vec3.normalize} does: a
	 * sound at the listener's own position gets no arrow.
	 */
	public static CueDirection from(double dx, double dy, double dz, double fx, double fy, double fz, double rx, double ry, double rz) {
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < MIN_LENGTH) {
			return of(0, 0);
		}
		double nx = dx / length;
		double ny = dy / length;
		double nz = dz / length;
		return of(rx * nx + ry * ny + rz * nz, fx * nx + fy * ny + fz * nz);
	}
}
