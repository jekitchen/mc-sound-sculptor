package soundsculptor.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** PRD FR-3.1: every sound is in scope (section 5, decided 2026-11-14), music and UI included. */
class SoundSearchTest {
	private static final SoundEntry MUSIC = new SoundEntry("minecraft:music.game", null);
	private static final SoundEntry UI_CLICK = new SoundEntry("minecraft:ui.button.click", null);
	private static final SoundEntry DISC = new SoundEntry("minecraft:music_disc.cat", "Music Disc - cat");
	private static final SoundEntry COW = new SoundEntry("minecraft:entity.cow.ambient", "Cow moos");

	@Test
	void musicAndUiSoundsAreFound() {
		List<SoundEntry> all = List.of(MUSIC, UI_CLICK, DISC, COW);
		assertEquals(all, SoundSearch.search(all, SearchQuery.parse("")));
		assertEquals(List.of(MUSIC), SoundSearch.search(all, SearchQuery.parse("music.game")));
		assertEquals(List.of(UI_CLICK), SoundSearch.search(all, SearchQuery.parse("ui.button")));
	}

	@Test
	void searchAppliesTheQueryAndKeepsInputOrder() {
		List<SoundEntry> all = List.of(COW, DISC);
		assertEquals(List.of(DISC), SoundSearch.search(all, SearchQuery.parse("disc")));
		assertEquals(List.of(COW, DISC), SoundSearch.search(all, SearchQuery.parse("minecraft")));
	}
}
