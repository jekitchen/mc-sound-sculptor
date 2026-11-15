package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import soundsculptor.core.outline.OutlineMode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import soundsculptor.core.TestEnglish;

import org.junit.jupiter.api.Test;

/** PRD 7.5 file format for the profile settings, last joined and the boost confirm; 7.6 the v1 → v2 migration. */
class ProfileSettingsCodecTest {
	private static final TestEnglish EN = new TestEnglish();

	private static final Instant JOINED = Instant.parse("2026-11-09T13:37:46.250Z");

	private static Profile read(String json) throws ProfileFormatException {
		return ProfileCodec.fromJson(json);
	}

	private static String problem(String json) {
		return assertThrows(ProfileFormatException.class, () -> read(json)).problem().text(EN);
	}

	private static JsonObject written(Profile p) {
		return JsonParser.parseString(ProfileCodec.toJson(p)).getAsJsonObject();
	}

	@Test
	void everySettingRoundTrips() throws ProfileFormatException {
		for (ProfileSettings s : List.of(ProfileSettings.DEFAULTS,
				new ProfileSettings(false, false, OutlineMode.ALWAYS),
				new ProfileSettings(true, false, OutlineMode.NEVER))) {
			Profile p = Profile.empty("A").withSettings(s);
			assertEquals(p, read(ProfileCodec.toJson(p)));
		}
	}

	@Test
	void settingsUseTheNamesFromTheFileSketch() {
		JsonObject s = written(Profile.empty("A").withSettings(
				new ProfileSettings(false, false, OutlineMode.ALWAYS))).getAsJsonObject("settings");
		assertFalse(s.get("showDistance").getAsBoolean());
		assertFalse(s.get("cueDefault").getAsBoolean());
		assertEquals("always", s.get("outlines").getAsString());
		assertFalse(s.has("edge"), "fade edges were removed on 2026-11-14");
	}

	@Test
	void anOldDefaultEdgeIsIgnoredWithoutAWarning() throws ProfileFormatException {
		ProfileCodec.Read read = ProfileCodec.read(
				"{\"schemaVersion\": 3, \"settings\": {\"edge\": {\"mode\": \"fade\", \"fade\": 8}}}", version -> { });
		assertEquals(ProfileSettings.DEFAULTS, read.profile().settings());
		assertEquals(List.of(), read.warnings());
	}

	@Test
	void missingSettingsUseTheDefaults() throws ProfileFormatException {
		assertEquals(ProfileSettings.DEFAULTS, read("{\"schemaVersion\": 3, \"settings\": {}}").settings());
		assertEquals(ProfileSettings.DEFAULTS.withOutlines(OutlineMode.NEVER),
				read("{\"schemaVersion\": 3, \"settings\": {\"outlines\": \"never\"}}").settings());
	}

	@Test
	void badSettingsNameTheFieldInPlainWords() {
		assertEquals("Settings, field outlines: \"sometimes\" is not one of: always, editing, never.",
				problem("{\"schemaVersion\": 3, \"settings\": {\"outlines\": \"sometimes\"}}"));
		assertEquals("Settings, field cueDefault: \"yes\" is not true or false.",
				problem("{\"schemaVersion\": 3, \"settings\": {\"cueDefault\": \"yes\"}}"));
	}

	@Test
	void lastJoinedAndBoostConfirmRoundTrip() throws ProfileFormatException {
		Profile p = Profile.empty("A").withLastJoined(JOINED).withBoostConfirmed(true);
		JsonObject json = written(p);
		assertEquals("2026-11-09T13:37:46.250Z", json.get("lastJoined").getAsString());
		assertTrue(json.get("boostConfirmed").getAsBoolean());
		assertEquals(p, read(ProfileCodec.toJson(p)));
	}

	@Test
	void neverJoinedIsOmittedAndReadsAsNull() throws ProfileFormatException {
		JsonObject json = written(Profile.empty("A"));
		assertFalse(json.has("lastJoined"));
		assertFalse(json.get("boostConfirmed").getAsBoolean());
		Profile p = read("{\"schemaVersion\": 3}");
		assertNull(p.lastJoined());
		assertFalse(p.boostConfirmed());
	}

	@Test
	void aBadDateIsRefused() {
		assertEquals("Field lastJoined: \"yesterday\" is not a date and time like 2026-11-09T13:37:46Z.",
				problem("{\"schemaVersion\": 3, \"lastJoined\": \"yesterday\"}"));
		assertEquals("Field boostConfirmed: 1 is not true or false.", problem("{\"schemaVersion\": 3, \"boostConfirmed\": 1}"));
	}

	@Test
	void filesAreWrittenAtSchemaVersion3() {
		assertEquals(3, Profile.SCHEMA_VERSION);
		assertEquals(3, written(Profile.empty("A")).get("schemaVersion").getAsInt());
		assertEquals(Profile.SCHEMA_VERSION, ProfileMigrations.CURRENT.target());
	}
}
