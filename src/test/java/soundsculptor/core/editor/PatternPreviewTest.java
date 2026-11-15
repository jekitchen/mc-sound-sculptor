package soundsculptor.core.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import soundsculptor.core.sets.PatternError;

import org.junit.jupiter.api.Test;

/** PRD FR-4.3: while editing, a live preview shows "Matches N sounds" with an expandable list. */
class PatternPreviewTest {
	private static final List<String> KNOWN = List.of(
			"minecraft:entity.villager.hurt",
			"minecraft:entity.villager.ambient",
			"minecraft:entity.wandering_trader.ambient",
			"minecraft:block.stone.step",
			"somemod:block.marble.step",
			"minecraft:music.game",
			"minecraft:ui.button.click",
			"somemod:theme");

	@Test
	void countsAndListsMatchingSoundsInOrder() {
		PatternPreview p = PatternPreview.of("minecraft:entity.villager.*", KNOWN);
		assertNull(p.error());
		assertEquals(2, p.count());
		assertEquals(List.of("minecraft:entity.villager.ambient", "minecraft:entity.villager.hurt"), p.matches());
	}

	@Test
	void patternWithoutNamespaceCoversModdedSounds() {
		PatternPreview p = PatternPreview.of("block.*.step", KNOWN);
		assertEquals(List.of("minecraft:block.stone.step", "somemod:block.marble.step"), p.matches());
	}

	@Test
	void starMatchesEverySoundMusicAndUiIncluded() {
		// FR-4.2: * means every sound in scope, and every sound is in scope (section 5, 2026-11-14).
		PatternPreview p = PatternPreview.of("*", KNOWN);
		assertEquals(KNOWN.size(), p.count());
	}

	@Test
	void invalidPatternReportsItsErrorAndMatchesNothing() {
		PatternPreview p = PatternPreview.of("entity.cow.#", KNOWN);
		assertEquals(PatternError.INVALID_CHARACTER, p.error());
		assertEquals(0, p.count());
		assertEquals(PatternError.EMPTY, PatternPreview.of("  ", KNOWN).error());
	}

	@Test
	void inputIsNormalisedLikeTheSavedRule() {
		PatternPreview p = PatternPreview.of("Minecraft:Entity.Villager.*", KNOWN);
		assertEquals(2, p.count());
		assertEquals("minecraft:entity.villager.*", p.normalizedPattern());
	}

	@Test
	void duplicateKnownIdsAreCountedOnce() {
		PatternPreview p = PatternPreview.of("minecraft:block.*", List.of("minecraft:block.stone.step", "minecraft:block.stone.step"));
		assertEquals(1, p.count());
	}
}
