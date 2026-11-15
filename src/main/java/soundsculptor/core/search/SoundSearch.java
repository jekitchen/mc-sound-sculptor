package soundsculptor.core.search;

import java.util.ArrayList;
import java.util.List;

/** Runs a {@link SearchQuery} over every known sound ID (PRD FR-3.1). Every sound is in scope (section 5). */
public final class SoundSearch {
	private SoundSearch() {
	}

	/**
	 * @param all   every known sound, in display order
	 * @param query the parsed query
	 * @return matching sounds in input order; the size is the result count shown to the player (FR-3.4)
	 */
	public static List<SoundEntry> search(List<SoundEntry> all, SearchQuery query) {
		List<SoundEntry> result = new ArrayList<>();
		for (SoundEntry entry : all) {
			if (query.matches(entry)) {
				result.add(entry);
			}
		}
		return result;
	}
}
