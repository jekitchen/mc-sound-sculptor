package soundsculptor.core.settings;

/**
 * One app-wide setting, for its reset-to-default button (PRD FR-12.6) and the Settings page it is shown on. The cue
 * settings that were here moved to the places, Everywhere and each zone (FR-7, decided 2026-11-21).
 */
public enum AppSetting {
	/** FR-10.5 "Outlines visible through blocks". */
	OUTLINES_THROUGH_BLOCKS(SettingsPage.ZONES),
	/** A-6 "Reduced motion". */
	REDUCED_MOTION(SettingsPage.ACCESSIBILITY),
	/** FR-12.2: the hotkey opens the last used tab. Remembered, not shown on a page. */
	LAST_TAB(null);

	private final SettingsPage page;

	AppSetting(SettingsPage page) {
		this.page = page;
	}

	/** The page that shows this setting, or {@code null} if it is remembered rather than shown. */
	public SettingsPage page() {
		return page;
	}
}
