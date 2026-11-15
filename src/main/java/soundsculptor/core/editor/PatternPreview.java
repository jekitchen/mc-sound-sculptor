package soundsculptor.core.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import soundsculptor.core.sets.PatternError;
import soundsculptor.core.sets.SoundPattern;

/**
 * The rule editor's live preview, "Matches N sounds" with an expandable list (PRD FR-4.3).
 *
 * <p>Uses the same ID set as search (FR-3.1), so Music, UI and Voice/Speech sounds count like any other (every sound
 * is in scope). Runs while the player types, over a few thousand IDs, so it is not on the sound hot path.
 *
 * @param normalizedPattern the pattern as it will be saved, or {@code null} when invalid
 * @param error             why the pattern is invalid, or {@code null}
 * @param matches           matching IDs in alphabetical order, without duplicates; empty when invalid
 */
public record PatternPreview(String normalizedPattern, PatternError error, List<String> matches) {
	public PatternPreview {
		matches = List.copyOf(matches);
	}

	/**
	 * @param raw             the text in the pattern field
	 * @param knownIds every sound ID the client knows (registry plus resource packs)
	 */
	public static PatternPreview of(String raw, Collection<String> knownIds) {
		PatternError error = SoundPattern.validate(raw);
		if (error != null) {
			return new PatternPreview(null, error, List.of());
		}
		SoundPattern pattern = SoundPattern.of(raw);
		TreeSet<String> found = new TreeSet<>();
		for (String id : knownIds) {
			if (pattern.matches(id)) {
				found.add(id);
			}
		}
		return new PatternPreview(pattern.text(), null, new ArrayList<>(found));
	}

	/** The N in "Matches N sounds". */
	public int count() {
		return matches.size();
	}
}
