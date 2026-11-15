package soundsculptor.core;

import java.util.List;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.WhereYouAre;
import soundsculptor.core.zones.Zone;

/** Short ways for tests to build places from a plain entry list: the entries become the place's Quick changes. */
public final class Fixtures {
	private Fixtures() {
	}

	/** A place whose only set is Quick changes with these entries; no sets at all for an empty list. */
	public static PlaceSets quick(List<SetEntry> entries) {
		return PlaceSets.EMPTY.withQuickEntries(entries);
	}

	public static PlaceSets quick(SetEntry... entries) {
		return quick(List.of(entries));
	}

	/** Everywhere's Quick changes holding {@code everywhere}, these zones and an empty library. */
	public static Precedence precedence(List<SetEntry> everywhere, List<Zone> zones) {
		return new Precedence(quick(everywhere), zones, SetLibrary.EMPTY);
	}

	/** Where you are for {@code p} with your feet in block {@code (x, y, z)} of this world and dimension. */
	public static WhereYouAre at(Precedence p, String worldKey, String dimension, int x, int y, int z) {
		WhereYouAre where = new WhereYouAre();
		where.update(p, worldKey, dimension, x, y, z);
		return where;
	}

	/** Where you are for {@code p} with no feet block known: Everywhere only. */
	public static WhereYouAre nowhere(Precedence p) {
		WhereYouAre where = new WhereYouAre();
		where.updateNowhere(p);
		return where;
	}
}
