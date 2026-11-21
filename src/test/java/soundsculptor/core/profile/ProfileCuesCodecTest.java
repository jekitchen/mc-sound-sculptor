package soundsculptor.core.profile;

import static soundsculptor.core.Fixtures.quick;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

/**
 * PRD 7.5 and FR-7 (decided 2026-11-21): each place's cue settings in the profile file, the migration of files saved
 * before they moved from the app-wide settings, forgiving reads, and Copy from, export and import carrying them.
 */
class ProfileCuesCodecTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String SERVER = "play.example.net";
	private static final PlaceCues EVERYWHERE = new PlaceCues(false, true, false, 0xF0E442, 150, new PlaceCues.Offset(-20, 30));

	private static Zone zone(String id, PlaceCues cues) {
		return new Zone(id, "Zone " + id, true, SERVER, "minecraft:overworld", new Point(0.5, 64, 0.5), new Shape.Box(10, 10, 10),
				PlaceSets.EMPTY, cues);
	}

	private static Profile profile() {
		return Profile.empty("A").withEverywhereCues(EVERYWHERE)
				.withZones(List.of(zone("z1", PlaceCues.UNCHANGED.withShowCues(true).withTextScale(50)), zone("z2", PlaceCues.UNCHANGED)));
	}

	private static ProfileCodec.Read read(String json) throws ProfileFormatException {
		return ProfileCodec.read(json, version -> { });
	}

	private static List<String> warnings(ProfileCodec.Read read) {
		return read.warnings().stream().map(m -> m.text(EN)).toList();
	}

	@Test
	void everyPlacesCueSettingsRoundTrip() throws ProfileFormatException {
		Profile p = profile();
		ProfileCodec.Read read = read(ProfileCodec.toJson(p));
		assertEquals(p, read.profile());
		assertTrue(read.everywhereCuesSaved());
		assertEquals(List.of(), read.warnings());
	}

	@Test
	void everywhereWritesEverySettingAndAZoneOnlyWhatItSets() {
		JsonObject root = JsonParser.parseString(ProfileCodec.toJson(profile())).getAsJsonObject();
		JsonObject everywhere = root.getAsJsonObject("everywhere").getAsJsonObject("cues");
		assertEquals(false, everywhere.get("showCues").getAsBoolean());
		assertEquals(true, everywhere.get("cuesWithoutSubtitles").getAsBoolean());
		assertEquals(false, everywhere.get("newMutesGetCue").getAsBoolean());
		assertEquals("#F0E442", everywhere.get("color").getAsString());
		assertEquals(150, everywhere.get("textScale").getAsInt());
		assertEquals(-20, everywhere.getAsJsonObject("boxOffset").get("x").getAsInt());
		assertEquals(30, everywhere.getAsJsonObject("boxOffset").get("y").getAsInt());
		JsonObject z1 = root.getAsJsonArray("zones").get(0).getAsJsonObject().getAsJsonObject("cues");
		assertEquals(2, z1.size(), "Unchanged settings are left out: " + z1);
		assertEquals(50, z1.get("textScale").getAsInt());
		assertFalse(root.getAsJsonArray("zones").get(1).getAsJsonObject().has("cues"), "a zone with every setting Unchanged has none");
	}

	@Test
	void aFileSavedBeforeHasDefaultsWithItsOwnNewMutesSetting() throws ProfileFormatException {
		ProfileCodec.Read read = read("{\"schemaVersion\": 3, \"settings\": {\"cueDefault\": false, \"showDistance\": true}, "
				+ "\"everywhere\": {\"sets\": []}}");
		assertFalse(read.everywhereCuesSaved(), "the caller fills in the old app-wide settings");
		assertEquals(PlaceCues.EVERYWHERE_DEFAULTS.withNewMutesGetCue(false), read.profile().everywhereCues());
		assertEquals(List.of(), read.warnings(), "the old cueDefault is accepted without a warning");
		assertFalse(ProfileCodec.toJson(read.profile()).contains("cueDefault"), "and not written again");
		// Once saved, Everywhere has its own settings, and the old value is not read again.
		ProfileCodec.Read again = read(ProfileCodec.toJson(read.profile()));
		assertTrue(again.everywhereCuesSaved());
		assertEquals(read.profile(), again.profile());
	}

	@Test
	void theOldAppWideSettingsSeedEverywhereButNotNewMutes() throws ProfileFormatException {
		Profile old = read("{\"schemaVersion\": 3, \"settings\": {\"cueDefault\": false}}").profile();
		PlaceCues appWide = new PlaceCues(false, true, null, 0x56B4E9, 200, new PlaceCues.Offset(5, 6));
		Profile seeded = old.withEverywhereCues(old.everywhereCues().seededFrom(appWide));
		assertEquals(new PlaceCues(false, true, false, 0x56B4E9, 200, new PlaceCues.Offset(5, 6)), seeded.everywhereCues());
	}

	@Test
	void unusableCueValuesAreIgnoredWithAWarningAndTheProfileStillLoads() throws ProfileFormatException {
		ProfileCodec.Read read = read("""
				{"schemaVersion": 3,
				 "everywhere": {"sets": [], "cues": {"showCues": "no", "color": "blue", "textScale": 500, "sparkle": 1,
				                                     "boxOffset": {"x": 3}}},
				 "zones": [{"id": "z1", "anchor": {"type": "fixed", "x": 0, "y": 0, "z": 0, "dimension": "d", "worldKey": "w"},
				            "shape": {"type": "box", "width": 4, "height": 4, "depth": 4},
				            "cues": {"textScale": 40, "cuesWithoutSubtitles": true}}]}""");
		assertEquals(PlaceCues.EVERYWHERE_DEFAULTS, read.profile().everywhereCues(), "Everywhere falls back to its defaults");
		assertEquals(PlaceCues.UNCHANGED.withWithoutSubtitles(true), read.profile().zone("z1").cues(), "a zone's bad value is Unchanged");
		assertEquals(List.of(
				"Everywhere, cue settings: unknown field \"sparkle\" was ignored.",
				"Everywhere, cue settings, field showCues: \"no\" is not true or false, so it was ignored.",
				"Everywhere, cue settings, field color: \"blue\" is not a color like #E69F00, so it was ignored.",
				"Everywhere, cue settings, field textScale: 500 is above the maximum 200, so it was ignored.",
				"Everywhere, cue settings, field boxOffset: it needs both x and y, so it was ignored.",
				"Zone 1, cue settings, field textScale: 40 is below the minimum 50, so it was ignored."), warnings(read));
	}

	@Test
	void cuesThatAreNotAnObjectAreIgnored() throws ProfileFormatException {
		ProfileCodec.Read read = read("{\"schemaVersion\": 3, \"everywhere\": {\"sets\": [], \"cues\": 7}}");
		assertEquals(PlaceCues.EVERYWHERE_DEFAULTS, read.profile().everywhereCues());
		assertEquals(List.of("Everywhere, field cues: 7 is not an object, so it was ignored."), warnings(read));
	}

	@Test
	void placesChangeTheirOwnCues() {
		Profile p = profile();
		assertEquals(PlaceCues.UNCHANGED.withShowCues(true).withTextScale(50), p.cues(PlaceRef.zone("z1")));
		Profile changed = p.withCues(PlaceRef.zone("z2"), PlaceCues.UNCHANGED.withColor(0xE69F00));
		assertEquals(0xE69F00, changed.zone("z2").cues().color());
		// Everywhere has no Unchanged: what is left Unchanged keeps its value.
		Profile everywhere = p.withCues(PlaceRef.EVERYWHERE, PlaceCues.UNCHANGED.withShowCues(true));
		assertEquals(EVERYWHERE.withShowCues(true), everywhere.everywhereCues());
		assertEquals(null, p.cues(PlaceRef.zone("missing")));
		assertEquals(p, p.withCues(PlaceRef.zone("missing"), PlaceCues.UNCHANGED));
	}

	@Test
	void copyFromCarriesEverywheresCueSettingsButNotTheZones() {
		Profile source = profile();
		Profile target = Profile.empty("B").withZones(List.of(zone("t1", PlaceCues.UNCHANGED.withShowCues(false))));
		Profile copied = ProfileOps.copyFrom(source, target, SetLibrary.EMPTY).result();
		assertEquals(EVERYWHERE, copied.everywhereCues());
		assertEquals(PlaceCues.UNCHANGED.withShowCues(false), copied.zone("t1").cues(), "this server's zones keep theirs");
	}

	@Test
	void exportAndImportCarryTheCueSettings() throws ProfileFormatException {
		Profile source = profile().withEverywhere(quick(SetEntry.of("q1", "x:a").withMute(true)));
		ExportCodec.Read file = ExportCodec.read(ExportCodec.toJson(source, SERVER, SetLibrary.EMPTY));
		assertTrue(file.everywhereCuesSaved());
		ImportPlan plan = ImportPlan.of(Profile.empty("Here"), file, SetLibrary.EMPTY, SERVER, Map.of());
		assertEquals(EVERYWHERE, plan.result().everywhereCues());
		assertEquals(source.zone("z1").cues(), plan.result().zone("z1").cues());
	}

	@Test
	void anOldExportKeepsThisProfilesEverywhereCues() throws ProfileFormatException {
		ExportCodec.Read old = ExportCodec.read("{\"schemaVersion\": 3, \"name\": \"Old\", \"everywhere\": {\"sets\": []}}");
		assertFalse(old.everywhereCuesSaved());
		Profile here = Profile.empty("Here").withEverywhereCues(EVERYWHERE);
		assertEquals(EVERYWHERE, ImportPlan.of(here, old, SetLibrary.EMPTY, SERVER, Map.of()).result().everywhereCues());
	}
}
