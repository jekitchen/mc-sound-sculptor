package soundsculptor.mc;

import soundsculptor.core.outline.OutlineMode;

/** Player settings (PRD 6.1, Settings tab), stored in the profile file (FR-11). */
public final class SoundSculptorSettings {
	private SoundSculptorSettings() {
	}

	/** FR-1.4 "Show distance" (default on): show the distance band in the recently played list. */
	/** FR-10.1 "Show zone outlines" (a profile setting). */
	public static OutlineMode outlineMode() {
		return ProfileManager.profile().settings().outlines();
	}

	public static void setOutlineMode(OutlineMode value) {
		ProfileManager.update(profile -> profile.withSettings(profile.settings().withOutlines(value)));
	}

	/** The outlines hotkey: Always, Only while editing, Never, then Always again. */
	public static void cycleOutlineMode() {
		OutlineMode[] modes = OutlineMode.values();
		setOutlineMode(modes[(outlineMode().ordinal() + 1) % modes.length]);
	}

	/** FR-10.5 "Outlines visible through blocks" (an app-wide setting). */
	public static boolean outlinesThroughBlocks() {
		return AppSettingsManager.get().outlinesThroughBlocks();
	}

	public static void setOutlinesThroughBlocks(boolean value) {
		AppSettingsManager.update(settings -> settings.withOutlinesThroughBlocks(value));
	}

	public static boolean showDistance() {
		return ProfileManager.profile().settings().showDistance();
	}

	public static void setShowDistance(boolean value) {
		ProfileManager.update(profile -> profile.withSettings(profile.settings().withShowDistance(value)));
	}

	/** FR-7.7 "Show muted-sound cues" (app-wide; default on). */
	public static boolean showCues() {
		return AppSettingsManager.get().showCues();
	}

	public static void setShowCues(boolean value) {
		AppSettingsManager.update(settings -> settings.withShowCues(value));
	}

	/** FR-7.2 "Cue sounds without subtitles" (app-wide; default off): such a cue shows the sound ID. */
	public static boolean cuesWithoutSubtitles() {
		return AppSettingsManager.get().cuesWithoutSubtitles();
	}

	public static void setCuesWithoutSubtitles(boolean value) {
		AppSettingsManager.update(settings -> settings.withCuesWithoutSubtitles(value));
	}

	/** FR-7.3, A-7 the cue text color, {@code 0xRRGGBB} (app-wide). */
	public static int cueColor() {
		return AppSettingsManager.get().cueColor();
	}

	public static void setCueColor(int rgb) {
		AppSettingsManager.update(settings -> settings.withCueColor(rgb));
	}

	/** FR-7.6 "New mutes get Cue" (a profile setting; default on). */
	public static boolean cueDefault() {
		return ProfileManager.profile().settings().cueDefault();
	}

	public static void setCueDefault(boolean value) {
		ProfileManager.update(profile -> profile.withSettings(profile.settings().withCueDefault(value)));
	}
}
