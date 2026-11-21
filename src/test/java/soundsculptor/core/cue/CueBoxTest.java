package soundsculptor.core.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PRD FR-7.1, FR-7.4 and FR-7.5: cue lines merge with a count, are capped at 5, track their plays like vanilla's
 * subtitle lines (best play in range, range check, arrow, fade) and last 3 seconds times Notification Time.
 */
class CueBoxTest {
	private static final double SCALE = 1.0;
	private final CueBox<String> box = new CueBox<>();

	/** The lines for a listener at the origin. */
	private List<CueLine<String>> lines(long nowMs) {
		return box.lines(nowMs, SCALE, 0, 0, 0);
	}

	private List<String> texts(List<CueLine<String>> lines) {
		return lines.stream().map(CueLine::text).toList();
	}

	/** A cue 10 blocks east of the origin, range 16. */
	private void add(String text, long nowMs) {
		box.add(text, true, false, 10, 0, 0, 16, nowMs, SCALE);
	}

	@Test
	void newCueAppearsAsOneLine() {
		box.add("Explosion", true, false, -10, 4, 3, 64, 1000, SCALE);
		List<CueLine<String>> lines = lines(1000);
		assertEquals(1, lines.size());
		CueLine<String> line = lines.get(0);
		assertEquals("Explosion", line.text());
		assertEquals(1, line.count());
		assertTrue(line.positional());
		assertEquals(-10, line.x());
		assertEquals(4, line.y());
		assertEquals(3, line.z());
	}

	@Test
	void repeatsMergeIntoOneLineWithACount() {
		for (int i = 0; i < 4; i++) {
			add("Villager mumbles", 1000 + i * 100);
		}
		List<CueLine<String>> lines = lines(1400);
		assertEquals(1, lines.size());
		assertEquals(4, lines.get(0).count());
	}

	@Test
	void repeatsMergeByEqualTextNotTheSameObject() {
		add(new String("Explosion"), 1000);
		add(new String("Explosion"), 1100);
		assertEquals(2, lines(1100).get(0).count());
	}

	@Test
	void theLatestPlayInRangeDecides() {
		box.add("Villager mumbles", true, false, -5, 0, 0, 16, 1000, SCALE);
		box.add("Villager mumbles", true, false, 5, 0, 0, 16, 1500, SCALE);
		assertEquals(5, lines(1600).get(0).x(), "the most recent play");
	}

	@Test
	void aLaterPlayOutOfRangeDoesNotHideTheLine() {
		// Vanilla picks the latest play still in range, so a far repeat does not hide a near one.
		box.add("Villager mumbles", true, false, 5, 0, 0, 16, 1000, SCALE);
		box.add("Villager mumbles", true, false, 40, 0, 0, 16, 1500, SCALE);
		List<CueLine<String>> lines = lines(1600);
		assertEquals(1, lines.size());
		assertEquals(5, lines.get(0).x());
		assertEquals(2, lines.get(0).count());
	}

	@Test
	void aPlayAtTheSamePlaceReplacesItsOldPlay() {
		box.add("Villager mumbles", true, false, 5, 0, 0, 16, 1000, SCALE);
		box.add("Villager mumbles", true, false, -5, 0, 0, 16, 2000, SCALE);
		box.add("Villager mumbles", true, false, 5, 0, 0, 16, 2500, SCALE);
		assertEquals(5, lines(2600).get(0).x(), "the play at x 5 is now the latest");
		assertEquals(0.0, lines(2500).get(0).fade(), 1e-9, "and it is new");
	}

	@Test
	void aLineFadesFromItsBestPlayLikeVanilla() {
		add("Explosion", 1000);
		assertEquals(0.0, lines(1000).get(0).fadeFraction(), 1e-9);
		assertEquals(0.5, lines(2500).get(0).fadeFraction(), 1e-9);
	}

	@Test
	void aLoopingLineStaysBrightForHalfASecond() {
		box.add("Minecart rolls", true, true, 3, 0, 0, 16, 1000, SCALE);
		assertEquals(0.0, lines(1500).get(0).fade(), 1e-9);
		assertEquals(0.5, lines(1500 + 1250).get(0).fade(), 1e-9, "then fades over the remaining 2.5 s");
	}

	@Test
	void linesLastThreeSecondsTimesNotificationTime() {
		add("Explosion", 1000);
		assertEquals(1, lines(1000 + CueBox.DISPLAY_MS).size(), "vanilla keeps a play while its age is at most the time");
		assertFalse(box.isEmpty(1000 + CueBox.DISPLAY_MS, SCALE));
		assertTrue(lines(1000 + CueBox.DISPLAY_MS + 1).isEmpty());
		assertTrue(box.isEmpty(1000 + CueBox.DISPLAY_MS + 1, SCALE));

		CueBox<String> slow = new CueBox<>();
		slow.add("Explosion", true, false, 10, 0, 0, 16, 1000, 2.0);
		assertEquals(1, slow.lines(1000 + 2 * CueBox.DISPLAY_MS, 2.0, 0, 0, 0).size(), "Notification Time 2 doubles it");
		assertEquals(0.5, slow.lines(1000 + CueBox.DISPLAY_MS, 2.0, 0, 0, 0).get(0).fade(), 1e-9);
	}

	@Test
	void countRestartsAfterALineHasGone() {
		add("Explosion", 1000);
		add("Explosion", 1000 + CueBox.DISPLAY_MS + 10);
		assertEquals(1, lines(1000 + CueBox.DISPLAY_MS + 20).get(0).count());
	}

	@Test
	void atMostFiveLinesAndTheOldestShownGoesFirst() {
		for (int i = 0; i < 7; i++) {
			add("Sound " + i, 1000 + i);
		}
		assertEquals(List.of("Sound 2", "Sound 3", "Sound 4", "Sound 5", "Sound 6"), texts(lines(1010)), "oldest line at the bottom");
	}

	@Test
	void aNewCueTakesAHiddenLinesSlotBeforeAShownOne() {
		// Two lines out of range, three in range; the box is full.
		box.add("Far 1", true, false, 40, 0, 0, 16, 1000, SCALE);
		box.add("Far 2", true, false, 41, 0, 0, 16, 1001, SCALE);
		add("Near 1", 900);
		add("Near 2", 1002);
		add("Near 3", 1003);
		assertEquals(List.of("Near 1", "Near 2", "Near 3"), texts(lines(1004)), "the far lines are hidden");
		add("Near 4", 1005);
		add("Near 5", 1006);
		assertEquals(List.of("Near 1", "Near 2", "Near 3", "Near 4", "Near 5"), texts(lines(1007)),
				"the new lines took the hidden lines' slots; Near 1, played longest ago, is still shown");
	}

	@Test
	void aLineOutOfRangeIsHiddenAndComesBackInRange() {
		// FR-7.1: the same check vanilla's subtitle overlay makes every frame.
		box.add("Villager mumbles", true, false, 16, 0, 0, 16, 1000, SCALE);
		assertEquals(1, box.lines(1000, SCALE, 0.5, 0, 0).size());
		assertTrue(box.lines(1000, SCALE, 0, 0, 0).isEmpty(), "exactly at the range is out of range, like Vec3.closerThan");
		assertEquals(1, box.lines(1000, SCALE, 10, 0, 0).size());
	}

	@Test
	void soundsWithoutAPositionOrRangeAreAlwaysShown() {
		box.add("Music", false, false, 0, 0, 0, Float.POSITIVE_INFINITY, 1000, SCALE);
		box.add("Thunder", true, false, 0, 0, 0, Float.POSITIVE_INFINITY, 1000, SCALE);
		assertEquals(2, box.lines(1000, SCALE, 1e6, 0, 1e6).size());
	}

	@Test
	void tenMutedVillagersAtOnceStayOnOneCountedLine() {
		// FR-7 acceptance check: no more than 5 lines, with counts.
		for (int i = 0; i < 10; i++) {
			box.add("Villager mumbles", true, false, i, 0, 0, 16, 1000, SCALE);
		}
		add("Villager hurts", 1000);
		List<CueLine<String>> lines = lines(1000);
		assertEquals(2, lines.size());
		assertEquals(10, lines.get(0).count());
	}

	@Test
	void theArrowFollowsTheListenerLikeVanilla() {
		box.add("Explosion", true, false, 10, 0, 0, 64, 1000, SCALE);
		CueLine<String> line = lines(1000).get(0);
		// Facing north (-z), east (+x) is to the right.
		assertEquals(CueDirection.RIGHT, line.direction(0, 0, 0, 0, 0, -1, 1, 0, 0));
		// Facing south (+z), east is to the left.
		assertEquals(CueDirection.LEFT, line.direction(0, 0, 0, 0, 0, 1, -1, 0, 0));
		// Facing east, the sound is in front: no arrow.
		assertEquals(CueDirection.NONE, line.direction(0, 0, 0, 1, 0, 0, 0, 0, 1));
	}

	@Test
	void aSoundWithoutAPositionHasNoArrow() {
		box.add("Music", false, false, 10, 0, 0, Float.POSITIVE_INFINITY, 1000, SCALE);
		assertEquals(CueDirection.NONE, lines(1000).get(0).direction(0, 0, 0, 0, 0, -1, 1, 0, 0));
	}

	@Test
	void addingCuesAllocatesNothing() {
		// A muted farm can fire hundreds of cues a second (PRD 9.1: no allocation on the hot path).
		String[] texts = {"Villager mumbles", "Villager hurts", "Chicken clucks", "Cow moos", "Explosion", "Glass breaks", "Hmm"};
		for (int i = 0; i < 200_000; i++) {
			box.add(texts[i % texts.length], true, false, i % 13, 0, 0, 16, i, SCALE);
		}
		long allocated = soundsculptor.core.Allocations.fewestBytes(round -> {
			int start = 200_000 + round * 100_000;
			for (int i = 0; i < 100_000; i++) {
				box.add(texts[i % texts.length], true, false, i % 13, 1, 2, 16, start + i, SCALE);
			}
		});
		assertEquals(0, allocated);
	}

	@Test
	void clearRemovesEverything() {
		add("Explosion", 1000);
		box.clear();
		assertTrue(lines(1000).isEmpty());
		assertTrue(box.isEmpty(1000, SCALE));
	}
}
