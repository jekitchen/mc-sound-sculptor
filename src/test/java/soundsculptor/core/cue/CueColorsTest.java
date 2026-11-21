package soundsculptor.core.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import soundsculptor.core.settings.AppSettings;
import soundsculptor.core.ui.Palette;

import org.junit.jupiter.api.Test;

/** PRD FR-7.3 and A-7: the named cue colors, the default first, readable on the cue background. */
class CueColorsTest {
	@Test
	void theDefaultLightBlueGrayComesFirst() {
		assertEquals(AppSettings.DEFAULT_CUE_COLOR, CueColors.PRESETS.get(0).rgb());
		assertEquals("light_blue_gray", CueColors.PRESETS.get(0).id());
	}

	@Test
	void presetsAreDistinctAndNoneIsWhite() {
		assertEquals(CueColors.PRESETS.size(), new HashSet<>(CueColors.PRESETS.stream().map(Palette.Swatch::rgb).toList()).size());
		assertNull(CueColors.preset(0xFFFFFF), "vanilla subtitles are white; a cue color must differ");
	}

	@Test
	void everyPresetHasAtLeastSixToOneContrastOnBlack() {
		for (Palette.Swatch swatch : CueColors.PRESETS) {
			double ratio = (luminance(swatch.rgb()) + 0.05) / 0.05;
			assertTrue(ratio >= 6.0, swatch.id() + " has contrast " + ratio);
		}
	}

	@Test
	void presetIgnoresAlpha() {
		assertEquals("sky_blue", CueColors.preset(0xFF56B4E9).id());
	}

	@Test
	void choicesAreThePresetsPlusACustomColor() {
		List<Integer> presets = CueColors.PRESETS.stream().map(Palette.Swatch::rgb).toList();
		assertEquals(presets, CueColors.choices(AppSettings.DEFAULT_CUE_COLOR));
		List<Integer> withCustom = CueColors.choices(0x123456);
		assertEquals(presets.size() + 1, withCustom.size());
		assertEquals(0x123456, withCustom.get(withCustom.size() - 1));
	}

	@Test
	void fadingDimsToVanillasEndBrightness() {
		assertEquals(0xFFA9BCD0, CueColors.faded(0xA9BCD0, 0));
		assertEquals(0xFF4B4B4B, CueColors.faded(0xFFFFFF, 1), "white ends at 75/255, like a vanilla subtitle");
		assertEquals(CueColors.faded(0xFFFFFF, 1), CueColors.faded(0xFFFFFF, 5), "clamped");
	}

	private static double luminance(int rgb) {
		return 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb);
	}

	private static double channel(int value) {
		double c = (value & 0xFF) / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}
}
