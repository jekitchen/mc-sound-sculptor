package soundsculptor.core.trace;

import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.WhereYouAre;

/**
 * Where the player stood, for a trace (PRD FR-14.1, FR-14.2): at the sound's last play, or now for a sound not heard
 * yet.
 *
 * @param known whether a feet block is known; if not, no zone applies, only Everywhere
 * @param x     the block the player's feet are in (FR-8.4)
 */
public record Standing(boolean known, String worldKey, String dimension, int x, int y, int z) {
	/** No feet block known: only Everywhere applies. */
	public static final Standing NOWHERE = new Standing(false, "", "", 0, 0, 0);

	/** Where the player stood at {@code play}. */
	public static Standing of(LastPlay play) {
		return new Standing(play.feetKnown(), play.worldKey(), play.dimension(), play.feetX(), play.feetY(), play.feetZ());
	}

	/** Works out where you are for {@code p} from this place. */
	public WhereYouAre locate(Precedence p) {
		WhereYouAre where = new WhereYouAre();
		if (known) {
			where.update(p, worldKey, dimension, x, y, z);
		} else {
			where.updateNowhere(p);
		}
		return where;
	}
}
