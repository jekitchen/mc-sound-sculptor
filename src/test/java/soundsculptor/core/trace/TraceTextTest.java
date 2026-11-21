package soundsculptor.core.trace;

import static soundsculptor.core.Fixtures.precedence;
import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;

import soundsculptor.core.TestEnglish;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/** PRD FR-14.5: "Copy trace" gives the panel as plain text, in the FR-14.2 order. */
class TraceTextTest {
	private static final TestEnglish EN = new TestEnglish();
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String AMBIENT = "minecraft:entity.villager.ambient";
	private static final String COW = "minecraft:entity.cow.ambient";

	private static LastPlay playAt(int x, float volume, boolean dropped, boolean cueShown) {
		return new LastPlay(true, WORLD, OVERWORLD, x, 0, 0, volume, dropped, cueShown);
	}

	private static Zone cube(String id, double x, double half, SetEntry... rules) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Box(2 * half, 2 * half, 2 * half), quick(List.of(rules)));
	}

	private static PlaceSets attached(String... setIds) {
		PlaceSets place = PlaceSets.EMPTY;
		for (String id : setIds) {
			place = place.attach(id);
		}
		return place;
	}

	private static String text(Precedence p, LastPlay play, boolean bypass) {
		return TraceText.of(SoundTracer.trace(p, SetLibrary.EMPTY, AMBIENT, play, Standing.NOWHERE, bypass), EN);
	}

	private static String text(SetLibrary library, PlaceSets everywhere, String soundId, ToIntFunction<String> placesUsing) {
		Precedence p = new Precedence(everywhere, List.of(), library);
		return TraceText.of(SoundTracer.trace(p, library, placesUsing, soundId, playAt(0, 100, false, false), Standing.NOWHERE, false), EN);
	}

	@Test
	void everywhereShowsEachSetTheWinnerItsReasonAndWhoWasOverridden() {
		SetEntry wildcard = SetEntry.of("w", "*villager*").withMute(true);
		SetEntry exact = SetEntry.of("e", AMBIENT).withVolume(60);
		SetLibrary library = new SetLibrary(List.of(new SoundSet("villagers", "Villagers", List.of(wildcard, exact))));
		String text = TraceText.of(SoundTracer.trace(new Precedence(attached("villagers"), List.of(), library), library, AMBIENT,
				playAt(0, 60, false, false), Standing.NOWHERE, false), EN);
		assertEquals(String.join("\n",
				"Sound Sculptor trace for minecraft:entity.villager.ambient",
				"Everywhere",
				"  Villagers",
				"    minecraft:entity.villager.ambient: Level 60%",
				"      Decides loudness (Exact sound ID)",
				"    *:*villager*: Muted",
				"      loudness overridden by Villagers: minecraft:entity.villager.ambient",
				"You were in: Everywhere",
				"Result",
				"  Volume 60%",
				"  No repeat limit",
				"  Last play was allowed",
				"  Cue off",
				"  No cue was shown",
				""), text);
	}

	@Test
	void higherSetSaysHigherInTheList() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40))),
				new SoundSet("quiet", "Quiet", List.of(SetEntry.of("e1", COW).withVolume(10)))));
		String text = text(library, attached("farm", "quiet"), COW, id -> 1);
		assertTrue(text.contains(String.join("\n",
				"Everywhere",
				"  Farm animals",
				"    *:*cow*: Level 40%",
				"      Decides loudness (Higher in the list)",
				"  Quiet",
				"    minecraft:entity.cow.ambient: Level 10%",
				"      loudness overridden by Farm animals: *:*cow*",
				"You were in: Everywhere")), text);
	}

	@Test
	void setUsedInMoreThanOnePlaceSaysSo() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40)))));
		assertTrue(text(library, attached("farm"), COW, id -> 3).contains("\n  Farm animals · used in 3 places\n"));
		assertTrue(text(library, attached("farm"), COW, id -> 1).contains("\n  Farm animals\n"));
	}

	@Test
	void quickChangesIsNamedAndListedFirst() {
		SetLibrary library = new SetLibrary(List.of(new SoundSet("farm", "Farm animals", List.of(SetEntry.of("e1", "*cow*").withVolume(40)))));
		PlaceSets everywhere = attached("farm").withQuickEntries(List.of(SetEntry.of("e1", COW).withMute(true)));
		String text = text(library, everywhere, COW, id -> 1);
		assertTrue(text.contains(String.join("\n",
				"Everywhere",
				"  Quick changes",
				"    minecraft:entity.cow.ambient: Muted",
				"      Decides loudness (Higher in the list)",
				"  Farm animals",
				"    *:*cow*: Level 40%",
				"      loudness overridden by Quick changes: minecraft:entity.cow.ambient",
				"You were in: Everywhere")), text);
	}

	@Test
	void zonesYouStoodInShowSmallestFirstWithTheirShapeSetsAndTheWinners() {
		Zone pen = cube("Pen", 3, 10, SetEntry.of("rn", "*villager*").withVolume(20));
		Zone base = cube("Base", 9, 20, SetEntry.of("rf", "*villager*").withVolume(80));
		String text = text(precedence(List.of(), List.of(base, pen)), playAt(0, 20, false, false), false);
		assertTrue(text.contains(String.join("\n",
				"You were in: Pen → Base → Everywhere",
				"  Pen (Box 20 × 20 × 20)",
				"    Quick changes",
				"      *:*villager*: Level 20%",
				"        Decides loudness (Only matching entry)",
				"  Base (Box 40 × 40 × 40)",
				"    Quick changes",
				"      *:*villager*: Level 80%",
				"        Decides loudness (Only matching entry)",
				"Deciding zones",
				"  loudness: Pen (Smallest zone that sets it)",
				"Result",
				"  Volume 20%")), text);
		assertTrue(text.startsWith("Sound Sculptor trace for " + AMBIENT + "\nEverywhere\n  No entry matches this sound\n"), text);
	}

	@Test
	void noticesComeFirst() {
		Precedence p = precedence(List.of(SetEntry.of("m", AMBIENT).withMute(true)), List.of());
		List<String> lines = text(p, playAt(0, 100, false, false), true).lines().toList();
		assertEquals("  Bypass is on: nothing is changed.", lines.get(1));
		assertEquals("Everywhere", lines.get(2));
	}

	@Test
	void limitDroppedPlayAndCueShown() {
		SetEntry rule = SetEntry.of("l", AMBIENT).withMute(true).withCue(true).withLimit(new Limit(3, 1000, true));
		String text = text(precedence(List.of(rule), List.of()), playAt(0, 0, true, true), false);
		assertTrue(text.endsWith(String.join("\n",
				"Result",
				"  Volume 0%",
				"  Repeat limit: at most 3 per 1 s, per source",
				"  Last play was Limited: dropped by the repeat limit",
				"  Cue on",
				"  A cue was shown",
				"")), text);
	}

	@Test
	void aSharedLimitSaysSoAndAnAllowedPlaySaysItWasAllowed() {
		SetEntry rule = SetEntry.of("l", AMBIENT).withLimit(new Limit(3, 250, false));
		String text = text(precedence(List.of(rule), List.of()), playAt(0, 100, false, false), false);
		assertTrue(text.contains("\n  Repeat limit: at most 3 per 0.25 s, shared\n  Last play was allowed\n"), text);
		assertTrue(text.contains(AMBIENT + ": At most 3 per 0.25 s\n"), "the entry line shows its limit:\n" + text);
		assertTrue(text.contains("Decides limit ("), "and that it decides the limit:\n" + text);
	}

	@Test
	void notHeardYetLeavesOutWhatHappenedAtTheLastPlay() {
		String text = text(precedence(List.of(SetEntry.of("m", AMBIENT).withMute(true)), List.of()), null, false);
		assertTrue(text.contains("Not heard yet: predicted for where you are."), text);
		assertTrue(text.contains("You are in: Everywhere"), text);
		assertFalse(text.contains("Last play"), text);
		assertFalse(text.contains("cue was shown"), text);
	}

	@Test
	void unnamedZonesShowTheirId() {
		Zone unnamed = new Zone("z7", "", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Box(40, 40, 40),
				quick(List.of(SetEntry.of("r", "*").withMute(true))));
		String text = text(precedence(List.of(), List.of(unnamed)), playAt(16, 0, false, false), false);
		assertTrue(text.contains("You were in: z7 → Everywhere\n  z7 (Box 40 × 40 × 40)"), text);
	}

	@Test
	void everywhereDecidesWhatNoZoneSets() {
		Zone cueOnly = cube("Pen", 0, 10, SetEntry.of("c", "*villager*").withCue(true));
		String text = text(precedence(List.of(SetEntry.of("g", AMBIENT).withVolume(40)), List.of(cueOnly)),
				playAt(0, 40, false, false), false);
		assertTrue(text.contains(String.join("\n",
				"Deciding zones",
				"  loudness: Everywhere (No zone sets it)",
				"  cue: Pen (Only zone that sets it)")), text);
	}
}
