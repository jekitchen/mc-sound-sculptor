package soundsculptor.core.cue;

import java.util.List;

import soundsculptor.core.settings.AppSettings;
import soundsculptor.core.ui.Palette;

/**
 * The cue text colors offered in Settings (PRD FR-7.3, A-7): the default light blue-gray and the light Okabe–Ito
 * colors, each with a name ("soundsculptor.color.&lt;id&gt;"). There is no free hex entry; a color set some other way
 * (an edited settings file) is kept and shown as a custom color. Each one keeps at least 6:1 contrast on the black
 * cue background, and none is vanilla's white, so a cue never looks like a subtitle even in color. Pure Java.
 */
public final class CueColors {
	/** The default first, then the Okabe–Ito colors that read well on a dark background. */
	public static final List<Palette.Swatch> PRESETS = List.of(
			new Palette.Swatch("light_blue_gray", AppSettings.DEFAULT_CUE_COLOR),
			new Palette.Swatch("sky_blue", 0x56B4E9),
			new Palette.Swatch("yellow", 0xF0E442),
			new Palette.Swatch("orange", 0xE69F00),
			new Palette.Swatch("reddish_purple", 0xCC79A7),
			new Palette.Swatch("bluish_green", 0x009E73));

	private CueColors() {
	}

	/** The preset with this color, or {@code null} for a custom color. */
	public static Palette.Swatch preset(int rgb) {
		for (Palette.Swatch swatch : PRESETS) {
			if (swatch.rgb() == (rgb & 0xFFFFFF)) {
				return swatch;
			}
		}
		return null;
	}

	/**
	 * The colors the Settings dropdown offers: the presets, and the current color at the end when it is not one of
	 * them, so the dropdown never shows a color the player does not have.
	 */
	public static List<Integer> choices(int current) {
		List<Integer> colors = new java.util.ArrayList<>();
		for (Palette.Swatch swatch : PRESETS) {
			colors.add(swatch.rgb());
		}
		if (preset(current) == null) {
			colors.add(current & 0xFFFFFF);
		}
		return colors;
	}

	/**
	 * The color a line is drawn in as it fades (FR-7.4): full color when new, down to the same 75/255 brightness vanilla
	 * subtitles end at, as {@code 0xAARRGGBB} with full alpha.
	 *
	 * @param fade 0 for a new line, 1 for one about to disappear
	 */
	public static int faded(int rgb, double fade) {
		double f = Math.max(0, Math.min(1, fade));
		double brightness = 1.0 - f * (1.0 - 75.0 / 255.0);
		int r = (int) Math.round(((rgb >> 16) & 0xFF) * brightness);
		int g = (int) Math.round(((rgb >> 8) & 0xFF) * brightness);
		int b = (int) Math.round((rgb & 0xFF) * brightness);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
