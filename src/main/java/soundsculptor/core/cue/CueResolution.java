package soundsculptor.core.cue;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import soundsculptor.core.sets.PlaceRef;

/**
 * The cue settings in effect, and for each one the place that decided it: the smallest place in the order that sets it,
 * or Everywhere ({@link CueResolver}). The place names what the hotkey changes and where a setting comes from.
 *
 * @param sources the deciding place of each setting, in {@link CueField} order
 */
public record CueResolution(CueSettings settings, List<PlaceRef> sources) {
	/** Everywhere's defaults, all from Everywhere. */
	public static final CueResolution DEFAULT = everywhere(CueSettings.DEFAULT);

	public CueResolution {
		Objects.requireNonNull(settings, "settings");
		sources = List.copyOf(sources);
		if (sources.size() != CueField.values().length) {
			throw new IllegalArgumentException("one source per cue setting, was " + sources.size());
		}
	}

	/** These settings, each decided by Everywhere. */
	public static CueResolution everywhere(CueSettings settings) {
		return new CueResolution(settings, Collections.nCopies(CueField.values().length, PlaceRef.EVERYWHERE));
	}

	/** The place that decided {@code field}. */
	public PlaceRef source(CueField field) {
		return sources.get(field.ordinal());
	}
}
