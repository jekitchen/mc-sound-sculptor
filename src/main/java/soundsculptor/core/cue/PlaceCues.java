package soundsculptor.core.cue;

/**
 * One place's cue settings (PRD FR-7.2, FR-7.3, FR-7.6, FR-7.7, A-7): Everywhere and every zone has one. A
 * {@code null} value is <b>Unchanged</b>: the place passes that setting on to the next bigger place where the player
 * stands, and finally to Everywhere, in the same smallest-first order FR-9 uses for entries ({@link CueResolver}).
 * Everywhere's settings are always {@linkplain #isComplete complete}, so it always answers.
 *
 * <p>Values are always in range: the color keeps only its RGB bits, the text size is clamped to
 * {@value #MIN_TEXT_SCALE}% to {@value #MAX_TEXT_SCALE}%, and the box offset to ±{@value #MAX_OFFSET} GUI pixels.
 * The two parts of the box position are one setting: both set, or both Unchanged.
 *
 * @param showCues         FR-7.7 "Show muted-sound cues"; decided when a sound starts
 * @param withoutSubtitles FR-7.2 "Cue sounds without subtitles"; decided when a sound starts
 * @param newMutesGetCue   FR-7.6 "New mutes get Cue"; read when a mute is made
 * @param color            FR-7.3, A-7 the cue text color, {@code 0xRRGGBB}
 * @param textScale        A-7 the cue text size in percent
 * @param boxOffset        section 8.1 where the cue box is moved to, in GUI pixels
 */
public record PlaceCues(Boolean showCues, Boolean withoutSubtitles, Boolean newMutesGetCue, Integer color, Integer textScale,
		Offset boxOffset) {
	/** FR-7.3 "light blue-gray"; 10:1 contrast on black. */
	public static final int DEFAULT_COLOR = 0xA9BCD0;
	/** A-7: cue text from half size (decided 2026-11-21, owner request) to double size. */
	public static final int MIN_TEXT_SCALE = 50;
	public static final int MAX_TEXT_SCALE = 200;
	public static final int DEFAULT_TEXT_SCALE = 100;
	/** The text size slider's step. */
	public static final int TEXT_SCALE_STEP = 10;
	/** The offset can move the cue box this far in each direction; the renderer still keeps it on screen. */
	public static final int MAX_OFFSET = 1000;

	/** Every setting Unchanged: what a new zone has. */
	public static final PlaceCues UNCHANGED = new PlaceCues(null, null, null, null, null, null);
	/** Everywhere's settings until the player changes them: what the app-wide settings defaulted to before. */
	public static final PlaceCues EVERYWHERE_DEFAULTS = new PlaceCues(true, false, true, DEFAULT_COLOR, DEFAULT_TEXT_SCALE,
			Offset.ZERO);

	/** The cue box offset, in GUI pixels: positive x is right, positive y is down. Each part is clamped. */
	public record Offset(int x, int y) {
		public static final Offset ZERO = new Offset(0, 0);

		public Offset {
			x = clamp(x, -MAX_OFFSET, MAX_OFFSET);
			y = clamp(y, -MAX_OFFSET, MAX_OFFSET);
		}
	}

	public PlaceCues {
		if (color != null) {
			color = color & 0xFFFFFF;
		}
		if (textScale != null) {
			textScale = clamp(textScale, MIN_TEXT_SCALE, MAX_TEXT_SCALE);
		}
	}

	static int clamp(long value, int min, int max) {
		return (int) Math.max(min, Math.min(max, value));
	}

	/** Whether every setting is set (Everywhere's always are). */
	public boolean isComplete() {
		for (CueField field : CueField.values()) {
			if (!has(field)) {
				return false;
			}
		}
		return true;
	}

	/** Whether every setting is Unchanged. */
	public boolean isUnchanged() {
		return equals(UNCHANGED);
	}

	/** Whether this place sets {@code field}, rather than leaving it Unchanged. */
	public boolean has(CueField field) {
		return switch (field) {
			case SHOW_CUES -> showCues != null;
			case WITHOUT_SUBTITLES -> withoutSubtitles != null;
			case NEW_MUTES_GET_CUE -> newMutesGetCue != null;
			case COLOR -> color != null;
			case TEXT_SCALE -> textScale != null;
			case BOX_OFFSET -> boxOffset != null;
		};
	}

	/** The same settings with {@code field} Unchanged. */
	public PlaceCues cleared(CueField field) {
		return copy(field, UNCHANGED);
	}

	/** The same settings with {@code field} taken from {@code from} (set or Unchanged, as it is there). */
	public PlaceCues copy(CueField field, PlaceCues from) {
		return switch (field) {
			case SHOW_CUES -> withShowCues(from.showCues);
			case WITHOUT_SUBTITLES -> withWithoutSubtitles(from.withoutSubtitles);
			case NEW_MUTES_GET_CUE -> withNewMutesGetCue(from.newMutesGetCue);
			case COLOR -> withColor(from.color);
			case TEXT_SCALE -> withTextScale(from.textScale);
			case BOX_OFFSET -> withBoxOffset(from.boxOffset);
		};
	}

	/** Each Unchanged setting filled in from {@code fallback}. */
	public PlaceCues orElse(PlaceCues fallback) {
		PlaceCues out = this;
		for (CueField field : CueField.values()) {
			if (!out.has(field)) {
				out = out.copy(field, fallback);
			}
		}
		return out;
	}

	/**
	 * Migration from the app-wide cue settings of earlier versions (decided 2026-11-21): these settings with the five
	 * that were app-wide (show, without subtitles, color, text size, box position) taken from {@code appWide} where it
	 * sets them. "New mutes get Cue" was already per profile, so it is kept.
	 */
	public PlaceCues seededFrom(PlaceCues appWide) {
		PlaceCues out = this;
		for (CueField field : CueField.values()) {
			if (field != CueField.NEW_MUTES_GET_CUE && appWide.has(field)) {
				out = out.copy(field, appWide);
			}
		}
		return out;
	}

	public PlaceCues withShowCues(Boolean value) {
		return new PlaceCues(value, withoutSubtitles, newMutesGetCue, color, textScale, boxOffset);
	}

	public PlaceCues withWithoutSubtitles(Boolean value) {
		return new PlaceCues(showCues, value, newMutesGetCue, color, textScale, boxOffset);
	}

	public PlaceCues withNewMutesGetCue(Boolean value) {
		return new PlaceCues(showCues, withoutSubtitles, value, color, textScale, boxOffset);
	}

	public PlaceCues withColor(Integer rgb) {
		return new PlaceCues(showCues, withoutSubtitles, newMutesGetCue, rgb, textScale, boxOffset);
	}

	/** Clamped to {@value #MIN_TEXT_SCALE}..{@value #MAX_TEXT_SCALE}. */
	public PlaceCues withTextScale(Integer percent) {
		return new PlaceCues(showCues, withoutSubtitles, newMutesGetCue, color, percent, boxOffset);
	}

	public PlaceCues withBoxOffset(Offset value) {
		return new PlaceCues(showCues, withoutSubtitles, newMutesGetCue, color, textScale, value);
	}
}
