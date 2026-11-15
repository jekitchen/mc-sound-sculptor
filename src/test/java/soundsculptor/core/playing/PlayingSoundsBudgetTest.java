package soundsculptor.core.playing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.Allocations;
import soundsculptor.core.stress.StressProfile;
import soundsculptor.core.zones.Precedence;
import soundsculptor.core.zones.WhereYouAre;

import org.junit.jupiter.api.Test;

/**
 * PRD section 9.1: when you enter or leave a zone, re-deciding the playing looping sounds must cost under 0.2 ms, and
 * allocate nothing. Measured at the worst case: the stress profile (50 entries on Everywhere, 64 zones that all contain
 * the player), as many sounds as vanilla can play at once, all of them looping, and the player stepping in and out of
 * all 64 zones on every event, so nothing can be skipped.
 */
class PlayingSoundsBudgetTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";
	/** Vanilla's channel limit: 247 normal plus 8 streaming. */
	private static final int SOUNDS = 255;
	private static final double BUDGET_NANOS = 200_000;

	private final Precedence precedence = new Precedence(StressProfile.everywhere(), StressProfile.zones(WORLD, OVERWORLD),
			StressProfile.library());
	private final String[] ids = StressProfile.soundIds().toArray(new String[0]);
	private final PlayingSounds playing = new PlayingSounds();
	private final PlayingSounds.GainListener listener = (handle, gain) -> { };
	private final WhereYouAre where = new WhereYouAre();

	PlayingSoundsBudgetTest() {
		for (int i = 0; i < SOUNDS; i++) {
			playing.start(i, ids[i % ids.length], true, 1F, 1F);
		}
	}

	/** One event: the player steps into all 64 zones, or out of them, and the loops are re-decided. */
	private void event(int n) {
		int x = n % 2 == 0 ? StressProfile.FEET_X : 1_000;
		where.update(precedence, WORLD, OVERWORLD, x, StressProfile.FEET_Y, StressProfile.FEET_Z);
		playing.update(precedence, where, false, true, listener);
	}

	@Test
	void reDecidingEveryLoopIsUnderTheBudget() {
		for (int n = 0; n < 2_000; n++) {
			event(n); // warm up the JIT and the per-ID caches
		}
		// Best of five batches: a shared CI machine can pause the test (another job, GC), which would inflate a single
		// average. A slow implementation is slow in every batch, so this still catches one.
		int events = 200;
		double perEvent = Double.MAX_VALUE;
		for (int batch = 0; batch < 5; batch++) {
			long start = System.nanoTime();
			for (int n = 0; n < events; n++) {
				event(2_000 + batch * events + n);
			}
			perEvent = Math.min(perEvent, (System.nanoTime() - start) / (double) events);
		}
		System.out.printf("Playing loops: %.0f ns per enter or leave for %d loops (budget %.0f ns)%n", perEvent, SOUNDS,
				BUDGET_NANOS);
		assertTrue(perEvent < BUDGET_NANOS, "average " + perEvent + " ns per event");
	}

	@Test
	void reDecidingAllocatesNothingInSteadyState() {
		for (int n = 0; n < 2_000; n++) {
			event(n);
		}
		long allocated = Allocations.fewestBytes(round -> {
			for (int n = 0; n < 100; n++) {
				event(2_000 + round * 100 + n);
			}
		});
		assertEquals(0, allocated, "bytes allocated re-deciding " + SOUNDS + " playing loops");
	}

	@Test
	void aQuietTickCostsNoDecision() {
		event(0);
		long decisions = playing.decisionCount();
		for (int t = 0; t < 100; t++) {
			where.update(precedence, WORLD, OVERWORLD, StressProfile.FEET_X, StressProfile.FEET_Y, StressProfile.FEET_Z);
			playing.update(precedence, where, false, true, listener);
		}
		assertEquals(decisions, playing.decisionCount());
	}
}
