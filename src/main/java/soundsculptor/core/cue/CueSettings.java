package soundsculptor.core.cue;

/**
 * The cue settings in effect where the player stands (PRD FR-7.2, FR-7.3, FR-7.6, FR-7.7, A-7): each one from the
 * smallest place that sets it, or Everywhere ({@link CueResolver}).
 *
 * @param showCues                  master switch "Show muted-sound cues"; independent of vanilla Subtitles
 * @param cueSoundsWithoutSubtitles show the sound ID for sounds that have no subtitle
 * @param newMuteRulesCueOn         the Cue setting new mutes start with
 * @param color                     the cue text color, {@code 0xRRGGBB}
 * @param textScale                 the cue text size in percent
 * @param offsetX                   the cue box offset in GUI pixels, positive is right
 * @param offsetY                   the cue box offset in GUI pixels, positive is down
 */
public record CueSettings(boolean showCues, boolean cueSoundsWithoutSubtitles, boolean newMuteRulesCueOn, int color,
		int textScale, int offsetX, int offsetY) {
	public static final CueSettings DEFAULT = of(PlaceCues.EVERYWHERE_DEFAULTS);

	/** The switches only; the look (color, size, position) at its defaults. */
	public CueSettings(boolean showCues, boolean cueSoundsWithoutSubtitles, boolean newMuteRulesCueOn) {
		this(showCues, cueSoundsWithoutSubtitles, newMuteRulesCueOn, PlaceCues.DEFAULT_COLOR, PlaceCues.DEFAULT_TEXT_SCALE, 0, 0);
	}

	/**
	 * The values of a place whose settings are all set.
	 *
	 * @throws IllegalArgumentException if a setting is Unchanged
	 */
	public static CueSettings of(PlaceCues complete) {
		if (!complete.isComplete()) {
			throw new IllegalArgumentException("every cue setting must be set: " + complete);
		}
		return new CueSettings(complete.showCues(), complete.withoutSubtitles(), complete.newMutesGetCue(), complete.color(),
				complete.textScale(), complete.boxOffset().x(), complete.boxOffset().y());
	}
}
