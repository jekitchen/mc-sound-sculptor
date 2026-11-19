package soundsculptor.core.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRD FR-1.3: while the pointer is over the list, rows keep their place and new ones wait. */
class HoldOrderTest {
	private record R(String key, int value) {
	}

	private static HoldOrder.Held<R> hold(List<R> shown, List<R> fresh) {
		return HoldOrder.hold(shown, fresh, R::key);
	}

	@Test
	void keepsShownOrderWhenFreshReorders() {
		HoldOrder.Held<R> held = hold(List.of(new R("a", 1), new R("b", 1)), List.of(new R("b", 2), new R("a", 2)));
		assertEquals(List.of(new R("a", 2), new R("b", 2)), held.rows());
		assertEquals(0, held.waiting());
	}

	@Test
	void swapsInFreshCopiesSoValuesUpdate() {
		HoldOrder.Held<R> held = hold(List.of(new R("a", 1)), List.of(new R("a", 5)));
		assertEquals(5, held.rows().get(0).value());
	}

	@Test
	void keepsRowsThatLeft() {
		HoldOrder.Held<R> held = hold(List.of(new R("a", 1), new R("b", 1)), List.of(new R("b", 2)));
		assertEquals(List.of(new R("a", 1), new R("b", 2)), held.rows());
		assertEquals(0, held.waiting());
	}

	@Test
	void countsNewRowsAsWaitingWithoutShowingThem() {
		HoldOrder.Held<R> held = hold(List.of(new R("a", 1)), List.of(new R("c", 1), new R("a", 2), new R("d", 1)));
		assertEquals(List.of(new R("a", 2)), held.rows());
		assertEquals(2, held.waiting());
	}

	@Test
	void emptyShownListIsNotHeld() {
		List<R> fresh = List.of(new R("a", 1), new R("b", 1));
		HoldOrder.Held<R> held = hold(List.of(), fresh);
		assertEquals(fresh, held.rows());
		assertEquals(0, held.waiting());
	}
}
