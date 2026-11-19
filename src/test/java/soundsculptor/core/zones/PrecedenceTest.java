package soundsculptor.core.zones;

import static soundsculptor.core.Fixtures.at;
import static soundsculptor.core.Fixtures.nowhere;
import static soundsculptor.core.Fixtures.precedence;
import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-9 and the zone precedence matrix (section 11.4): zones follow where the player's feet are, smallest first
 * (decided 2026-11-14). The sound's own position plays no part, so {@link Precedence#decide} does not take one.
 */
class PrecedenceTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";

	private static SetEntry villagers(String id) {
		return SetEntry.of(id, "minecraft:entity.villager.*");
	}

	/** A box of {@code w x h x d} blocks whose low corner is block {@code (x, 0, 0)}. */
	private static Zone box(String id, int x, int w, int h, int d, SetEntry... entries) {
		return new Zone(id, id, true, WORLD, OVERWORLD, new Point(x + w / 2.0, h / 2.0, d / 2.0), new Shape.Box(w, h, d),
				quick(List.of(entries)));
	}

	/** Decides a villager sound with the player's feet in block {@code (x, 1, 1)}. */
	private static SoundDecision standingAt(Precedence p, int x) {
		SoundDecision out = new SoundDecision();
		p.decide(VILLAGER, at(p, WORLD, OVERWORLD, x, 1, 1), out);
		return out;
	}

	// --- Matrix cases ---

	@Test
	void p1ZoneOverridesEverywhereWhileYouStandInIt() {
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withVolume(50));
		Precedence p = precedence(List.of(villagers("g").withMute(true)), List.of(a));
		assertEquals(50, standingAt(p, 2).volumePercent(), 1e-6);
		assertSame(a, standingAt(p, 2).loudnessZone());
		assertEquals(0, standingAt(p, 50).volumePercent(), 1e-6, "muted when you are not in A");
		assertNull(standingAt(p, 50).loudnessZone());
	}

	@Test
	void p2EverywhereAloneWithNoZones() {
		Precedence p = precedence(List.of(villagers("g").withMute(true)), List.of());
		assertEquals(0, standingAt(p, 0).volumePercent(), 1e-6);
	}

	@Test
	void p3TheSmallerZoneInsideTheBiggerOneWins() {
		// A (10 x 5 x 10) inside B (40 x 10 x 40); B is first in the list, but A is smaller.
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withVolume(20));
		Zone b = box("b", 0, 40, 10, 40, villagers("rb").withVolume(80));
		Precedence p = precedence(List.of(), List.of(b, a));
		assertEquals(20, standingAt(p, 2).volumePercent(), 1e-6);
		assertEquals(80, standingAt(p, 30).volumePercent(), 1e-6, "out of A but still in B");
	}

	@Test
	void p4EachPropertyComesFromTheFirstZoneThatSetsIt() {
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withMute(true));
		Zone b = box("b", 0, 20, 5, 20, villagers("rb").withLimit(new Limit(2, 1000, false)));
		SoundDecision d = standingAt(precedence(List.of(), List.of(a, b)), 2);
		assertEquals(0, d.volumePercent(), 1e-6);
		assertSame(a, d.loudnessZone());
		assertEquals(new Limit(2, 1000, false), d.limit());
		assertSame(b, d.limitZone());
	}

	@Test
	void p4bVolumeAndLimitApplyTogether() {
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withVolume(40));
		Zone b = box("b", 0, 20, 5, 20, villagers("rb").withLimit(new Limit(2, 1000, false)));
		SoundDecision d = standingAt(precedence(List.of(), List.of(b, a)), 2);
		assertEquals(40, d.volumePercent(), 1e-6);
		assertEquals(new Limit(2, 1000, false), d.limit());
	}

	@Test
	void p5AndP5bEqualSizesGoByListOrder() {
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withVolume(20));
		Zone b = box("b", 2, 10, 5, 10, villagers("rb").withVolume(80));
		assertEquals(20, standingAt(precedence(List.of(), List.of(a, b)), 5).volumePercent(), 1e-6);
		assertEquals(80, standingAt(precedence(List.of(), List.of(b, a)), 5).volumePercent(), 1e-6, "swapped");
	}

	@Test
	void p8CueFromAZoneKeepsTheLoudnessFromEverywhere() {
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withCue(true));
		SoundDecision d = standingAt(precedence(List.of(villagers("g").withVolume(150)), List.of(a)), 2);
		assertEquals(150, d.volumePercent(), 1e-6);
		assertNull(d.loudnessZone());
		assertTrue(d.cueOn());
		assertSame(a, d.cueZone());
	}

	@Test
	void p17AndP18OnlyWhereYouStandCounts() {
		// decide has no sound position at all: a villager outside A is muted while you stand in A (P17), and one inside A
		// is heard while you stand outside it (P18).
		Precedence p = precedence(List.of(), List.of(box("a", 0, 10, 5, 10, villagers("ra").withMute(true))));
		assertEquals(0, standingAt(p, 2).volumePercent(), 1e-6);
		assertEquals(100, standingAt(p, 30).volumePercent(), 1e-6);
	}

	// --- Where you are ---

	@Test
	void theZoneHasHardEdgesAtBlockPrecision() {
		Precedence p = precedence(List.of(), List.of(box("a", 0, 10, 5, 10, villagers("ra").withMute(true))));
		assertEquals(0, standingAt(p, 0).volumePercent(), 1e-6, "first block inside");
		assertEquals(0, standingAt(p, 9).volumePercent(), 1e-6, "last block inside");
		assertEquals(100, standingAt(p, 10).volumePercent(), 1e-6, "first block outside");
		assertEquals(100, standingAt(p, -1).volumePercent(), 1e-6);
	}

	@Test
	void aFloorOnlyBoxDoesNotContainThePlayerStandingOnIt() {
		// FR-8.4: corners marked on the floor blocks at y = 0 make a 1-block-tall box; your feet are in y = 1.
		Zone floor = new Zone("f", "f", true, WORLD, OVERWORLD, new Point(5, 0.5, 5), new Shape.Box(10, 1, 10),
				quick(villagers("rf").withMute(true)));
		Zone grown = new Zone("f", "f", true, WORLD, OVERWORLD, new Point(5, 1, 5), new Shape.Box(10, 2, 10),
				quick(villagers("rf").withMute(true)));
		assertEquals(100, standingAt(precedence(List.of(), List.of(floor)), 2).volumePercent(), 1e-6);
		assertEquals(0, standingAt(precedence(List.of(), List.of(grown)), 2).volumePercent(), 1e-6, "after Grow Up");
	}

	@Test
	void boxesContainTheBlocksWhoseCentresAreInside() {
		Zone c = new Zone("c", "c", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Box(10, 10, 10),
				quick(villagers("rc").withMute(true)));
		Precedence p = precedence(List.of(), List.of(c));
		SoundDecision out = new SoundDecision();
		p.decide(VILLAGER, at(p, WORLD, OVERWORLD, 4, 0, 0), out);
		assertEquals(0, out.volumePercent(), 1e-6, "block 4's centre is 4.5 from the anchor, inside the half-width 5");
		p.decide(VILLAGER, at(p, WORLD, OVERWORLD, 5, 0, 0), out);
		assertEquals(100, out.volumePercent(), 1e-6, "block 5's centre is 5.5 from the anchor, outside");
	}

	@Test
	void zonesInAnotherDimensionOrWorldOrTurnedOffTakeNoPart() {
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withMute(true));
		Precedence p = precedence(List.of(), List.of(a, a.withWorldKey("other.example.net"), a.withEnabled(false)));
		SoundDecision out = new SoundDecision();
		p.decide(VILLAGER, at(p, WORLD, NETHER, 2, 1, 1), out);
		assertEquals(100, out.volumePercent(), 1e-6);
		p.decide(VILLAGER, at(p, "other.example.net", OVERWORLD, 2, 1, 1), out);
		assertEquals(0, out.volumePercent(), 1e-6, "the copy in the other world applies there");
		Precedence off = precedence(List.of(), List.of(a.withEnabled(false)));
		off.decide(VILLAGER, at(off, WORLD, OVERWORLD, 2, 1, 1), out);
		assertEquals(100, out.volumePercent(), 1e-6);
	}

	@Test
	void aZoneWhoseSetsDoNotMatchPassesThePropertyOn() {
		Zone small = box("small", 0, 10, 5, 10, SetEntry.of("cows", "minecraft:entity.cow.*").withMute(true));
		Zone big = box("big", 0, 40, 10, 40, villagers("rb").withVolume(30));
		assertEquals(30, standingAt(precedence(List.of(), List.of(small, big)), 2).volumePercent(), 1e-6);
	}

	@Test
	void withNoFeetBlockOnlyEverywhereApplies() {
		Precedence p = precedence(List.of(villagers("g").withVolume(40)),
				List.of(box("a", 0, 10, 5, 10, villagers("ra").withMute(true))));
		SoundDecision out = new SoundDecision();
		p.decide(VILLAGER, nowhere(p), out);
		assertEquals(40, out.volumePercent(), 1e-6);
	}

	@Test
	void whereYouAreForAnotherSnapshotIsRefused() {
		Precedence p = precedence(List.of(), List.of());
		Precedence other = precedence(List.of(), List.of());
		assertThrows(IllegalArgumentException.class, () -> p.decide(VILLAGER, at(other, WORLD, OVERWORLD, 0, 0, 0),
				new SoundDecision()));
	}

	@Test
	void whereYouAreChangesOnlyWhenTheListDoes() {
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withMute(true));
		Precedence p = precedence(List.of(), List.of(a));
		WhereYouAre where = new WhereYouAre();
		assertTrue(where.update(p, WORLD, OVERWORLD, 2, 1, 1));
		long version = where.version();
		assertFalse(where.update(p, WORLD, OVERWORLD, 2, 1, 1), "same block");
		assertFalse(where.update(p, WORLD, OVERWORLD, 3, 1, 2), "another block in the same zone");
		assertEquals(version, where.version());
		assertTrue(where.update(p, WORLD, OVERWORLD, 30, 1, 1), "stepped out");
		assertEquals(0, where.count());
		Precedence edited = precedence(List.of(villagers("g").withVolume(10)), List.of(a));
		assertFalse(where.update(edited, WORLD, OVERWORLD, 30, 1, 1), "a new snapshot with the same result");
		assertSame(edited, where.precedence());
	}

	@Test
	void whereYouAreListsZonesSmallestFirstThenInListOrder() {
		Zone big = box("big", 0, 40, 10, 40);
		Zone sameA = box("sameA", 0, 10, 5, 10);
		Zone sameB = box("sameB", 1, 10, 5, 10);
		Zone small = box("small", 1, 4, 4, 4);
		Precedence p = precedence(List.of(), List.of(big, sameA, sameB, small));
		WhereYouAre where = at(p, WORLD, OVERWORLD, 2, 1, 1);
		assertEquals(4, where.count());
		assertEquals(List.of(3, 1, 2, 0), List.of(where.zone(0), where.zone(1), where.zone(2), where.zone(3)));
	}

	// --- The decision object ---

	@Test
	void noEntriesAnywhereMeansVanilla() {
		SoundDecision d = standingAt(precedence(List.of(), List.of(box("a", 0, 10, 5, 10))), 2);
		assertEquals(100, d.volumePercent(), 1e-6);
		assertNull(d.limit());
		assertFalse(d.cueOn());
		assertNull(d.loudnessRule());
	}

	@Test
	void decisionReportsTheDecidingEntries() {
		SetEntry everywhere = villagers("g").withMute(true).withCue(true);
		SetEntry zoneEntry = villagers("z").withVolume(50);
		SoundDecision d = standingAt(precedence(List.of(everywhere), List.of(box("a", 0, 10, 5, 10, zoneEntry))), 2);
		assertSame(zoneEntry, d.loudnessRule());
		assertSame(everywhere, d.cueRule());
		assertNull(d.cueZone());
	}

	@Test
	void reusingTheDecisionObjectClearsOldResults() {
		Zone a = box("a", 0, 10, 5, 10, villagers("ra").withMute(true).withLimit(new Limit(2, 1000, false)).withCue(true));
		Precedence p = precedence(List.of(), List.of(a));
		SoundDecision d = new SoundDecision();
		p.decide(VILLAGER, at(p, WORLD, OVERWORLD, 2, 1, 1), d);
		assertSame(a, d.loudnessZone());
		p.decide(VILLAGER, at(p, WORLD, OVERWORLD, 50, 1, 1), d);
		assertNull(d.loudnessZone());
		assertNull(d.limit());
		assertFalse(d.cueOn());
		assertEquals(100, d.volumePercent(), 1e-6);
	}

	@Test
	void cacheStatsAddUpEverywhereAndZoneLists() {
		Precedence p = precedence(List.of(villagers("g").withMute(true)), List.of(box("a", 0, 10, 5, 10, villagers("ra").withVolume(50))));
		standingAt(p, 2);
		standingAt(p, 2);
		// The first decide looks up Everywhere and zone a (2 misses); the second answers both from the row (2 hits).
		assertEquals(2, p.cacheHits());
		assertEquals(2, p.cacheMisses());
		assertEquals(1, p.zoneCount());
		assertEquals(1, p.everywhere().ruleCount());
	}

	@Test
	void sixtyFourZonesAllContainingThePlayerStillWork() {
		// FR-8.9: up to 64 zones per profile; the smallest wins.
		Zone[] zones = new Zone[Precedence.MAX_ZONES];
		for (int i = 0; i < zones.length; i++) {
			zones[i] = box("z" + i, 0, 100 - i, 10, 100 - i, villagers("r" + i).withVolume(i));
		}
		assertEquals(Precedence.MAX_ZONES - 1, standingAt(precedence(List.of(), List.of(zones)), 2).volumePercent(), 1e-6);
	}
}
