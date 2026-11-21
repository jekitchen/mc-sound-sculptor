package soundsculptor.core.start;

import static soundsculptor.core.Fixtures.quick;
import static soundsculptor.core.Fixtures.precedence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.cue.CueSettings;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.trace.LastPlay;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/**
 * One sound start, end to end (PRD section 7 flowchart): range gate, decide (global rules, zones, precedence, repeat
 * limit), then drop, mute (with cue) or play. Zones follow where the player stands (FR-8.4): {@link #villagerAt} puts the
 * player and the villager in the same block, 5 blocks from the listener for the range gate, unless a test moves one.
 */
class SoundStartDeciderTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final Limit TWO_PER_SECOND = new Limit(2, 1000, false);

	private final LastPlays lastPlays = new LastPlays();
	private final SoundStartDecider decider = new SoundStartDecider(lastPlays);
	private final SoundStart in = new SoundStart();
	private final SoundStartResult out = new SoundStartResult();

	private static SetEntry villagers(String id) {
		return SetEntry.of(id, "minecraft:entity.villager.*");
	}

	/** The limiter key of an entry in Everywhere's Quick changes, where {@link #profile} puts the entries. */
	private static String everywhereKey(String entryId) {
		return SoundStartDecider.limiterKey(null, SoundSet.QUICK_ID, entryId);
	}

	private static Zone cube(String id, double x, double half, SetEntry... rules) {
		return new Zone(id, "Zone " + id, true, WORLD, OVERWORLD, new Point(x, 0, 0), new Shape.Box(2 * half, 2 * half, 2 * half), quick(List.of(rules)));
	}

	private void profile(List<SetEntry> global, Zone... zones) {
		decider.setProfile(precedence(global, List.of(zones)));
	}

	/**
	 * The player's feet in block {@code (floor(x), 0, 0)} and a villager sound at {@code (x, 0, 0)}, heard from 5 blocks
	 * away with vanilla's 16-block range.
	 */
	private SoundStart villagerAt(double x) {
		in.reset();
		in.soundId = VILLAGER;
		in.positional = true;
		in.range = 16;
		in.distanceSq = 25;
		in.worldKey = WORLD;
		in.dimension = OVERWORLD;
		in.x = x;
		in.feetKnown = true;
		in.feetX = (int) Math.floor(x);
		in.hasSubtitle = true;
		return in;
	}

	private SoundStartResult start(SoundStart sound, long nowMs) {
		sound.nowMs = nowMs;
		decider.start(sound, out);
		return out;
	}

	private SoundStartResult start(SoundStart sound) {
		return start(sound, 1_000);
	}

	// --- Pass-through cases ---

	@Test
	void noRulesPlaysAsVanilla() {
		profile(List.of());
		SoundStartResult r = start(villagerAt(0));
		assertTrue(r.handled());
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(100, r.volumePercent(), 1e-6);
		assertEquals(1.0F, r.factor(), 1e-6);
		assertFalse(r.cancel());
		assertFalse(r.cue());
		assertNull(r.zone());
	}

	@Test
	void bypassLeavesEverythingAlone() {
		profile(List.of(villagers("g1").withMute(true)));
		SoundStart s = villagerAt(0);
		s.bypass = true;
		SoundStartResult r = start(s);
		assertFalse(r.handled());
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(1.0F, r.factor(), 1e-6);
		assertFalse(r.cancel());
		// The trace still knows the sound was heard, so the panel can say Bypass was on.
		assertEquals(100, lastPlays.get(VILLAGER).volumePercent(), 1e-6);
	}

	@Test
	void p11MuteAllSilencesMusicAndUiToo() {
		// Every sound is in scope (section 5, decided 2026-11-14).
		profile(List.of(), cube("a", 0, 10, SetEntry.of("all", "*").withMute(true)));
		for (String id : List.of("minecraft:music.game", "minecraft:ui.button.click", "minecraft:entity.generic.eat")) {
			SoundStart s = villagerAt(0);
			s.soundId = id;
			s.positional = false;
			s.range = Float.POSITIVE_INFINITY;
			SoundStartResult r = start(s);
			assertTrue(r.handled(), id);
			assertEquals(PlayStatus.MUTED, r.status(), id);
			assertTrue(r.cancel(), id + " is not a loop, so it does not start");
			assertNotNull(lastPlays.get(id), id + " has a trace");
		}
	}

	@Test
	void noProfileYetFailsOpen() {
		SoundStartResult r = start(villagerAt(0));
		assertFalse(r.handled());
		assertEquals(1.0F, r.factor(), 1e-6);
		assertFalse(r.cancel());
	}

	// --- Mute and volume ---

	@Test
	void p2GlobalMuteCancelsTheStart() {
		profile(List.of(villagers("g1").withMute(true)));
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.MUTED, r.status());
		assertEquals(0, r.volumePercent(), 1e-6);
		assertEquals(0F, r.factor(), 1e-6);
		assertTrue(r.cancel(), "nothing can unmute it while it plays, so no channel is needed");
	}

	@Test
	void zeroVolumeCountsAsMuted() {
		profile(List.of(villagers("g1").withVolume(0)));
		assertEquals(PlayStatus.MUTED, start(villagerAt(0)).status());
	}

	@Test
	void turnedDownPlaysQuieter() {
		profile(List.of(villagers("g1").withVolume(25)));
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(0.25F, r.factor(), 1e-6);
	}

	@Test
	void p1ZoneOverridesGlobalInsideAndNamesTheZone() {
		Zone a = cube("a", 0, 10, villagers("z1").withVolume(50));
		profile(List.of(villagers("g1").withMute(true)), a);

		SoundStartResult inside = start(villagerAt(0));
		assertEquals(0.5F, inside.factor(), 1e-6);
		assertSame(a, inside.zone());
		assertEquals("Zone a", inside.zoneName());

		SoundStartResult outside = start(villagerAt(100));
		assertEquals(PlayStatus.MUTED, outside.status());
		assertNull(outside.zone());
		assertTrue(outside.cancel(), "a muted one-shot does not start");
	}

	@Test
	void p3SmallestZoneYouStandInWins() {
		Zone small = cube("a", 3, 10, villagers("ra").withVolume(20));
		Zone big = cube("b", 9, 20, villagers("rb").withVolume(80));
		profile(List.of(), big, small);
		assertEquals(0.2F, start(villagerAt(0)).factor(), 1e-6, "in both: the smaller wins, whatever the list order");
		assertEquals(0.8F, start(villagerAt(20)).factor(), 1e-6, "only in the big one");
	}

	@Test
	void p17ASoundOutsideTheZoneYouStandInFollowsThatZone() {
		profile(List.of(), cube("a", 0, 10, villagers("ra").withMute(true)));
		SoundStart s = villagerAt(0);
		s.x = 50; // the villager is far outside zone a; you are inside it
		assertEquals(PlayStatus.MUTED, start(s).status());
	}

	@Test
	void p18ASoundInsideAZoneYouAreNotInIsNotChanged() {
		profile(List.of(), cube("a", 0, 10, villagers("ra").withMute(true)));
		SoundStart s = villagerAt(50);
		s.x = 0; // the villager is in zone a; you are not
		assertEquals(PlayStatus.PLAYING, start(s).status());
		assertEquals(1.0F, out.factor(), 1e-6);
	}

	@Test
	void withoutAFeetBlockOnlyEverywhereApplies() {
		profile(List.of(villagers("g1").withVolume(40)), cube("a", 0, 10, villagers("ra").withMute(true)));
		SoundStart s = villagerAt(0);
		s.feetKnown = false;
		assertEquals(0.4F, start(s).factor(), 1e-6);
	}

	@Test
	void p10FixedZonesStayInTheirDimension() {
		profile(List.of(), cube("a", 0, 10, villagers("ra").withMute(true)));
		SoundStart s = villagerAt(0);
		s.dimension = NETHER;
		SoundStartResult r = start(s);
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(1.0F, r.factor(), 1e-6);
	}

	@Test
	void p12AMutedLoopStartsSilentlySoItCanComeBack() {
		// FR-8.8: a minecart rolling in a muting zone starts at volume 0, and follows you out of the zone.
		profile(List.of(), cube("a", 0, 10, villagers("ra").withMute(true)));
		SoundStart s = villagerAt(0);
		s.looping = true;
		SoundStartResult r = start(s);
		assertEquals(PlayStatus.MUTED, r.status());
		assertFalse(r.cancel());
		assertEquals(0F, r.factor(), 1e-6);
	}

	@Test
	void aMutedOneShotDoesNotStartWhereverTheMuteComesFrom() {
		profile(List.of(villagers("g1").withMute(true)), cube("far", 500, 10, villagers("z1").withVolume(50)));
		assertTrue(start(villagerAt(0)).cancel(), "from Everywhere");
		profile(List.of(), cube("a", 0, 10, villagers("ra").withMute(true)));
		assertTrue(start(villagerAt(0)).cancel(), "from a zone");
	}

	@Test
	void aMutedNonPositionalLoopStartsSilentlyToo() {
		// The underwater loop and biome ambience have no position, and follow you all the same.
		profile(List.of(villagers("g1").withMute(true)));
		SoundStart s = villagerAt(0);
		s.positional = false;
		s.range = Float.POSITIVE_INFINITY;
		s.looping = true;
		assertFalse(start(s).cancel());
	}

	@Test
	void disabledZonesNeverOverride() {
		Zone a = new Zone("a", "A", false, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Box(20, 20, 20),
				quick(List.of(villagers("z1").withVolume(50))));
		profile(List.of(villagers("g1").withMute(true)), a);
		assertTrue(start(villagerAt(0)).cancel());
	}

	// --- Boost ---

	@Test
	void boostAbove100() {
		profile(List.of(villagers("g1").withVolume(150)));
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.BOOSTED, r.status());
		assertEquals(1.5F, r.factor(), 1e-6);
	}

	@Test
	void boostStopsAt100WhenTheDeviceCannotBoost() {
		profile(List.of(villagers("g1").withVolume(150)));
		decider.setBoostSupported(false);
		SoundStartResult r = start(villagerAt(0));
		assertEquals(PlayStatus.PLAYING, r.status());
		assertEquals(1.0F, r.factor(), 1e-6);
		assertEquals(150, r.volumePercent(), 1e-6, "the decided volume is kept for the trace");
	}

	@Test
	void p8CueOnlyZoneKeepsTheGlobalBoost() {
		profile(List.of(villagers("g1").withVolume(150)), cube("a", 0, 10, villagers("z1").withCue(true)));
		assertEquals(1.5F, start(villagerAt(0)).factor(), 1e-6);
	}

	// --- Range gate ---

	@Test
	void outOfRangeSoundsAreDecidedButNotHeard() {
		profile(List.of(villagers("g1").withVolume(50).withLimit(new Limit(1, 1000, false)).withCue(true)));
		SoundStart s = villagerAt(0);
		s.distanceSq = 20 * 20;
		for (int i = 0; i < 5; i++) {
			SoundStartResult r = start(s, 1_000 + i);
			assertFalse(r.audible());
			assertEquals(PlayStatus.PLAYING, r.status(), "never limited: unheard plays do not use up the limit");
			assertEquals(0.5F, r.factor(), 1e-6);
		}
		assertNull(lastPlays.get(VILLAGER), "not heard, not logged");
		// The limit is still free for the first sound the player can hear.
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_010).status());
	}

	@Test
	void rangeIsStrictlyCloser() {
		profile(List.of());
		SoundStart s = villagerAt(0);
		s.distanceSq = 16 * 16;
		assertFalse(start(s).audible());
		s.distanceSq = 15.99 * 15.99;
		assertTrue(start(s).audible());
	}

	// --- Repeat limit ---

	@Test
	void limitDropsPlaysOverTheCap() {
		profile(List.of(villagers("g1").withLimit(TWO_PER_SECOND)));
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_000).status());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_100).status());
		SoundStartResult dropped = start(villagerAt(0), 1_200);
		assertEquals(PlayStatus.LIMITED, dropped.status());
		assertTrue(dropped.cancel());
		assertTrue(lastPlays.get(VILLAGER).dropped());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 2_000).status(), "the window slides");
		assertEquals(1, decider.limiter().droppedCount(everywhereKey("g1")));
	}

	@Test
	void p4MutedWithLimitFromAnotherZone() {
		Zone a = cube("a", 2, 10, villagers("ra").withMute(true).withCue(true));
		Zone b = cube("b", 6, 10, villagers("rb").withLimit(TWO_PER_SECOND));
		profile(List.of(), a, b);
		assertEquals(PlayStatus.MUTED, start(villagerAt(0), 1_000).status());
		assertTrue(out.cue());
		assertEquals(PlayStatus.MUTED, start(villagerAt(0), 1_001).status());
		SoundStartResult third = start(villagerAt(0), 1_002);
		assertEquals(PlayStatus.LIMITED, third.status());
		assertSame(b, third.zone(), "the zone that dropped it is the one shown");
		assertFalse(third.cue(), "dropped plays never produce a cue (FR-6.8)");
	}

	@Test
	void zoneLimitWinsInsideTheZone() {
		// FR-6 AC: global limit 5/s, zone limit 1/s on the same sound: inside the zone, at most 1 per second plays.
		profile(List.of(villagers("g1").withLimit(new Limit(5, 1000, false))),
				cube("a", 0, 10, villagers("z1").withLimit(new Limit(1, 1000, false))));
		int played = 0;
		for (int i = 0; i < 10; i++) {
			if (start(villagerAt(0), 1_000 + i * 50).status() != PlayStatus.LIMITED) {
				played++;
			}
		}
		assertEquals(1, played);
	}

	@Test
	void globalAndZoneRulesWithTheSameIdHaveTheirOwnCounters() {
		// Entry IDs are unique within one set only, so Everywhere's "r1" and zone a's "r1" are different entries (FR-6.7).
		SetEntry global = SetEntry.of("r1", "minecraft:entity.cow.*").withLimit(new Limit(1, 1000, false));
		Zone a = cube("a", 0, 10, villagers("r1").withLimit(new Limit(1, 1000, false)));
		profile(List.of(global), a);

		SoundStart cow = villagerAt(100); // outside the zone: the global rule decides
		cow.soundId = "minecraft:entity.cow.ambient";
		assertEquals(PlayStatus.PLAYING, start(cow, 1_000).status());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_001).status(), "zone a's r1 has not played yet");
		assertEquals(PlayStatus.LIMITED, start(villagerAt(0), 1_002).status());
		assertEquals(1, decider.limiter().droppedCount(SoundStartDecider.limiterKey("a", SoundSet.QUICK_ID, "r1")));
		assertEquals(0, decider.limiter().droppedCount(everywhereKey("r1")));
	}

	@Test
	void duplicateSetsHaveSeparateLimiterKeys() {
		// A Duplicate shares the original's entry instances, yet each set counts on its own (FR-6.7).
		SetEntry limited = villagers("e1").withLimit(new Limit(1, 1000, false));
		SoundSet farm = new SoundSet("set-farm", "Farm", List.of(limited));
		SoundSet copy = new SoundSet("set-copy", "Farm (2)", farm.entries());
		SetLibrary library = new SetLibrary(List.of(farm, copy));
		PlaceSets both = PlaceSets.EMPTY.attach(copy.id()).attach(farm.id());
		String copyKey = SoundStartDecider.limiterKey(null, copy.id(), "e1");
		String farmKey = SoundStartDecider.limiterKey(null, farm.id(), "e1");

		decider.setProfile(new Precedence(both, List.of(), library));
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_000).status());
		assertEquals(PlayStatus.LIMITED, start(villagerAt(0), 1_001).status());
		assertEquals(1, decider.limiter().droppedCount(copyKey), "the top set won, so its counter counted");
		assertEquals(0, decider.limiter().droppedCount(farmKey));

		// The original moved to the top: it wins now, with a counter of its own that has not played yet.
		decider.setProfile(new Precedence(both.move(farm.id(), -1), List.of(), library));
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_002).status());
		assertEquals(PlayStatus.LIMITED, start(villagerAt(0), 1_003).status());
		assertEquals(1, decider.limiter().droppedCount(farmKey));
		assertEquals(1, decider.limiter().droppedCount(copyKey));
	}

	@Test
	void twoZonesWithTheSameRuleIdHaveTheirOwnCounters() {
		Zone a = cube("a", 0, 10, villagers("r1").withLimit(new Limit(1, 1000, false)));
		Zone b = cube("b", 100, 10, villagers("r1").withLimit(new Limit(1, 1000, false)));
		profile(List.of(), a, b);
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_000).status());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(100), 1_001).status());
	}

	@Test
	void oneLibrarySetAttachedToTwoZonesCountsInEachOnItsOwn() {
		// FR-6 AC: one set limiting villagers to 2/s, attached to two zones far apart: each pen plays up to 2 per second.
		SoundSet pens = new SoundSet("set-pens", "Pens", List.of(villagers("e1").withLimit(TWO_PER_SECOND)));
		Zone a = new Zone("a", "Pen A", true, WORLD, OVERWORLD, new Point(0, 0, 0), new Shape.Box(20, 20, 20), PlaceSets.EMPTY.attach(pens.id()));
		Zone b = new Zone("b", "Pen B", true, WORLD, OVERWORLD, new Point(500, 0, 0), new Shape.Box(20, 20, 20), PlaceSets.EMPTY.attach(pens.id()));
		decider.setProfile(new Precedence(PlaceSets.EMPTY, List.of(a, b), new SetLibrary(List.of(pens))));
		int inA = 0;
		int inB = 0;
		for (int i = 0; i < 5; i++) {
			inA += start(villagerAt(0), 1_000 + i).status() == PlayStatus.PLAYING ? 1 : 0;
			inB += start(villagerAt(500), 1_000 + i).status() == PlayStatus.PLAYING ? 1 : 0;
		}
		assertEquals(2, inA);
		assertEquals(2, inB);
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 2_000).status(), "each window slides on its own");
	}

	@Test
	void p4bQuieterAndLimitedTogether() {
		// Matrix P4b: A sets villagers to 40%, B limits them to 2/s, the player stands in both: at most 2 per second,
		// each at 40%.
		Zone a = cube("a", 2, 10, villagers("ra").withVolume(40));
		Zone b = cube("b", 6, 12, villagers("rb").withLimit(TWO_PER_SECOND));
		profile(List.of(), a, b);
		int played = 0;
		for (int i = 0; i < 10; i++) {
			SoundStartResult r = start(villagerAt(0), 1_000 + i * 10);
			if (r.status() != PlayStatus.LIMITED) {
				played++;
				assertEquals(0.4F, r.factor(), 1e-6, "each allowed play at 40%");
				assertSame(a, r.zone(), "loudness from A");
			} else {
				assertSame(b, r.zone(), "the drop from B");
			}
		}
		assertEquals(2, played);
	}

	@Test
	void aLimitedLoopNeverStartsAndOutOfRangePlaysDoNotCount() {
		// FR-6.6: a looping sound counts once, at its start; a dropped loop does not start at all (the caller never
		// re-decides a limit while a sound plays). A sound out of vanilla range is not heard, so it uses up nothing.
		profile(List.of(villagers("g1").withLimit(new Limit(1, 10_000, false))));
		SoundStart far = villagerAt(0);
		far.distanceSq = 40 * 40;
		assertFalse(start(far, 1_000).audible());
		SoundStart loop = villagerAt(0);
		loop.looping = true;
		assertEquals(PlayStatus.PLAYING, start(loop, 1_001).status(), "the first loop starts");
		SoundStart second = villagerAt(0);
		second.looping = true;
		SoundStartResult dropped = start(second, 3_000);
		assertEquals(PlayStatus.LIMITED, dropped.status());
		assertTrue(dropped.cancel(), "a dropped loop is not started silently: it would never be counted again");
	}

	@Test
	void countersSurviveASaveThatKeepsTheRule() {
		SetEntry g1 = villagers("g1").withLimit(new Limit(1, 1000, false));
		profile(List.of(g1));
		start(villagerAt(0), 1_000);
		profile(List.of(g1.withCue(true))); // the player saves an unrelated change
		assertEquals(PlayStatus.LIMITED, start(villagerAt(0), 1_001).status());
	}

	@Test
	void countersOfDeletedRulesAreForgotten() {
		profile(List.of(villagers("g1").withLimit(TWO_PER_SECOND), SetEntry.of("g2", "*cow*").withLimit(TWO_PER_SECOND)));
		start(villagerAt(0), 1_000);
		SoundStart cow = villagerAt(0);
		cow.soundId = "minecraft:entity.cow.ambient";
		start(cow, 1_000);
		assertEquals(2, decider.limiter().ruleIds().size());
		profile(List.of(villagers("g1").withLimit(TWO_PER_SECOND)));
		assertEquals(java.util.Set.of(everywhereKey("g1")), decider.limiter().ruleIds());
	}

	@Test
	void perSourceLimitCountsEachBlock() {
		profile(List.of(villagers("g1").withLimit(new Limit(1, 1000, true))));
		assertEquals(PlayStatus.PLAYING, start(villagerAt(0), 1_000).status());
		assertEquals(PlayStatus.PLAYING, start(villagerAt(3), 1_001).status());
		assertEquals(PlayStatus.LIMITED, start(villagerAt(0.5), 1_002).status());
	}

	// --- Cues ---

	@Test
	void mutedSoundWithCueOnShowsItsSubtitle() {
		profile(List.of(villagers("g1").withMute(true).withCue(true)));
		SoundStartResult r = start(villagerAt(0));
		assertTrue(r.cue());
		assertFalse(r.cueUsesSoundId());
		assertTrue(lastPlays.get(VILLAGER).cueShown());
	}

	@Test
	void noCueWhenTheRuleHasCueOff() {
		profile(List.of(villagers("g1").withMute(true)));
		assertFalse(start(villagerAt(0)).cue());
	}

	@Test
	void noCueForAudibleSounds() {
		profile(List.of(villagers("g1").withVolume(10).withCue(true)));
		assertFalse(start(villagerAt(0)).cue());
	}

	@Test
	void noCueOutOfRange() {
		profile(List.of(villagers("g1").withMute(true).withCue(true)));
		SoundStart s = villagerAt(0);
		s.distanceSq = 25 * 25;
		assertFalse(start(s).cue());
	}

	@Test
	void masterSwitchOffHidesAllCues() {
		profile(List.of(villagers("g1").withMute(true).withCue(true)));
		decider.setCueSettings(new CueSettings(false, true, true));
		assertFalse(start(villagerAt(0)).cue());
	}

	@Test
	void soundsWithoutSubtitlesShowTheirIdOnlyWhenAllowed() {
		profile(List.of(villagers("g1").withMute(true).withCue(true)));
		SoundStart s = villagerAt(0);
		s.hasSubtitle = false;
		assertFalse(start(s).cue());

		decider.setCueSettings(new CueSettings(true, true, true));
		SoundStartResult r = start(s);
		assertTrue(r.cue());
		assertTrue(r.cueUsesSoundId());
	}

	@Test
	void aZoneMuteShowsACueWhileYouStandInIt() {
		profile(List.of(), cube("a", 0, 10, villagers("z1").withMute(true).withCue(true)));
		assertTrue(start(villagerAt(0)).cue());
		assertFalse(start(villagerAt(50)).cue(), "outside the zone the sound plays");
	}

	// --- Trace ---

	@Test
	void theLastPlayIsRecordedForTheTrace() {
		profile(List.of(villagers("g1").withVolume(60)));
		SoundStart s = villagerAt(1);
		s.feetY = 64;
		s.feetZ = -3;
		start(s);
		LastPlay play = lastPlays.get(VILLAGER);
		assertNotNull(play);
		assertEquals(60, play.volumePercent(), 1e-6);
		assertTrue(play.feetKnown());
		assertEquals(1, play.feetX(), "where you stood, not where the sound was");
		assertEquals(64, play.feetY());
		assertEquals(-3, play.feetZ());
		assertEquals(WORLD, play.worldKey());
		assertFalse(play.dropped());
		assertFalse(play.cueShown());
	}
}
