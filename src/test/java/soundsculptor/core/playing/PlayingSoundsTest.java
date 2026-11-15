package soundsculptor.core.playing;

import static soundsculptor.core.Fixtures.precedence;
import static soundsculptor.core.Fixtures.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.WhereYouAre;
import soundsculptor.core.zones.Zone;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-8.8 and FR-13.1: a playing sound keeps the volume it was decided with at its start; looping sounds are decided
 * again when where you are or the profile changes; Bypass on puts every sound back to vanilla, Bypass off re-decides
 * loops only. Nothing is re-decided on a tick where none of that changed.
 */
class PlayingSoundsTest {
	private static final String WORLD = "play.example.net";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String MINECART = "minecraft:entity.minecart.riding";
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final boolean LOOP = true;
	private static final boolean ONE_SHOT = false;

	/** An 11-block box around block (0, 64, 0) that mutes minecarts and villagers. */
	private static final Zone BASE = new Zone("base", "Base", true, WORLD, OVERWORLD, new Point(0.5, 64.5, 0.5),
			new Shape.Box(11, 11, 11), quick(SetEntry.of("m", MINECART).withMute(true), SetEntry.of("v", VILLAGER).withMute(true)));

	private final PlayingSounds playing = new PlayingSounds();
	private final Map<Long, Float> reported = new HashMap<>();
	private final PlayingSounds.GainListener listener = reported::put;
	private final WhereYouAre where = new WhereYouAre();
	private final Precedence profile = precedence(List.of(), List.of(BASE));

	private void standInBase(Precedence p) {
		where.update(p, WORLD, OVERWORLD, 0, 64, 0);
	}

	private void standOutside(Precedence p) {
		where.update(p, WORLD, OVERWORLD, 100, 64, 0);
	}

	private int update(Precedence p) {
		return playing.update(p, where, false, true, listener);
	}

	private int bypassed() {
		return playing.update(null, null, true, true, listener);
	}

	@Test
	void aLoopGoesSilentWhenYouWalkInAndComesBackWhenYouWalkOut() {
		standOutside(profile);
		playing.start(1, MINECART, LOOP, 0.8F, 1F);
		update(profile);
		assertTrue(reported.isEmpty(), "outside the base the minecart plays as it started");

		standInBase(profile);
		assertEquals(1, update(profile));
		assertEquals(0F, reported.get(1L), "walking in mutes it, without stopping it");
		assertTrue(playing.isTracked(1));

		standOutside(profile);
		assertEquals(1, update(profile));
		assertEquals(0.8F, reported.get(1L), 1e-6, "walking out brings it back");
	}

	@Test
	void aOneShotPlaysOnAsItStarted() {
		// FR-8.8: a villager mumble already playing when you walk in plays to its end.
		standOutside(profile);
		playing.start(1, VILLAGER, ONE_SHOT, 0.8F, 1F);
		update(profile);
		standInBase(profile);
		assertEquals(0, update(profile));
		assertEquals(0.8F, playing.gain(1), 1e-6);
	}

	@Test
	void anEditReDecidesLoopsOnly() {
		// FR-8.8: Mute on a row works at once for a loop.
		standOutside(profile);
		playing.start(1, MINECART, LOOP, 1F, 1F);
		playing.start(2, VILLAGER, ONE_SHOT, 1F, 1F);
		update(profile);
		Precedence muted = precedence(List.of(SetEntry.of("m", MINECART).withMute(true), SetEntry.of("v", VILLAGER).withMute(true)),
				List.of(BASE));
		standOutside(muted);
		assertEquals(1, update(muted));
		assertEquals(0F, reported.get(1L));
		assertFalse(reported.containsKey(2L));
	}

	@Test
	void nothingIsReDecidedWhileNothingChanges() {
		standInBase(profile);
		playing.start(1, MINECART, LOOP, 1F, 0F);
		update(profile);
		long decisions = playing.decisionCount();
		for (int tick = 0; tick < 100; tick++) {
			where.update(profile, WORLD, OVERWORLD, 1, 64, tick % 3); // moving around inside the base
			assertEquals(0, update(profile));
		}
		assertEquals(decisions, playing.decisionCount(), "moving inside one zone changes nothing");
	}

	@Test
	void bypassOnPutsEverySoundBackToVanillaAndOffReDecidesLoopsOnly() {
		// FR-13.1: a 30% one-shot jumps to full at once; a silent loop comes back; turning Bypass off mutes the loop
		// again at once and leaves the one-shot at vanilla until it ends.
		standInBase(profile);
		playing.start(1, VILLAGER, ONE_SHOT, 1F, 0.3F);
		playing.start(2, MINECART, LOOP, 1F, 1e-6F);
		update(profile);
		reported.clear();

		assertEquals(2, bypassed());
		assertEquals(1F, reported.get(1L), 1e-6);
		assertEquals(1F, reported.get(2L), 1e-6);
		assertEquals(0, bypassed(), "staying on changes nothing more");

		reported.clear();
		assertEquals(1, update(profile));
		assertEquals(0F, reported.get(2L));
		assertEquals(1F, playing.gain(1), 1e-6, "the one-shot keeps playing at vanilla volume");
	}

	@Test
	void aLoopStartedDuringBypassFollowsTheProfileWhenBypassTurnsOff() {
		standInBase(profile);
		bypassed();
		playing.start(1, MINECART, LOOP, 1F, 1F);
		assertEquals(0, bypassed());
		assertEquals(1, update(profile));
		assertEquals(0F, reported.get(1L));
	}

	@Test
	void vanillaRecalculatingTheVolumeKeepsTheSoundsOwnFactor() {
		// A slider change: a 30% one-shot stays at 30% of the new vanilla gain; a muted loop stays silent.
		playing.start(1, VILLAGER, ONE_SHOT, 1F, 0.3F);
		playing.start(2, MINECART, LOOP, 1F, 0F);
		assertEquals(0.15F, playing.recalculated(1, 0.5F), 1e-6);
		assertEquals(0F, playing.recalculated(2, 0.5F));
		assertTrue(Float.isNaN(playing.recalculated(3, 0.5F)), "not tracked");
	}

	@Test
	void aReDecidedLoopUsesTheLatestVanillaGain() {
		standOutside(profile);
		playing.start(1, MINECART, LOOP, 1F, 1F);
		update(profile);
		playing.recalculated(1, 0.5F);
		standInBase(profile);
		update(profile);
		standOutside(profile);
		update(profile);
		assertEquals(0.5F, reported.get(1L), 1e-6);
	}

	@Test
	void boostIsCappedWhenNotSupported() {
		Precedence loud = precedence(List.of(SetEntry.of("m", MINECART).withVolume(150)), List.of());
		where.updateNowhere(loud);
		playing.start(1, MINECART, LOOP, 0.5F, 1F);
		playing.update(loud, where, false, false, listener);
		assertTrue(reported.isEmpty(), "capped at 100%, the gain did not change");
		playing.clear();
		playing.start(1, MINECART, LOOP, 0.5F, 1F);
		playing.update(loud, where, false, true, listener);
		assertEquals(0.75F, reported.get(1L), 1e-6);
	}

	@Test
	void stoppedSoundsAreRemoved() {
		playing.start(1, VILLAGER, ONE_SHOT, 1F, 1F);
		playing.start(2, VILLAGER, ONE_SHOT, 1F, 1F);
		playing.stop(1);
		assertFalse(playing.isTracked(1));
		assertTrue(playing.isTracked(2));
		assertEquals(1, playing.size());
		assertTrue(Float.isNaN(playing.gain(1)));
		playing.stop(1); // stopping twice is harmless
		assertEquals(1, playing.size());
	}

	@Test
	void startingTheSameHandleTwiceIsRefused() {
		assertTrue(playing.start(1, VILLAGER, ONE_SHOT, 1F, 1F));
		assertFalse(playing.start(1, MINECART, LOOP, 1F, 1F));
		assertEquals(1, playing.size());
	}

	@Test
	void aFullTableFailsOpen() {
		for (int i = 0; i < PlayingSounds.CAPACITY; i++) {
			assertTrue(playing.start(i, MINECART, LOOP, 1F, 1F));
		}
		assertFalse(playing.start(9_999, MINECART, LOOP, 1F, 1F));
		assertEquals(1, playing.rejectedCount());
		standInBase(profile);
		assertEquals(PlayingSounds.CAPACITY, update(profile), "every tracked loop is still re-decided");
		assertFalse(reported.containsKey(9_999L));
		playing.stop(0);
		assertTrue(playing.start(9_999, MINECART, LOOP, 1F, 1F), "room again after a sound stops");
	}

	@Test
	void manyStartsAndStopsKeepTheTableConsistent() {
		// Random churn against a plain map, so the handle hash and swap-remove stay in step.
		Random random = new Random(88);
		Map<Long, Float> model = new HashMap<>();
		List<Long> live = new ArrayList<>();
		for (int step = 0; step < 50_000; step++) {
			if (!live.isEmpty() && (live.size() >= PlayingSounds.CAPACITY || random.nextInt(3) == 0)) {
				long h = live.remove(random.nextInt(live.size()));
				playing.stop(h);
				model.remove(h);
			} else {
				long h = random.nextLong() >>> random.nextInt(60); // varied handles, some colliding in the hash
				if (model.containsKey(h)) {
					continue;
				}
				float factor = random.nextInt(100) / 100F;
				assertTrue(playing.start(h, VILLAGER, random.nextBoolean(), 1F, factor));
				model.put(h, factor);
				live.add(h);
			}
		}
		assertEquals(model.size(), playing.size());
		for (long h : live) {
			assertEquals(model.get(h), playing.recalculated(h, 1F), 1e-6, "handle " + h);
		}
		playing.clear();
		assertEquals(0, playing.size());
		assertFalse(playing.isTracked(live.get(0)));
	}

	@Test
	void stoppingASoundKeepsTheOthersLoopFlagAndFactorStraight() {
		// Swap-remove moves the last sound into the hole; its flag and factor must move with it.
		standOutside(profile);
		playing.start(1, VILLAGER, ONE_SHOT, 1F, 0.4F);
		playing.start(2, MINECART, LOOP, 1F, 1F);
		update(profile);
		playing.stop(1);
		standInBase(profile);
		assertEquals(1, update(profile));
		assertEquals(0F, reported.get(2L));
	}
}
