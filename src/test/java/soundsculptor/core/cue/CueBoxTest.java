package soundsculptor.core.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRD FR-7.1, FR-7.4 and FR-7.5: cue lines merge, are capped at 5, fade after 3 seconds and keep where they played. */
class CueBoxTest {
	private final CueBox<String> box = new CueBox<>();

	private List<CueLine<String>> lines(long nowMs) {
		return box.visibleLines(nowMs);
	}

	/** A cue 10 blocks east of the origin, in range. */
	private void add(String text, long nowMs) {
		box.add(text, true, 10, 0, 0, 16, nowMs);
	}

	@Test
	void newCueAppearsAsOneLine() {
		box.add("Explosion", true, -10, 64, 3, 64, 1000);
		List<CueLine<String>> lines = lines(1000);
		assertEquals(1, lines.size());
		CueLine<String> line = lines.get(0);
		assertEquals("Explosion", line.text());
		assertEquals(1, line.count());
		assertTrue(line.positional());
		assertEquals(-10, line.x());
		assertEquals(64, line.y());
		assertEquals(3, line.z());
		assertEquals(64, line.range());
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
	void aRepeatRefreshesTheLineAndTakesTheNewPosition() {
		box.add("Villager mumbles", true, -5, 0, 0, 16, 1000);
		box.add("Villager mumbles", true, 5, 0, 0, 16, 3500);
		CueLine<String> line = lines(3600).get(0);
		assertEquals(5, line.x());
		assertEquals(100, line.ageMs(), "age counts from the latest repeat");
	}

	@Test
	void linesFadeAfterThreeSeconds() {
		add("Explosion", 1000);
		assertEquals(1, lines(1000 + CueBox.DISPLAY_MS - 1).size());
		assertFalse(box.isEmpty(1000 + CueBox.DISPLAY_MS - 1));
		assertTrue(lines(1000 + CueBox.DISPLAY_MS).isEmpty());
		assertTrue(box.isEmpty(1000 + CueBox.DISPLAY_MS));
	}

	@Test
	void countRestartsAfterALineHasFaded() {
		add("Explosion", 1000);
		add("Explosion", 1000 + CueBox.DISPLAY_MS + 10);
		assertEquals(1, lines(1000 + CueBox.DISPLAY_MS + 20).get(0).count());
	}

	@Test
	void atMostFiveLinesAndTheOldestGoesFirst() {
		for (int i = 0; i < 7; i++) {
			add("Sound " + i, 1000 + i);
		}
		List<CueLine<String>> lines = lines(1010);
		assertEquals(CueBox.MAX_LINES, lines.size());
		assertEquals(List.of("Sound 6", "Sound 5", "Sound 4", "Sound 3", "Sound 2"), lines.stream().map(CueLine::text).toList());
	}

	@Test
	void tenMutedVillagersAtOnceStayOnOneCountedLine() {
		// FR-7 acceptance check: no more than 5 lines, with counts.
		for (int i = 0; i < 10; i++) {
			box.add("Villager mumbles", true, i, 0, 0, 16, 1000);
		}
		add("Villager hurts", 1000);
		List<CueLine<String>> lines = lines(1000);
		assertEquals(2, lines.size());
		assertEquals(10, lines.stream().filter(l -> l.text().equals("Villager mumbles")).findFirst().orElseThrow().count());
	}

	@Test
	void newestLineIsFirst() {
		add("A", 1000);
		add("B", 1100);
		add("A", 1200);
		assertEquals(List.of("A", "B"), lines(1300).stream().map(CueLine::text).toList());
	}

	@Test
	void fadeFractionGoesFromZeroToOne() {
		add("Explosion", 1000);
		assertEquals(0.0, lines(1000).get(0).fadeFraction(), 1e-9);
		assertEquals(0.5, lines(1000 + CueBox.DISPLAY_MS / 2).get(0).fadeFraction(), 1e-9);
	}

	@Test
	void aLineIsInRangeOnlyWhileTheListenerIsStrictlyCloserThanTheRange() {
		// FR-7.1: the same check vanilla's subtitle overlay makes every frame.
		box.add("Villager mumbles", true, 16, 0, 0, 16, 1000);
		CueLine<String> line = lines(1000).get(0);
		assertTrue(line.audibleFrom(0.5, 0, 0));
		assertFalse(line.audibleFrom(0, 0, 0), "exactly at the range is out of range, like Vec3.closerThan");
		assertFalse(line.audibleFrom(-9, 0, 0), "a listener who walked away no longer sees the line");
	}

	@Test
	void soundsWithoutAPositionOrRangeAreAlwaysInRange() {
		box.add("Music", false, 0, 0, 0, Float.POSITIVE_INFINITY, 1000);
		box.add("Thunder", true, 0, 0, 0, Float.POSITIVE_INFINITY, 1000);
		for (CueLine<String> line : lines(1000)) {
			assertTrue(line.audibleFrom(1e6, 0, 1e6), line.text());
		}
	}

	@Test
	void theArrowFollowsTheListenerLikeVanilla() {
		box.add("Explosion", true, 10, 0, 0, 64, 1000);
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
		box.add("Music", false, 10, 0, 0, Float.POSITIVE_INFINITY, 1000);
		assertEquals(CueDirection.NONE, lines(1000).get(0).direction(0, 0, 0, 0, 0, -1, 1, 0, 0));
	}

	@Test
	void addingCuesAllocatesNothing() {
		// A muted farm can fire hundreds of cues a second (PRD 9.1: no allocation on the hot path).
		String[] texts = {"Villager mumbles", "Villager hurts", "Chicken clucks", "Cow moos", "Explosion", "Glass breaks", "Hmm"};
		for (int i = 0; i < 200_000; i++) {
			box.add(texts[i % texts.length], true, i, 0, 0, 16, i);
		}
		long allocated = soundsculptor.core.Allocations.fewestBytes(round -> {
			int start = 200_000 + round * 100_000;
			for (int i = 0; i < 100_000; i++) {
				box.add(texts[i % texts.length], true, i, 1, 2, 16, start + i);
			}
		});
		assertEquals(0, allocated);
	}

	@Test
	void clearRemovesEverything() {
		add("Explosion", 1000);
		box.clear();
		assertTrue(lines(1000).isEmpty());
		assertTrue(box.isEmpty(1000));
	}
}
