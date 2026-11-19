package soundsculptor.core.browser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Holding the Sounds list while the mouse pointer is over it (PRD FR-1.3), so a row does not move away just as the
 * player aims at it. A held refresh keeps the rows on screen in their order, each swapped for its fresh copy so its
 * values (plays, status, time) still update; a row that left the fresh list stays; a row new to it waits, and is
 * only counted. An empty list is never held: there is nothing to aim at, and holding it would hide the first sounds.
 */
public final class HoldOrder {
	private HoldOrder() {
	}

	/**
	 * @param rows    what to show
	 * @param waiting how many fresh rows are not shown yet
	 */
	public record Held<T>(List<T> rows, int waiting) {
	}

	/**
	 * @param shown the rows on screen, in their order
	 * @param fresh the rows a normal refresh would show
	 * @param key   a row's identity (unique within each list)
	 */
	public static <T> Held<T> hold(List<T> shown, List<T> fresh, Function<T, ?> key) {
		if (shown.isEmpty()) {
			return new Held<>(fresh, 0);
		}
		Map<Object, T> freshByKey = new HashMap<>(fresh.size() * 2);
		for (T row : fresh) {
			freshByKey.put(key.apply(row), row);
		}
		List<T> rows = new ArrayList<>(shown.size());
		Set<Object> shownKeys = new HashSet<>(shown.size() * 2);
		for (T row : shown) {
			Object k = key.apply(row);
			shownKeys.add(k);
			rows.add(freshByKey.getOrDefault(k, row));
		}
		int waiting = 0;
		for (Object k : freshByKey.keySet()) {
			if (!shownKeys.contains(k)) {
				waiting++;
			}
		}
		return new Held<>(rows, waiting);
	}
}
