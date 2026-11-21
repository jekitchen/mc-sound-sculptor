package soundsculptor.core.cue;

/** One of a place's cue settings ({@link PlaceCues}); each is resolved on its own (PRD FR-7, FR-9). */
public enum CueField {
	/** FR-7.7 "Show muted-sound cues". */
	SHOW_CUES,
	/** FR-7.2 "Cue sounds without subtitles". */
	WITHOUT_SUBTITLES,
	/** FR-7.6 "New mutes get Cue". */
	NEW_MUTES_GET_CUE,
	/** FR-7.3, A-7 the cue text color. */
	COLOR,
	/** A-7 the cue text size. */
	TEXT_SCALE,
	/** Section 8.1 the cue box position (left/right and up/down together). */
	BOX_OFFSET
}
