package soundsculptor.mc.ui;

/**
 * UI settings the kit reads. The app-wide settings ({@code AppSettingsManager}) set them when they load and change; the
 * UI kit demo screen's toggle sets them for its own test.
 */
public final class UiSettings {
	private UiSettings() {
	}

	/** PRD A-6 (Settings › Accessibility › Reduced motion): toasts appear in place instead of sliding in. */
	public static volatile boolean reducedMotion;
}
