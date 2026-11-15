package soundsculptor.core.recent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** FR-1.2: the row says the time since the last play in minutes; hovering it shows the exact time to the second. */
class TimeAgoTest {
	private static final long SECOND = 1000;
	private static final long MINUTE = 60 * SECOND;
	private static final long HOUR = 60 * MINUTE;

	@Test
	void underAMinuteIsOneLabel() {
		assertEquals(TimeAgo.of(0), TimeAgo.of(59 * SECOND + 999));
		assertEquals(TimeAgo.Unit.UNDER_MINUTE, TimeAgo.of(0).unit());
		assertEquals(TimeAgo.Unit.UNDER_MINUTE, TimeAgo.of(59 * SECOND + 999).unit());
	}

	@Test
	void aMinuteUpToAnHourCountsWholeMinutes() {
		assertEquals(new TimeAgo(TimeAgo.Unit.MINUTES, 1), TimeAgo.of(MINUTE));
		assertEquals(new TimeAgo(TimeAgo.Unit.MINUTES, 1), TimeAgo.of(MINUTE + 59 * SECOND));
		assertEquals(new TimeAgo(TimeAgo.Unit.MINUTES, 2), TimeAgo.of(2 * MINUTE));
		assertEquals(new TimeAgo(TimeAgo.Unit.MINUTES, 59), TimeAgo.of(HOUR - 1));
	}

	@Test
	void anHourOrMoreIsOneLabel() {
		assertEquals(TimeAgo.Unit.OVER_HOUR, TimeAgo.of(HOUR).unit());
		assertEquals(TimeAgo.of(HOUR), TimeAgo.of(5 * HOUR));
	}

	@Test
	void theLabelChangesOnlyAtMinuteBoundaries() {
		assertEquals(TimeAgo.of(MINUTE + SECOND), TimeAgo.of(MINUTE + 30 * SECOND));
		assertNotEquals(TimeAgo.of(MINUTE + 59 * SECOND), TimeAgo.of(2 * MINUTE));
	}

	@Test
	void aClockThatWentBackCountsAsJustNow() {
		assertEquals(TimeAgo.Unit.UNDER_MINUTE, TimeAgo.of(-5 * SECOND).unit());
		assertEquals(new TimeAgo.Exact(0, 0, 0), TimeAgo.exact(-5 * SECOND));
	}

	@Test
	void theExactTimeIsToTheSecond() {
		assertEquals(new TimeAgo.Exact(0, 0, 45), TimeAgo.exact(45 * SECOND + 999));
		assertEquals(new TimeAgo.Exact(0, 1, 23), TimeAgo.exact(MINUTE + 23 * SECOND));
		assertEquals(new TimeAgo.Exact(1, 2, 5), TimeAgo.exact(HOUR + 2 * MINUTE + 5 * SECOND));
		assertEquals(new TimeAgo.Exact(26, 0, 0), TimeAgo.exact(26 * HOUR));
	}

	@Test
	void oneMinuteIsSingular() {
		assertEquals("soundsculptor.sounds.ago.minute", TimeAgo.of(MINUTE).translationKey());
		assertEquals("soundsculptor.sounds.ago.minutes", TimeAgo.of(2 * MINUTE).translationKey());
		assertEquals("soundsculptor.sounds.ago.under_minute", TimeAgo.of(0).translationKey());
		assertEquals("soundsculptor.sounds.ago.over_hour", TimeAgo.of(HOUR).translationKey());
	}

	@Test
	void everyUnitHasATranslationKey() {
		for (TimeAgo.Unit unit : TimeAgo.Unit.values()) {
			assertTrue(unit.translationKey().startsWith("soundsculptor.sounds.ago."), unit.name());
		}
	}
}
