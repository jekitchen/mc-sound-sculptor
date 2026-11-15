package soundsculptor.core.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.diag.TimingWindow;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentSounds;
import soundsculptor.core.trace.LastPlays;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.SoundDecision;
import soundsculptor.core.zones.WhereYouAre;

import org.junit.jupiter.api.Test;

/**
 * PRD section 9.1: the sound hot path must cost under 5 µs per sound on average with 50 global rules and 64 zones
 * that all contain the player, and allocate nothing in steady state. Each sound start checks where the player is, as
 * the sound hook does. These run on every build, so a regression shows up in CI, not in a farm.
 *
 * <p>The time limit is the PRD budget, far above the expected cost, so a slow CI machine does not make it flaky. The
 * allocation check uses the JVM's own per-thread counter.
 */
class HotPathBudgetTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final double BUDGET_NANOS = 5_000;

	private final Precedence precedence = new Precedence(StressProfile.everywhere(), StressProfile.zones(WORLD, OVERWORLD),
			StressProfile.library());
	private final String[] ids = StressProfile.soundIds().toArray(new String[0]);
	private final WhereYouAre where = new WhereYouAre();

	/** What the sound hook does to decide one sound: check where the player is (cheap when unchanged), then decide. */
	private void decide(int i, SoundDecision out) {
		where.update(precedence, WORLD, OVERWORLD, StressProfile.FEET_X, StressProfile.FEET_Y, StressProfile.FEET_Z);
		precedence.decide(ids[i], where, out);
	}

	private void decideAll(SoundDecision out, int rounds) {
		for (int r = 0; r < rounds; r++) {
			for (int i = 0; i < ids.length; i++) {
				decide(i, out);
			}
		}
	}

	@Test
	void averageDecisionIsUnderFiveMicroseconds() {
		SoundDecision out = new SoundDecision();
		decideAll(out, 2_000); // warm up the JIT and the per-ID caches
		int rounds = 1_000;
		long start = System.nanoTime();
		decideAll(out, rounds);
		double perSound = (System.nanoTime() - start) / (double) (rounds * ids.length);
		System.out.printf("Stress profile: %.0f ns per sound decision (budget %.0f ns)%n", perSound, BUDGET_NANOS);
		assertTrue(perSound < BUDGET_NANOS, "average " + perSound + " ns per sound");
	}

	@Test
	void stressProfileReallyExercisesTheZones() {
		// Guard against a profile that never reaches the zone code: most sounds should be decided by a zone.
		SoundDecision out = new SoundDecision();
		int byZone = 0;
		for (int i = 0; i < ids.length; i++) {
			decide(i, out);
			if (out.loudnessZone() != null) {
				byZone++;
			}
		}
		assertTrue(byZone > ids.length / 2, byZone + " of " + ids.length);
	}

	@Test
	void hotPathAllocatesNothingInSteadyState() {
		SoundDecision out = new SoundDecision();
		RecentSounds recent = new RecentSounds();
		LastPlays lastPlays = new LastPlays();
		TimingWindow timing = new TimingWindow();

		// Warm up: every ID seen once, so caches and rows exist, and the JIT has compiled the loop.
		for (int round = 0; round < 2_000; round++) {
			soundStarts(out, recent, lastPlays, timing, round);
		}
		long allocated = soundsculptor.core.Allocations.fewestBytes(pass -> {
			for (int round = 0; round < 200; round++) {
				soundStarts(out, recent, lastPlays, timing, 2_000 + pass * 200 + round);
			}
		});
		assertEquals(0, allocated, "bytes allocated on the hot path for " + 200 * ids.length + " sound starts");
	}

	/** What the sound hook does per sound start: decide, log, remember the play, time it. */
	private void soundStarts(SoundDecision out, RecentSounds recent, LastPlays lastPlays, TimingWindow timing, int round) {
		long now = 1_000L + round * 10L; // stays inside the 3-minute window, so no row expires
		for (int i = 0; i < ids.length; i++) {
			long t0 = System.nanoTime();
			decide(i, out);
			PlayStatus status = out.volumePercent() == 0 ? PlayStatus.MUTED : PlayStatus.PLAYING;
			String zoneName = out.loudnessZone() == null ? null : out.loudnessZone().name();
			recent.record(ids[i], "neutral", true, 6.0, true, status, zoneName, now);
			lastPlays.record(ids[i], true, WORLD, OVERWORLD, StressProfile.FEET_X, StressProfile.FEET_Y, StressProfile.FEET_Z,
					out.volumePercent(), false, false);
			timing.add(System.nanoTime() - t0, now);
		}
	}

	@Test
	void soundMixFitsTheRecentListAndLastPlayTable() {
		// Otherwise the allocation test would measure evictions, not steady state.
		List<String> ids = StressProfile.soundIds();
		assertTrue(ids.size() <= RecentSounds.MAX_IDS && ids.size() <= LastPlays.CAPACITY, "ids: " + ids.size());
	}
}
