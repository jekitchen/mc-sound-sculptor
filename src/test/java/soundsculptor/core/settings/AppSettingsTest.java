package soundsculptor.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import soundsculptor.core.cue.PlaceCues;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PRD A-6, FR-10.5, FR-12.2 and FR-12.6 (reset buttons). The cue settings moved to the places on 2026-11-21; what is left
 * of them here is the migration record ({@link AppSettings#legacyCues()}).
 */
class AppSettingsTest {
	private static final AppSettings D = AppSettings.DEFAULTS;

	/** Every value differs from its default. */
	private static AppSettings changed() {
		return D.withOutlinesThroughBlocks(false)
				.withReducedMotion(true)
				.withLastTab("rules");
	}

	@Test
	void defaultsFollowThePrd() {
		assertTrue(D.outlinesThroughBlocks(), "FR-10.5: default on");
		assertFalse(D.reducedMotion());
		assertEquals(AppSettings.DEFAULT_TAB, D.lastTab());
		assertEquals("sounds", AppSettings.DEFAULT_TAB);
		assertNull(D.legacyCues(), "a new install has no old app-wide cue settings");
	}

	@Test
	void defaultCueColorIsALightBlueGrayThatReadsOnTheDarkCueBox() {
		int rgb = PlaceCues.DEFAULT_COLOR;
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		assertTrue(b > r && b >= g, "blue-ish");
		assertTrue(Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 64, "gray-ish, not saturated");
		// A-3: contrast of at least 4.5:1 against the black subtitle background.
		assertTrue((luminance(rgb) + 0.05) / 0.05 >= 4.5);
	}

	private static double luminance(int rgb) {
		double[] c = new double[3];
		for (int i = 0; i < 3; i++) {
			double v = (rgb >> (16 - 8 * i) & 0xFF) / 255.0;
			c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	@Test
	void withersChangeOnlyTheirOwnValue() {
		AppSettings s = D.withReducedMotion(true);
		assertTrue(s.reducedMotion());
		assertEquals(D.withReducedMotion(false), D);
		assertEquals(D.outlinesThroughBlocks(), s.outlinesThroughBlocks());
		assertEquals(D.lastTab(), s.lastTab());
	}

	@Test
	void theOldCueSettingsNeverCarryNewMutesGetCue() {
		// "New mutes get Cue" was a profile setting even before; the profile keeps its own (2026-11-21).
		AppSettings s = D.withLegacyCues(PlaceCues.EVERYWHERE_DEFAULTS.withShowCues(true));
		assertNull(s.legacyCues().newMutesGetCue());
		assertEquals(true, s.legacyCues().showCues());
		assertEquals(s.legacyCues(), s.withReducedMotion(true).legacyCues(), "kept through other changes");
	}

	@Test
	void lastTabMustBeAPlainId() {
		assertEquals("profiles", D.withLastTab("profiles").lastTab());
		assertTrue(AppSettings.isValidTab("inspect"));
		assertTrue(AppSettings.isValidTab("tab_2"));
		for (String bad : new String[] {"", "Rules", "rules tab", "../x", "a".repeat(33)}) {
			assertFalse(AppSettings.isValidTab(bad), bad);
			assertThrows(IllegalArgumentException.class, () -> D.withLastTab(bad), bad);
		}
		assertFalse(AppSettings.isValidTab(null));
		assertThrows(NullPointerException.class, () -> D.withLastTab(null));
	}

	@Test
	void resetOneValueLeavesTheOthers() {
		AppSettings s = changed();
		for (AppSetting setting : AppSetting.values()) {
			AppSettings reset = s.reset(setting);
			assertTrue(reset.isDefault(setting), setting.name());
			for (AppSetting other : AppSetting.values()) {
				if (other != setting) {
					assertFalse(reset.isDefault(other), setting + " also reset " + other);
				}
			}
		}
	}

	@Test
	void resetPageResetsOnlyThatPage() {
		AppSettings s = changed();
		AppSettings reset = s.resetPage(SettingsPage.ACCESSIBILITY);
		assertEquals(D.reducedMotion(), reset.reducedMotion());
		assertEquals(s.outlinesThroughBlocks(), reset.outlinesThroughBlocks());
		assertEquals(s.lastTab(), reset.lastTab());
		assertTrue(reset.isPageDefault(SettingsPage.ACCESSIBILITY));
		assertFalse(reset.isPageDefault(SettingsPage.ZONES));
	}

	@Test
	void resettingEveryPageKeepsTheLastTab() {
		AppSettings s = changed();
		for (SettingsPage page : SettingsPage.values()) {
			s = s.resetPage(page);
		}
		assertEquals(D.withLastTab("rules"), s);
	}

	@Test
	void onlyTheLastTabHasNoPageAndGeneralHoldsNoAppWideSetting() {
		Set<SettingsPage> used = EnumSet.noneOf(SettingsPage.class);
		for (AppSetting setting : AppSetting.values()) {
			if (setting == AppSetting.LAST_TAB) {
				assertNull(setting.page());
			} else {
				used.add(setting.page());
			}
		}
		// General now holds only profile settings (Show distance); its cue switches moved to the places.
		assertEquals(EnumSet.of(SettingsPage.ZONES, SettingsPage.ACCESSIBILITY), used);
		assertEquals(SettingsPage.ZONES, AppSetting.OUTLINES_THROUGH_BLOCKS.page());
		assertEquals(SettingsPage.ACCESSIBILITY, AppSetting.REDUCED_MOTION.page());
	}
}
