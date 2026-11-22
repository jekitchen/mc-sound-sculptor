package soundsculptor.core.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One place's cue settings (PRD FR-7, A-7): Unchanged values, ranges and the migration from app-wide settings. */
class PlaceCuesTest {
	@Test
	void aNewZoneLeavesEverythingUnchangedAndEverywhereSetsEverything() {
		assertTrue(PlaceCues.UNCHANGED.isUnchanged());
		assertFalse(PlaceCues.UNCHANGED.isComplete());
		assertTrue(PlaceCues.EVERYWHERE_DEFAULTS.isComplete());
		for (CueField field : CueField.values()) {
			assertFalse(PlaceCues.UNCHANGED.has(field));
			assertTrue(PlaceCues.EVERYWHERE_DEFAULTS.has(field));
		}
	}

	@Test
	void textSizeGoesFromHalfToDouble() {
		assertEquals(50, PlaceCues.MIN_TEXT_SCALE, "decided 2026-11-21: down to 50%");
		assertEquals(200, PlaceCues.MAX_TEXT_SCALE);
		assertEquals(50, PlaceCues.UNCHANGED.withTextScale(10).textScale());
		assertEquals(50, PlaceCues.UNCHANGED.withTextScale(50).textScale());
		assertEquals(60, PlaceCues.UNCHANGED.withTextScale(60).textScale());
		assertEquals(200, PlaceCues.UNCHANGED.withTextScale(900).textScale());
		assertNull(PlaceCues.UNCHANGED.withTextScale(null).textScale(), "null stays Unchanged");
	}

	@Test
	void colorKeepsItsRgbAndTheOffsetStaysInRange() {
		assertEquals(0x123456, PlaceCues.UNCHANGED.withColor(0xFF123456).color());
		PlaceCues.Offset offset = new PlaceCues.Offset(5000, -5000);
		assertEquals(PlaceCues.MAX_OFFSET, offset.x());
		assertEquals(-PlaceCues.MAX_OFFSET, offset.y());
	}

	@Test
	void clearingAndFillingOneSetting() {
		PlaceCues zone = PlaceCues.UNCHANGED.withShowCues(false).withTextScale(80);
		assertFalse(zone.cleared(CueField.SHOW_CUES).has(CueField.SHOW_CUES));
		assertEquals(80, zone.cleared(CueField.SHOW_CUES).textScale());
		PlaceCues filled = zone.orElse(PlaceCues.EVERYWHERE_DEFAULTS);
		assertTrue(filled.isComplete());
		assertEquals(false, filled.showCues());
		assertEquals(80, filled.textScale());
		assertEquals(PlaceCues.DEFAULT_COLOR, filled.color());
	}

	@Test
	void seedingFromTheOldAppWideSettingsKeepsTheProfilesNewMutesSetting() {
		PlaceCues appWide = new PlaceCues(false, true, null, 0xF0E442, 150, new PlaceCues.Offset(-20, 10));
		PlaceCues everywhere = PlaceCues.EVERYWHERE_DEFAULTS.withNewMutesGetCue(false);
		PlaceCues seeded = everywhere.seededFrom(appWide);
		assertEquals(new PlaceCues(false, true, false, 0xF0E442, 150, new PlaceCues.Offset(-20, 10)), seeded);
		// Even an app-wide record that carries one does not replace the profile's own.
		assertEquals(false, everywhere.seededFrom(appWide.withNewMutesGetCue(true)).newMutesGetCue());
		// The old app-wide Show cues was on by default, so it is not seeded either (decided 2026-11-22).
		assertEquals(false, everywhere.seededFrom(appWide.withShowCues(true)).showCues());
		assertEquals(true, everywhere.withShowCues(true).seededFrom(appWide).showCues(), "and Everywhere's own is kept");
	}
}
