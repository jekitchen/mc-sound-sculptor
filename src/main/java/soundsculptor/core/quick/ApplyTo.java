package soundsculptor.core.quick;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.zones.FeetBlock;
import soundsculptor.core.zones.Zone;

/**
 * The choices of the Sounds tab's "Apply to" selector (PRD FR-1.5): Everywhere first, then each zone the player is
 * standing in, in the order of the profile's zone list. Not on the hot path; the screen rebuilds the list when it
 * refreshes (at most 4 times a second, FR-1.6).
 *
 * <p>"Standing in" a zone means the player is on the side the zone affects, so a rule added there changes sounds
 * around the player:
 * <ul>
 *   <li>the zone is turned on;</li>
 *   <li>the zone must be in the player's world and dimension (FR-8.1);</li>
 *   <li>an Inside zone counts when the player is in its shape, edge included; an Outside zone counts when the player
 *       is outside its shape (FR-8.4). A fade edge does not matter: the weight at the boundary may be 0, but the
 *       player is still in the zone.</li>
 * </ul>
 */
public final class ApplyTo {
	private ApplyTo() {
	}

	/**
	 * The selector's choices for the player whose feet are in {@code feet}: Everywhere, then each zone they stand in
	 * (FR-8.4).
	 *
	 * @param worldKey  the current world key (FR-11.1)
	 * @param dimension the current dimension, e.g. {@code minecraft:overworld}
	 */
	public static List<ApplyTarget> choices(Profile profile, String worldKey, String dimension, FeetBlock feet) {
		List<ApplyTarget> choices = new ArrayList<>();
		choices.add(ApplyTarget.EVERYWHERE);
		for (Zone zone : profile.zones()) {
			if (feet != null && feet.isIn(zone, worldKey, dimension)) {
				choices.add(new ApplyTarget(zone.id(), zone.name()));
			}
		}
		return List.copyOf(choices);
	}

	/**
	 * The choice to show selected: the zone chosen last time ({@code lastZoneId}, null for Everywhere) if it is still
	 * offered, else Everywhere.
	 */
	public static ApplyTarget select(List<ApplyTarget> choices, String lastZoneId) {
		if (lastZoneId != null) {
			for (ApplyTarget choice : choices) {
				if (lastZoneId.equals(choice.zoneId())) {
					return choice;
				}
			}
		}
		return ApplyTarget.EVERYWHERE;
	}
}
