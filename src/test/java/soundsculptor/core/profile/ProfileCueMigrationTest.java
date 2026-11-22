package soundsculptor.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.settings.AppSettingsCodec;
import soundsculptor.core.settings.AppSettingsFormatException;

/**
 * The migration of the app-wide cue settings (decided 2026-11-21) through every way a stored profile is read: joining
 * ({@link ProfileStore#load(String, String, PlaceCues)}), the Profiles tab ({@link ProfileStore#readStored}) and what it
 * does with the result (Rename, Copy from, Export), so a profile not joined since the update never loses the player's
 * old color, size and position. Reset gives plain defaults.
 */
class ProfileCueMigrationTest {
	/** What settings.json held before: yellow, 150%, moved left, cues off, sound IDs on. */
	private static final PlaceCues APP_WIDE = new PlaceCues(false, true, null, 0xF0E442, 150, new PlaceCues.Offset(-20, 0));
	/** A profile file from before 2026-11-21: no Everywhere cues, and "New mutes get Cue" off in its settings. */
	private static final String OLD_FILE = """
			{"schemaVersion": 3, "name": "World B", "worldKey": "world:b",
			 "settings": {"showDistance": true, "cueDefault": false, "outlines": "editing"},
			 "everywhere": {"sets": []}, "zones": []}""";
	private static final PlaceCues MIGRATED = new PlaceCues(false, true, false, 0xF0E442, 150, new PlaceCues.Offset(-20, 0));

	@TempDir
	Path dir;

	private ProfileStore store() {
		return new ProfileStore(dir);
	}

	private void writeOld(String key) throws IOException {
		Files.writeString(store().fileFor(key), OLD_FILE, StandardCharsets.UTF_8);
	}

	@Test
	void joiningAnOldProfileGivesEverywhereTheOldAppWideSettings() throws IOException, ProfileFormatException {
		writeOld("world:b");
		ProfileStore.LoadResult result = store().load("world:b", "B", APP_WIDE);
		assertFalse(result.everywhereCuesSaved());
		assertEquals(MIGRATED, result.profile().everywhereCues());
		// Without old app-wide settings (a new install) only the profile's own cueDefault moves.
		assertEquals(PlaceCues.EVERYWHERE_DEFAULTS.withNewMutesGetCue(false), store().load("world:b", "B", null).profile().everywhereCues());
	}

	@Test
	void aProfileWithItsOwnCueSettingsKeepsThem() throws IOException, ProfileFormatException {
		PlaceCues own = PlaceCues.EVERYWHERE_DEFAULTS.withTextScale(70);
		store().save("world:a", Profile.empty("A").withEverywhereCues(own));
		ProfileStore.LoadResult result = store().load("world:a", "A", APP_WIDE);
		assertTrue(result.everywhereCuesSaved());
		assertEquals(own, result.profile().everywhereCues());
		assertEquals(own, store().readStored(store().fileFor("world:a"), APP_WIDE).everywhereCues());
	}

	@Test
	void aNewOrBrokenProfileStartsFromTheOldAppWideSettingsToo() throws IOException, ProfileFormatException {
		assertEquals(PlaceCues.EVERYWHERE_DEFAULTS.seededFrom(APP_WIDE), store().load("world:new", "New", APP_WIDE).profile().everywhereCues());
		Files.writeString(store().fileFor("world:broken"), "not json {", StandardCharsets.UTF_8);
		ProfileStore.LoadResult broken = store().load("world:broken", "Broken", APP_WIDE);
		assertTrue(broken.failed());
		assertEquals(PlaceCues.EVERYWHERE_DEFAULTS.seededFrom(APP_WIDE), broken.profile().everywhereCues());
	}

	@Test
	void theProfilesTabReadsTheSameMigratedProfileWithoutRewritingTheFile() throws IOException, ProfileFormatException {
		writeOld("world:b");
		Profile shown = store().readStored(store().fileFor("world:b"), APP_WIDE);
		assertEquals(MIGRATED, shown.everywhereCues());
		assertEquals(OLD_FILE, Files.readString(store().fileFor("world:b")), "reading never changes the file");
	}

	@Test
	void renamingFromTheProfilesTabKeepsTheOldLook() throws IOException, ProfileFormatException {
		writeOld("world:b");
		Profile renamed = ProfileOps.rename(store().readStored(store().fileFor("world:b"), APP_WIDE), "Base world");
		store().save("world:b", renamed);
		// Saved with its own cue settings now: later reads, even with other app-wide values, keep them.
		ProfileStore.LoadResult again = store().load("world:b", "B", PlaceCues.EVERYWHERE_DEFAULTS.withNewMutesGetCue(null));
		assertTrue(again.everywhereCuesSaved());
		assertEquals("Base world", again.profile().name());
		assertEquals(MIGRATED, again.profile().everywhereCues());
	}

	@Test
	void copyFromAndExportCarryTheMigratedLook() throws IOException, ProfileFormatException {
		writeOld("world:b");
		Profile b = store().readStored(store().fileFor("world:b"), APP_WIDE);
		Profile a = Profile.empty("A").withEverywhereCues(PlaceCues.EVERYWHERE_DEFAULTS.withTextScale(80));
		assertEquals(MIGRATED, ProfileOps.copyFrom(b, a, SetLibrary.EMPTY).result().everywhereCues());
		ExportCodec.Read exported = ExportCodec.read(ExportCodec.toJson(b, "world:b", SetLibrary.EMPTY));
		assertTrue(exported.everywhereCuesSaved());
		assertEquals(MIGRATED, exported.profile().everywhereCues());
	}

	@Test
	void aNewProfilesEverywhereHasShowCuesOff() {
		// Owner decision 2026-11-22: cues show only where a zone, or the player in Everywhere, turns them on.
		assertEquals(false, PlaceCues.EVERYWHERE_DEFAULTS.showCues());
		assertEquals(false, Profile.empty("New").everywhereCues().showCues());
		assertEquals(false, Profile.empty("New", null).everywhereCues().showCues());
	}

	@Test
	void theOldAppWideShowCuesOnDoesNotSeedEverywhere() throws IOException, ProfileFormatException, AppSettingsFormatException {
		// settings.json from before 2026-11-21, with the old default "showCues": true and a custom look. True was the old
		// default, not the player's choice, so it is not seeded (2026-11-22); the look still is.
		PlaceCues appWide = AppSettingsCodec.read("""
				{"schemaVersion": 1, "showCues": true, "cuesWithoutSubtitles": true, "cueColor": "#F0E442",
				 "cueTextScale": 150, "cueOffsetX": -20, "cueOffsetY": 0}""").settings().legacyCues();
		assertEquals(true, appWide.showCues(), "the record keeps what the file said");
		PlaceCues seeded = new PlaceCues(false, true, false, 0xF0E442, 150, new PlaceCues.Offset(-20, 0));
		writeOld("world:b");
		assertEquals(seeded, store().load("world:b", "B", appWide).profile().everywhereCues(),
				"an old profile: Show cues off, its own cueDefault, the old look");
		assertEquals(seeded, store().readStored(store().fileFor("world:b"), appWide).everywhereCues());
		assertEquals(seeded.withNewMutesGetCue(true), store().load("world:new", "New", appWide).profile().everywhereCues(),
				"a new profile");
		Files.writeString(store().fileFor("world:broken"), "not json {", StandardCharsets.UTF_8);
		assertEquals(false, store().load("world:broken", "Broken", appWide).profile().everywhereCues().showCues(),
				"a broken one");
	}

	@Test
	void aProfileThatSavedShowCuesOnKeepsIt() throws IOException, ProfileFormatException {
		// Written by a version where Everywhere's Show cues was on by default: what it holds is kept, not rewritten.
		store().save("world:a", Profile.empty("A").withEverywhereCues(PlaceCues.EVERYWHERE_DEFAULTS.withShowCues(true)));
		assertEquals(true, store().load("world:a", "A", APP_WIDE).profile().everywhereCues().showCues());
		assertEquals(true, store().load("world:a", "A", null).profile().everywhereCues().showCues());
	}

	@Test
	void resetGivesPlainDefaults() throws IOException, ProfileFormatException {
		writeOld("world:b");
		Profile b = store().readStored(store().fileFor("world:b"), APP_WIDE);
		assertEquals(PlaceCues.EVERYWHERE_DEFAULTS, ProfileOps.reset(b).everywhereCues());
		Profile on = b.withEverywhereCues(b.everywhereCues().withShowCues(true));
		assertEquals(false, ProfileOps.reset(on).everywhereCues().showCues(), "Reset turns Show cues off again");
	}

	@Test
	void importingAnExportFromBeforeTakesItsNewMutesSettingAndKeepsTheRest() throws ProfileFormatException {
		ExportCodec.Read old = ExportCodec.read(OLD_FILE);
		assertFalse(old.everywhereCuesSaved());
		PlaceCues here = PlaceCues.EVERYWHERE_DEFAULTS.withColor(0x56B4E9).withTextScale(120);
		Profile current = Profile.empty("Here").withEverywhereCues(here);
		Profile result = ImportPlan.of(current, old, SetLibrary.EMPTY, "world:b", Map.of()).result();
		assertEquals(here.withNewMutesGetCue(false), result.everywhereCues(),
				"the file's old cueDefault (off) becomes New mutes get Cue; color and size stay this profile's");
		assertEquals(List.of(), old.warnings());
	}
}
