package soundsculptor.core.profile;

import soundsculptor.core.outline.OutlineMode;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Profile settings (PRD FR-11: outline mode, show distance). Keybinds and UI preferences are app-wide and live
 * elsewhere; cue settings belong to the places, Everywhere and each zone (FR-7, decided 2026-11-21).
 *
 * @param showDistance FR-1.4: show the distance band in the recently played list (default on)
 * @param outlines     FR-10.1: "Show zone outlines" (default only while editing)
 */
public record ProfileSettings(boolean showDistance, OutlineMode outlines) {
	public static final ProfileSettings DEFAULTS = new ProfileSettings(true, OutlineMode.DEFAULT);

	public ProfileSettings {
		Objects.requireNonNull(outlines, "outlines");
	}

	/** Only "show distance" set; every other setting at its default. */
	public ProfileSettings(boolean showDistance) {
		this(showDistance, OutlineMode.DEFAULT);
	}

	public ProfileSettings withShowDistance(boolean value) {
		return new ProfileSettings(value, outlines);
	}

	public ProfileSettings withOutlines(OutlineMode value) {
		return new ProfileSettings(showDistance, value);
	}

	/** Whether this value is at its default, so its reset button has nothing to do. */
	public boolean isDefault(ProfileSetting setting) {
		return switch (setting) {
			case SHOW_DISTANCE -> showDistance == DEFAULTS.showDistance;
			case OUTLINES -> outlines == DEFAULTS.outlines;
		};
	}

	/** The values that differ from their defaults. */
	public Set<ProfileSetting> changed() {
		Set<ProfileSetting> changed = EnumSet.noneOf(ProfileSetting.class);
		for (ProfileSetting setting : ProfileSetting.values()) {
			if (!isDefault(setting)) {
				changed.add(setting);
			}
		}
		return changed;
	}

	/** One value's reset-to-default button (PRD FR-12.6); the other values stay as they are. */
	public ProfileSettings reset(ProfileSetting setting) {
		return switch (setting) {
			case SHOW_DISTANCE -> withShowDistance(DEFAULTS.showDistance);
			case OUTLINES -> withOutlines(DEFAULTS.outlines);
		};
	}

	/** "Reset page": every profile setting back to its default (PRD FR-12.6). */
	public ProfileSettings resetPage() {
		return DEFAULTS;
	}
}
