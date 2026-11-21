package soundsculptor.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.cue.PlaceCues;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The app-wide settings file: human-readable JSON that never loses good values because of one bad one. Files from
 * before 2026-11-21 hold the app-wide cue settings; they still read without a problem, as {@link AppSettings#legacyCues}.
 */
class AppSettingsCodecTest {
	private static final AppSettings D = AppSettings.DEFAULTS;
	/** What the app-wide cue settings defaulted to. */
	private static final PlaceCues OLD_DEFAULTS = PlaceCues.EVERYWHERE_DEFAULTS.withNewMutesGetCue(null);

	private static AppSettingsCodec.Read read(String json) throws AppSettingsFormatException {
		return AppSettingsCodec.read(json);
	}

	private static String only(AppSettingsCodec.Read read) {
		assertEquals(1, read.problems().size(), read.problems().toString());
		return read.problems().get(0);
	}

	@Test
	void defaultsAreWrittenAsReadableJson() {
		assertEquals("""
				{
				  "schemaVersion": 1,
				  "outlinesThroughBlocks": true,
				  "reducedMotion": false,
				  "lastTab": "sounds"
				}
				""", AppSettingsCodec.toJson(D));
	}

	@Test
	void roundTrip() throws AppSettingsFormatException {
		AppSettings s = D.withOutlinesThroughBlocks(false).withReducedMotion(true).withLastTab("zones");
		AppSettingsCodec.Read read = read(AppSettingsCodec.toJson(s));
		assertEquals(s, read.settings());
		assertEquals(List.of(), read.problems());
	}

	@Test
	void anOldFilesCueSettingsAreReadWithoutAProblem() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("""
				{"schemaVersion": 1, "showCues": false, "cuesWithoutSubtitles": true, "cueColor": "#F0E442",
				 "cueTextScale": 150, "cueOffsetX": -20, "cueOffsetY": 30, "reducedMotion": true}""");
		assertEquals(List.of(), read.problems());
		assertTrue(read.settings().reducedMotion());
		assertEquals(new PlaceCues(false, true, null, 0xF0E442, 150, new PlaceCues.Offset(-20, 30)), read.settings().legacyCues());
	}

	@Test
	void anOldFileWithSomeCueSettingsGetsTheOldDefaultsForTheRest() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"cueTextScale\": 180}");
		assertEquals(OLD_DEFAULTS.withTextScale(180), read.settings().legacyCues());
		assertEquals(List.of(), read.problems());
	}

	@Test
	void theOldFieldsAreNotWrittenAgainButTheirValuesAreKeptForProfilesNotLoadedYet() throws AppSettingsFormatException {
		AppSettings old = read("{\"schemaVersion\": 1, \"showCues\": false, \"cueColor\": \"#123456\"}").settings();
		String json = AppSettingsCodec.toJson(old);
		assertFalse(json.contains("\n  \"showCues\""), "no old top-level field: " + json);
		assertTrue(json.contains("\"legacyCues\""), json);
		AppSettingsCodec.Read again = read(json);
		assertEquals(List.of(), again.problems());
		assertEquals(old, again.settings());
		assertEquals(0x123456, again.settings().legacyCues().color(), "a custom color from an edited file is kept");
	}

	@Test
	void aNewFileHasNoOldCueSettings() throws AppSettingsFormatException {
		assertNull(read("{\"schemaVersion\": 1, \"reducedMotion\": true}").settings().legacyCues());
		assertFalse(AppSettingsCodec.toJson(D).contains("legacyCues"));
	}

	@Test
	void reducedMotionIsSavedAndReadBack() throws AppSettingsFormatException {
		// A-6, Settings › Accessibility: off by default; on is written to the file and read back on its own.
		assertTrue(AppSettingsCodec.toJson(D).contains("\"reducedMotion\": false"));
		String json = AppSettingsCodec.toJson(D.withReducedMotion(true));
		assertTrue(json.contains("\"reducedMotion\": true"), json);
		AppSettingsCodec.Read read = read(json);
		assertEquals(D.withReducedMotion(true), read.settings());
		assertEquals(List.of(), read.problems());
		assertEquals(D, read.settings().reset(AppSetting.REDUCED_MOTION));
	}

	@Test
	void missingValuesUseDefaultsQuietly() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"reducedMotion\": true}");
		assertEquals(D.withReducedMotion(true), read.settings());
		assertEquals(List.of(), read.problems());
	}

	@Test
	void unknownFieldsAreIgnoredWithAProblem() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"reducedMotion\": true, \"sparkles\": 3}");
		assertEquals(D.withReducedMotion(true), read.settings());
		assertEquals("Unknown setting \"sparkles\" was ignored.", only(read));
	}

	@Test
	void aWrongOldCueValueUsesItsOldDefault() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"showCues\": \"no\", \"cueTextScale\": 150}");
		assertEquals(OLD_DEFAULTS.withTextScale(150), read.settings().legacyCues());
		assertEquals("showCues: \"no\" is not true or false, so the default is used.", only(read));
		assertEquals("cueTextScale: 1.5 is not a whole number, so the default is used.",
				only(read("{\"schemaVersion\": 1, \"cueTextScale\": 1.5}")));
		assertEquals("cueOffsetX: \"left\" is not a whole number, so the default is used.",
				only(read("{\"schemaVersion\": 1, \"cueOffsetX\": \"left\"}")));
		assertEquals("cueColor: \"#GG0000\" is not a color like #E69F00, so the default is used.",
				only(read("{\"schemaVersion\": 1, \"cueColor\": \"#GG0000\"}")));
	}

	@Test
	void oldCueNumbersOutOfRangeAreClampedWithAProblem() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"cueTextScale\": 300}");
		assertEquals(200, read.settings().legacyCues().textScale());
		assertEquals("cueTextScale: 300 is above the maximum 200, so 200 is used.", only(read));
		read = read("{\"schemaVersion\": 1, \"cueOffsetY\": -99999999999999}");
		assertEquals(-PlaceCues.MAX_OFFSET, read.settings().legacyCues().boxOffset().y());
		assertEquals(1, read.problems().size());
	}

	@Test
	void aLegacyCuesValueThatIsNotAnObjectIsIgnored() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"legacyCues\": 3}");
		assertNull(read.settings().legacyCues());
		assertEquals("legacyCues: 3 is not an object, so it was ignored.", only(read));
	}

	@Test
	void anUnknownLastTabFallsBackToTheFirstTab() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"lastTab\": \"../../etc\"}");
		assertEquals(D, read.settings());
		assertEquals("lastTab: \"../../etc\" is not a tab name, so the default is used.", only(read));
	}

	@Test
	void theRetiredRecentAndSearchTabsOpenTheSoundsTab() throws AppSettingsFormatException {
		// FR-12.1: Sounds replaced the Recently Played and Search tabs; a file saved before still opens a tab.
		for (String old : List.of("recent", "search")) {
			AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"lastTab\": \"" + old + "\"}");
			assertEquals("sounds", read.settings().lastTab());
			assertEquals(List.of(), read.problems());
		}
	}

	@Test
	void aNewerFileIsReadAsFarAsPossible() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 2, \"reducedMotion\": true}");
		assertEquals(D.withReducedMotion(true), read.settings());
		assertEquals("The file is from a newer Sound Sculptor (schema 2); settings this version does not know are ignored.",
				only(read));
	}

	@Test
	void aMissingOrBadSchemaVersionIsReported() throws AppSettingsFormatException {
		assertEquals("schemaVersion is missing; the file is read as schema 1.", only(read("{\"reducedMotion\": true}")));
		assertEquals("schemaVersion: \"one\" is not a whole number; the file is read as schema 1.",
				only(read("{\"schemaVersion\": \"one\"}")));
		assertEquals("schemaVersion: 0 is not a schema version; the file is read as schema 1.",
				only(read("{\"schemaVersion\": 0}")));
	}

	@Test
	void nullValuesUseTheDefault() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"schemaVersion\": 1, \"reducedMotion\": null}");
		assertEquals(D, read.settings());
		assertEquals("reducedMotion: null is not true or false, so the default is used.", only(read));
	}

	@Test
	void everyProblemIsReportedAtOnce() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read(
				"{\"schemaVersion\": 1, \"reducedMotion\": 1, \"cueTextScale\": 0, \"cueColor\": \"x\", \"what\": 1}");
		assertEquals(D.withLegacyCues(OLD_DEFAULTS.withTextScale(PlaceCues.MIN_TEXT_SCALE)), read.settings());
		assertEquals(4, read.problems().size());
	}

	@Test
	void textThatIsNotASettingsObjectCannotBeRead() {
		for (String bad : new String[] {"", "   ", "not json {", "[1, 2]", "\"text\"", "42", "{\"a\": "}) {
			AppSettingsFormatException e = assertThrows(AppSettingsFormatException.class, () -> read(bad), bad);
			assertTrue(e.getMessage().endsWith("."), e.getMessage());
		}
	}

	@Test
	void problemsListIsReadOnly() throws AppSettingsFormatException {
		AppSettingsCodec.Read read = read("{\"x\": 1}");
		assertThrows(UnsupportedOperationException.class, () -> read.problems().add("y"));
	}
}
