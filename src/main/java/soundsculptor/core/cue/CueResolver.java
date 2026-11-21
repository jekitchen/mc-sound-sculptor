package soundsculptor.core.cue;

import java.util.ArrayList;
import java.util.List;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.WhereYouAre;
import soundsculptor.core.zones.Zone;

/**
 * Which cue settings apply (PRD FR-7, decided 2026-11-21): like entries under FR-9, each setting comes from the first
 * place in the smallest-first order of the zones the player stands in that sets it, and Everywhere, whose settings are
 * always set, answers the rest. The order depends only on where the player stands, so the result is worked out again
 * only when that order or the profile changes, never per sound or per frame ({@code SoundStartDecider#cues}). Pure Java.
 */
public final class CueResolver {
	private CueResolver() {
	}

	/**
	 * @param smallestFirst the zones the player stands in, in precedence order (FR-9 step 1)
	 * @param everywhere    Everywhere's settings, all set
	 * @throws IllegalArgumentException if a setting of {@code everywhere} is Unchanged
	 */
	public static CueResolution resolve(List<Zone> smallestFirst, PlaceCues everywhere) {
		if (!everywhere.isComplete()) {
			throw new IllegalArgumentException("Everywhere's cue settings must all be set: " + everywhere);
		}
		CueField[] fields = CueField.values();
		PlaceRef[] sources = new PlaceRef[fields.length];
		PlaceCues merged = PlaceCues.UNCHANGED;
		for (Zone zone : smallestFirst) {
			PlaceCues cues = zone.cues();
			for (CueField field : fields) {
				if (!merged.has(field) && cues.has(field)) {
					merged = merged.copy(field, cues);
					sources[field.ordinal()] = PlaceRef.zone(zone.id());
				}
			}
		}
		for (CueField field : fields) {
			if (!merged.has(field)) {
				merged = merged.copy(field, everywhere);
				sources[field.ordinal()] = PlaceRef.EVERYWHERE;
			}
		}
		return new CueResolution(CueSettings.of(merged), List.of(sources));
	}

	/**
	 * The settings where the player stands now.
	 *
	 * @param where worked out for {@code p}
	 * @throws IllegalArgumentException if {@code where} belongs to another snapshot
	 */
	public static CueResolution resolve(Precedence p, WhereYouAre where) {
		if (where.precedence() != p) {
			throw new IllegalArgumentException("where you are was worked out for another profile snapshot");
		}
		List<Zone> zones = new ArrayList<>(where.count());
		for (int k = 0; k < where.count(); k++) {
			zones.add(p.zoneAt(where.zone(k)));
		}
		return resolve(zones, p.everywhereCues());
	}

	/**
	 * What {@code zone}'s Unchanged settings pass on to, for its Cues pop-up: the places that come after it where it
	 * stands at its center block, that is the enabled zones of its world and dimension containing that block that are
	 * bigger than it (or as big and lower in the zone list), then Everywhere. The zone itself takes no part.
	 *
	 * @param zone the zone as edited, which may not be saved yet (a new zone comes last in the list)
	 */
	public static CueResolution inheritedBy(Profile profile, Zone zone) {
		return resolve(biggerAround(profile, zone), profile.everywhereCues());
	}

	/** The zones after {@code zone} at its center block, smallest first; see {@link #inheritedBy}. */
	static List<Zone> biggerAround(Profile profile, Zone zone) {
		int bx = (int) Math.floor(zone.anchor().x());
		int by = (int) Math.floor(zone.anchor().y());
		int bz = (int) Math.floor(zone.anchor().z());
		List<Zone> zones = profile.zones();
		int ownIndex = zones.size();
		for (int i = 0; i < zones.size(); i++) {
			if (zones.get(i).id().equals(zone.id())) {
				ownIndex = i;
			}
		}
		double ownVolume = zone.shape().volume();
		List<Zone> after = new ArrayList<>();
		List<Double> volumes = new ArrayList<>();
		for (int i = 0; i < zones.size(); i++) {
			Zone other = zones.get(i);
			if (i == ownIndex || !other.isActiveIn(zone.worldKey(), zone.dimension()) || !other.containsBlock(bx, by, bz)) {
				continue;
			}
			double volume = other.shape().volume();
			if (volume < ownVolume || (volume == ownVolume && i < ownIndex)) {
				continue;
			}
			// Zones arrive in list order: an insertion sort keeps equal volumes in list order, as WhereYouAre does.
			int k = after.size();
			while (k > 0 && volumes.get(k - 1) > volume) {
				k--;
			}
			after.add(k, other);
			volumes.add(k, volume);
		}
		return after;
	}
}
