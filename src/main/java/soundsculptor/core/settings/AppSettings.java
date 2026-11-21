package soundsculptor.core.settings;

import soundsculptor.core.cue.PlaceCues;
import java.util.Objects;

/**
 * App-wide settings: the same on every server and world (PRD FR-11: "Keybinds and UI preferences are app-wide, not per
 * profile"). Profile settings (outline mode, show distance) live in the profile file instead, and cue settings belong to
 * each profile's places, Everywhere and its zones (FR-7, decided 2026-11-21).
 *
 * @param outlinesThroughBlocks FR-10.5 "Outlines visible through blocks"
 * @param reducedMotion         A-6 no list animations and no toast slide-in
 * @param lastTab               FR-12.2 the tab the hotkey opens, e.g. "sounds"; see {@link #isValidTab}
 * @param legacyCues            the cue settings this file held when they were app-wide (before 2026-11-21), or
 *                              {@code null}: Everywhere of a profile that has none of its own yet starts from them, so a
 *                              player's chosen color, size and position carry over to every world as before. "New mutes
 *                              get Cue" was never app-wide, so it is always Unchanged here.
 */
public record AppSettings(boolean outlinesThroughBlocks, boolean reducedMotion, String lastTab, PlaceCues legacyCues) {
	public static final String DEFAULT_TAB = "sounds";
	static final int MAX_TAB_LENGTH = 32;

	public static final AppSettings DEFAULTS = new AppSettings(true, false, DEFAULT_TAB, null);

	public AppSettings {
		Objects.requireNonNull(lastTab, "lastTab");
		if (!isValidTab(lastTab)) {
			throw new IllegalArgumentException("Not a tab name: " + lastTab);
		}
		if (legacyCues != null) {
			legacyCues = legacyCues.withNewMutesGetCue(null);
		}
	}

	/** Settings without old app-wide cue settings. */
	public AppSettings(boolean outlinesThroughBlocks, boolean reducedMotion, String lastTab) {
		this(outlinesThroughBlocks, reducedMotion, lastTab, null);
	}

	/** A tab name is 1 to 32 characters of a-z, 0-9 and _. */
	public static boolean isValidTab(String tab) {
		if (tab == null || tab.isEmpty() || tab.length() > MAX_TAB_LENGTH) {
			return false;
		}
		for (int i = 0; i < tab.length(); i++) {
			char c = tab.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
				return false;
			}
		}
		return true;
	}

	public AppSettings withOutlinesThroughBlocks(boolean value) {
		return new AppSettings(value, reducedMotion, lastTab, legacyCues);
	}

	public AppSettings withReducedMotion(boolean value) {
		return new AppSettings(outlinesThroughBlocks, value, lastTab, legacyCues);
	}

	/** @throws IllegalArgumentException if {@code tab} is not a {@linkplain #isValidTab tab name} */
	public AppSettings withLastTab(String tab) {
		return new AppSettings(outlinesThroughBlocks, reducedMotion, tab, legacyCues);
	}

	public AppSettings withLegacyCues(PlaceCues value) {
		return new AppSettings(outlinesThroughBlocks, reducedMotion, lastTab, value);
	}

	/** Whether {@code setting} has its default value; the reset button is disabled then (FR-12.6). */
	public boolean isDefault(AppSetting setting) {
		return equals(reset(setting));
	}

	/** Whether every setting on {@code page} has its default value; "Reset page" is disabled then. */
	public boolean isPageDefault(SettingsPage page) {
		return equals(resetPage(page));
	}

	/** {@code setting} back to its default; every other value is kept (FR-12.6). */
	public AppSettings reset(AppSetting setting) {
		AppSettings d = DEFAULTS;
		return switch (setting) {
			case OUTLINES_THROUGH_BLOCKS -> withOutlinesThroughBlocks(d.outlinesThroughBlocks);
			case REDUCED_MOTION -> withReducedMotion(d.reducedMotion);
			case LAST_TAB -> withLastTab(d.lastTab);
		};
	}

	/** Every setting on {@code page} back to its default ("Reset page", FR-12.6). The last used tab is never reset. */
	public AppSettings resetPage(SettingsPage page) {
		AppSettings result = this;
		for (AppSetting setting : AppSetting.values()) {
			if (setting.page() == page) {
				result = result.reset(setting);
			}
		}
		return result;
	}
}
