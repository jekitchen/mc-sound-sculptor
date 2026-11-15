package soundsculptor.core.zones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.Allocations;
import soundsculptor.core.stress.StressProfile;

import org.junit.jupiter.api.Test;

/**
 * PRD section 9.1: working out where you are with 64 zones must cost under 0.1 ms each time the feet block changes,
 * and nothing on ticks where it did not change; it allocates nothing.
 */
class WhereYouAreBudgetTest {
	private static final String WORLD = "world:stress";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final double BUDGET_NANOS = 100_000;

	private final Precedence precedence = new Precedence(StressProfile.everywhere(), StressProfile.zones(WORLD, OVERWORLD),
			StressProfile.library());
	private final WhereYouAre where = new WhereYouAre();

	/** A new feet block every call: walking across the pen and out of it, so every zone is tested each time. */
	private void step(int n) {
		where.update(precedence, WORLD, OVERWORLD, StressProfile.FEET_X + n % 40 - 20, StressProfile.FEET_Y, StressProfile.FEET_Z);
	}

	@Test
	void recomputingIsUnderTheBudget() {
		for (int n = 0; n < 20_000; n++) {
			step(n);
		}
		int steps = 2_000;
		double perStep = Double.MAX_VALUE;
		for (int batch = 0; batch < 5; batch++) {
			long start = System.nanoTime();
			for (int n = 0; n < steps; n++) {
				step(20_000 + batch * steps + n);
			}
			perStep = Math.min(perStep, (System.nanoTime() - start) / (double) steps);
		}
		System.out.printf("Where you are: %.0f ns per feet block change with 64 zones (budget %.0f ns)%n", perStep, BUDGET_NANOS);
		assertTrue(perStep < BUDGET_NANOS, "average " + perStep + " ns");
	}

	@Test
	void recomputingAllocatesNothing() {
		for (int n = 0; n < 20_000; n++) {
			step(n);
		}
		long allocated = Allocations.fewestBytes(round -> {
			for (int n = 0; n < 1_000; n++) {
				step(round * 1_000 + n);
			}
		});
		assertEquals(0, allocated);
	}

	@Test
	void standingStillChangesNothing() {
		step(20);
		long version = where.version();
		for (int n = 0; n < 1_000; n++) {
			where.update(precedence, WORLD, OVERWORLD, StressProfile.FEET_X, StressProfile.FEET_Y, StressProfile.FEET_Z);
		}
		assertEquals(version, where.version());
		assertEquals(Precedence.MAX_ZONES, where.count());
	}
}
