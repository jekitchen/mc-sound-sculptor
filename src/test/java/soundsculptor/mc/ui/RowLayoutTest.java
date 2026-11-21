package soundsculptor.mc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** How a list row fits a narrow width (PRD 9.3, 11.5): optional text hides, columns wrap, then buttons go below. */
class RowLayoutTest {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/edit");

	/** 6 px a character, and lines broken at spaces: enough to check wrapping without the game's font. */
	private static final SculptList.Row.Measure SIX_PX = new SculptList.Row.Measure() {
		@Override
		public int width(String text) {
			return 6 * text.length();
		}

		@Override
		public List<String> split(String text, int width) {
			List<String> lines = new ArrayList<>();
			StringBuilder line = new StringBuilder();
			for (String word : text.split(" ")) {
				if (line.length() > 0 && 6 * (line.length() + 1 + word.length()) > width) {
					lines.add(line.toString());
					line.setLength(0);
				}
				line.append(line.length() > 0 ? " " : "").append(word);
			}
			lines.add(line.toString());
			return lines;
		}
	};

	@org.junit.jupiter.api.BeforeEach
	void measureBySixPixels() {
		SculptList.Row.measure = SIX_PX;
	}

	private static SculptList.Row.Column rest(String text, int line) {
		return new SculptList.Row.Column(() -> text, 0, line);
	}

	private static SculptList.Row.Column fixed(String text, int width, int line) {
		return new SculptList.Row.Column(() -> text, width, line);
	}

	private static List<SculptButton> icons(int count) {
		List<SculptButton> buttons = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			buttons.add(SculptButton.icon(ICON, Component.literal("Button " + i), () -> { }));
		}
		return buttons;
	}

	private static SculptList.Row.Placed placed(SculptList.Row.Layout layout, String text) {
		for (SculptList.Row.Placed placed : layout.columns()) {
			if (placed.column().text().get().equals(text)) {
				return placed;
			}
		}
		throw new AssertionError(text + " is not shown");
	}

	@Test
	void aReadOnlyRowWrapsInsteadOfCutting() {
		// Diagnostics: one line of 60 characters (360 px) in a 200 px row with no buttons.
		String text = "Time per sound: average 12.5 us, max 40.0 us (peak 98.0 us)";
		SculptList.Row row = new SculptList.Row("k", List.of(rest(text, 0)), List.of());
		SculptList.Row.Layout layout = row.layout(0, 200);
		assertTrue(layout.lines() >= 2, "it wraps: " + layout);
		StringBuilder joined = new StringBuilder();
		for (SculptList.Row.Placed placed : layout.columns()) {
			assertTrue(6 * placed.text().length() <= placed.width(), "no line is cut: " + placed.text());
			joined.append(joined.length() > 0 ? " " : "").append(placed.text());
		}
		assertEquals(text, joined.toString());
		assertEquals(24 + Math.max(0, layout.lines() - 2) * SculptList.Row.EXTRA_LINE_HEIGHT, row.heightFor(200));
	}

	@Test
	void aRowWithButtonsStillCutsItsLongText() {
		SculptList.Row row = new SculptList.Row("k", List.of(rest("minecraft:entity.villager.ambient.long.name", 0)), icons(1));
		assertEquals(1, row.layout(0, 200).lines());
	}

	@Test
	void aKeptHeightHidesTheLinesItHasNoRoomFor() {
		// Three lines in a narrow row; a held list keeps it at its old 24 px, so the third line hides until the hold ends.
		SculptList.Row row = new SculptList.Row("k", List.of(rest("name", 0), fixed("Off", 36, 0), rest("details", 1)), List.of());
		assertEquals(36, row.heightFor(122));
		row.keepHeight(24);
		assertEquals(24, row.heightFor(122));
		SculptList.Row.Layout kept = row.layout(0, 122);
		assertEquals(2, kept.lines());
		assertEquals(List.of("Off"), kept.hidden().stream().map(c -> c.text().get()).toList());
		row.keepHeight(0);
		assertEquals(36, row.heightFor(122));
	}

	@Test
	void aWideRowKeepsItsTwoLines() {
		SculptList.Row row = new SculptList.Row("k", List.of(rest("name", 0), fixed("Off", 40, 0), rest("details", 1)), icons(2));
		SculptList.Row.Layout layout = row.layout(0, 400);
		assertEquals(2, layout.lines());
		assertTrue(layout.fits());
		assertFalse(layout.buttonsBelow());
		assertEquals(0, placed(layout, "Off").line());
		assertEquals(24, row.heightFor(400));
	}

	@Test
	void aFixedColumnThatSqueezesTheNameMovesToTheNextLine() {
		// A 122 px row (a gap at each end): the name would get 74 px beside "Off", under the 80 px a name keeps.
		SculptList.Row row = new SculptList.Row("k", List.of(rest("name", 0), fixed("Off", 36, 0), rest("details", 1)), List.of());
		SculptList.Row.Layout layout = row.layout(0, 122);
		assertTrue(layout.fits());
		assertEquals(122 - 2 * Theme.GAP, placed(layout, "name").width());
		// "details" cannot share a line with "Off" either, so "Off" goes on a third line, and the row grows for it.
		assertEquals(3, layout.lines());
		assertEquals(2, placed(layout, "Off").line());
		assertEquals(24 + SculptList.Row.EXTRA_LINE_HEIGHT, row.heightFor(122));
	}

	@Test
	void aWrappedColumnJoinsTheNextLineWhenThereIsRoom() {
		SculptList.Row row = new SculptList.Row("k", List.of(rest("name", 0), fixed("Joined", 100, 0), fixed("2 zones", 60, 1)), List.of());
		SculptList.Row.Layout layout = row.layout(0, 170);
		assertEquals(2, layout.lines());
		assertEquals(1, placed(layout, "Joined").line());
		assertEquals(170 - 2 * Theme.GAP, placed(layout, "name").width());
	}

	@Test
	void optionalTextHidesFirstAndOnlyWhenNarrow() {
		List<SculptList.Row.Column> columns = List.of(rest("Server", 0), rest("play.example.net", 1).hiddenWhenNarrow(),
				fixed("2 Sound Sets", 120, 1));
		SculptList.Row wide = new SculptList.Row("k", columns, List.of());
		assertTrue(wide.layout(0, 400).hidden().isEmpty());
		SculptList.Row.Layout narrow = new SculptList.Row("k", columns, List.of()).layout(0, 180);
		assertEquals(1, narrow.hidden().size());
		assertEquals("play.example.net", narrow.hidden().get(0).text().get());
		assertEquals(2, narrow.lines());
		assertTrue(narrow.fits());
	}

	@Test
	void buttonsGoBelowTheTextWhenTheNameAloneIsTooNarrow() {
		// Five 20 px buttons leave 76 px of a 180 px row: too little even for the name alone.
		SculptList.Row row = new SculptList.Row("k", List.of(rest("name", 0), rest("minecraft:entity.cow.ambient", 1)), icons(5));
		SculptList.Row.Layout layout = row.layout(0, 180);
		assertTrue(layout.buttonsBelow());
		assertTrue(layout.fits());
		assertEquals(180 - Theme.GAP, placed(layout, "name").width());
		assertEquals(2, layout.lines());
		// Two text lines, then the buttons' own line.
		assertEquals(1 + 2 * SculptList.Row.LINE_STEP + 1, layout.buttonsTop());
		assertEquals(4 + layout.buttonsTop() + Theme.WIDGET_HEIGHT, row.heightFor(180));
	}
}
