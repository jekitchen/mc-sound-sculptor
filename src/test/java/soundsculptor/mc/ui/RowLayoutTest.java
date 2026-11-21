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
