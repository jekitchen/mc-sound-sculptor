package soundsculptor.core.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import soundsculptor.core.Fixtures;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.WhereYouAre;
import soundsculptor.core.zones.Zone;

/**
 * Cue settings per place (PRD FR-7, decided 2026-11-21): like entries under FR-9, the smallest zone the player stands in
 * that sets a value wins, Unchanged passes on to the next bigger zone, and Everywhere answers the rest.
 */
class CueResolverTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final PlaceCues EVERYWHERE = PlaceCues.EVERYWHERE_DEFAULTS;

	private static Zone box(String id, double size, PlaceCues cues) {
		return new Zone(id, "Zone " + id, true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Box(size, size, size),
				PlaceSets.EMPTY, cues);
	}

	@Test
	void everywhereAloneDecidesEverything() {
		CueResolution r = CueResolver.resolve(List.of(), EVERYWHERE);
		assertEquals(CueSettings.DEFAULT, r.settings());
		for (CueField field : CueField.values()) {
			assertEquals(PlaceRef.EVERYWHERE, r.source(field), field.name());
		}
		assertEquals(new CueSettings(true, false, true, 0xA9BCD0, 100, 0, 0), r.settings(), "today's defaults");
	}

	@Test
	void aZoneOverridesOnlyWhatItSets() {
		Zone base = box("base", 20, PlaceCues.UNCHANGED.withShowCues(false));
		CueResolution r = CueResolver.resolve(List.of(base), EVERYWHERE.withColor(0xF0E442));
		assertEquals(false, r.settings().showCues());
		assertEquals(PlaceRef.zone("base"), r.source(CueField.SHOW_CUES));
		assertEquals(0xF0E442, r.settings().color(), "the color is Unchanged in the zone: Everywhere's");
		assertEquals(PlaceRef.EVERYWHERE, r.source(CueField.COLOR));
	}

	@Test
	void theSmallerOfTwoNestedZonesWins() {
		Zone room = box("room", 4, PlaceCues.UNCHANGED.withTextScale(50));
		Zone base = box("base", 40, PlaceCues.UNCHANGED.withTextScale(150).withShowCues(false));
		CueResolution r = CueResolver.resolve(List.of(room, base), EVERYWHERE);
		assertEquals(50, r.settings().textScale());
		assertEquals(PlaceRef.zone("room"), r.source(CueField.TEXT_SCALE));
		assertEquals(false, r.settings().showCues(), "Unchanged in the room passes on to the base");
		assertEquals(PlaceRef.zone("base"), r.source(CueField.SHOW_CUES));
	}

	@Test
	void unchangedEverywhereInTheChainPassesThroughToEverywhere() {
		Zone a = box("a", 4, PlaceCues.UNCHANGED);
		Zone b = box("b", 8, PlaceCues.UNCHANGED);
		CueResolution r = CueResolver.resolve(List.of(a, b), EVERYWHERE.withBoxOffset(new PlaceCues.Offset(-30, 12)));
		assertEquals(-30, r.settings().offsetX());
		assertEquals(12, r.settings().offsetY());
		assertEquals(PlaceRef.EVERYWHERE, r.source(CueField.BOX_OFFSET));
	}

	@Test
	void theBoxPositionIsOneSettingWithBothParts() {
		Zone moved = box("m", 4, PlaceCues.UNCHANGED.withBoxOffset(new PlaceCues.Offset(5, 0)));
		CueResolution r = CueResolver.resolve(List.of(moved), EVERYWHERE.withBoxOffset(new PlaceCues.Offset(100, 100)));
		assertEquals(5, r.settings().offsetX());
		assertEquals(0, r.settings().offsetY(), "the zone's y, not Everywhere's: the two parts go together");
	}

	@Test
	void everywhereMustSetEverything() {
		assertThrows(IllegalArgumentException.class, () -> CueResolver.resolve(List.of(), PlaceCues.UNCHANGED.withShowCues(true)));
	}

	@Test
	void whereYouAreGivesTheOrder() {
		Zone room = box("room", 4, PlaceCues.UNCHANGED.withShowCues(true));
		Zone base = box("base", 40, PlaceCues.UNCHANGED.withShowCues(false));
		// Listed base first: the order comes from the sizes, not the list.
		Precedence p = new Precedence(PlaceSets.EMPTY, EVERYWHERE, List.of(base, room), SetLibrary.EMPTY);
		WhereYouAre inRoom = Fixtures.at(p, WORLD, OVERWORLD, 0, 0, 0);
		assertEquals(true, CueResolver.resolve(p, inRoom).settings().showCues());
		WhereYouAre inBaseOnly = Fixtures.at(p, WORLD, OVERWORLD, 10, 0, 0);
		assertEquals(false, CueResolver.resolve(p, inBaseOnly).settings().showCues());
		WhereYouAre outside = Fixtures.at(p, WORLD, OVERWORLD, 100, 0, 0);
		assertEquals(PlaceRef.EVERYWHERE, CueResolver.resolve(p, outside).source(CueField.SHOW_CUES));
		assertThrows(IllegalArgumentException.class, () -> CueResolver.resolve(
				new Precedence(PlaceSets.EMPTY, List.of(), SetLibrary.EMPTY), inRoom), "where you are must be for this snapshot");
	}

	@Test
	void aZoneInheritsFromTheBiggerZonesAroundItsCenterThenEverywhere() {
		Zone room = box("room", 4, PlaceCues.UNCHANGED);
		Zone base = box("base", 40, PlaceCues.UNCHANGED.withColor(0xE69F00));
		Zone far = new Zone("far", "Far", true, WORLD, OVERWORLD, new Point(500, 0, 0), new Shape.Box(100, 100, 100),
				PlaceSets.EMPTY, PlaceCues.UNCHANGED.withShowCues(false));
		Profile profile = Profile.empty("p").withZones(List.of(room, base, far)).withEverywhereCues(EVERYWHERE.withTextScale(70));
		CueResolution forRoom = CueResolver.inheritedBy(profile, room);
		assertEquals(0xE69F00, forRoom.settings().color());
		assertEquals(PlaceRef.zone("base"), forRoom.source(CueField.COLOR));
		assertEquals(true, forRoom.settings().showCues(), "a zone elsewhere takes no part");
		assertEquals(70, forRoom.settings().textScale());
		// The bigger zone does not inherit from the smaller one inside it.
		CueResolution forBase = CueResolver.inheritedBy(profile, base);
		assertEquals(PlaceRef.EVERYWHERE, forBase.source(CueField.COLOR));
		// A new zone, not saved yet, comes last in the list: an equal-sized saved zone around it comes first.
		Zone twin = box("new", 40, PlaceCues.UNCHANGED);
		assertEquals(PlaceRef.EVERYWHERE, CueResolver.inheritedBy(profile, twin).source(CueField.COLOR),
				"a zone of equal size higher in the list beats the new one, so the new one does not inherit from it");
		Zone small = box("new", 2, PlaceCues.UNCHANGED);
		assertEquals(PlaceRef.zone("base"), CueResolver.inheritedBy(profile, small).source(CueField.COLOR));
		// A zone that is off passes nothing on.
		Profile baseOff = profile.putZone(base.withEnabled(false));
		assertTrue(CueResolver.inheritedBy(baseOff, room).source(CueField.COLOR).isEverywhere());
	}
}
