package soundsculptor.core.trace;

/**
 * Where the player stood and how a sound last played: enough to rebuild its trace later (PRD FR-14.6).
 *
 * @param feetKnown     whether the player's feet block was known; if not, only Everywhere applied
 * @param feetX         the block the player's feet were in (FR-8.4)
 * @param volumePercent the volume Sound Sculptor actually applied, to spot rule changes since
 * @param dropped       whether a repeat limit dropped the play
 * @param cueShown      whether a muted-sound cue was shown
 */
public record LastPlay(boolean feetKnown, String worldKey, String dimension, int feetX, int feetY, int feetZ,
		float volumePercent, boolean dropped, boolean cueShown) {
}
