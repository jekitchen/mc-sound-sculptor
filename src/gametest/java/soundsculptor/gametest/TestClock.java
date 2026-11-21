package soundsculptor.gametest;

/**
 * The time sound starts are decided with in game tests ({@code mixin.LimiterClock}): real time unless a test sets
 * {@link #fixedNow}. Render thread only. A test that sets it must clear the repeat-limit counters when it puts real time
 * back, since real time is then behind the times the counters hold.
 */
public final class TestClock {
	/** The time every sound start is decided at, in milliseconds; {@code null} for real time. */
	public static Long fixedNow;
	/** The time the last sound start was decided at. */
	public static long lastDecided;

	private TestClock() {
	}
}
