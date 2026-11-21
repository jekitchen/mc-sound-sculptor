package soundsculptor.mc.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import soundsculptor.core.cue.CueColors;
import soundsculptor.core.ui.Palette;

/**
 * PRD A-3: every text color keeps a contrast ratio of at least 4.5:1 (WCAG 2) against the background it is drawn on.
 * Most backgrounds are see-through, so each is worked out over the worst thing behind it: a white sky or snow field,
 * and also black. The text shadow vanilla draws under every line is left out, so the real contrast is higher.
 */
class ThemeContrastTest {
	private static final double MINIMUM = 4.5;
	private static final int WHITE = 0xFFFFFF;
	private static final int BLACK = 0x000000;
	/** Vanilla's in-world menu background (textures/gui/inworld_menu_background.png): black at alpha 64. */
	private static final int VANILLA_MENU_BACKGROUND = 0x40000000;
	/** Vanilla's subtitle row background, which the cue box uses ({@code Options.getBackgroundColor(0.8F)}). */
	private static final int VANILLA_SUBTITLE_BACKGROUND = 0xCC000000;
	/** The typical inside pixel of vanilla's button texture, plain and highlighted (focused or hovered). */
	private static final int VANILLA_BUTTON = 0x6F6F6F;
	private static final int VANILLA_BUTTON_HIGHLIGHTED = 0x757575;
	/** Vanilla's disabled button texture's inside, and the gray vanilla draws a disabled button's label in. */
	private static final int VANILLA_BUTTON_DISABLED = 0x2C2C2C;
	private static final int VANILLA_DISABLED_TEXT = 0xA0A0A0;

	/** The body of every Sound Sculptor screen: the world (white at worst), vanilla's menu background, then {@link Theme#BODY}. */
	private static int body(int world) {
		return over(Theme.BODY, over(VANILLA_MENU_BACKGROUND, world));
	}

	private static int list(int world) {
		return over(Theme.LIST_BACKGROUND, body(world));
	}

	private record Case(String what, int text, int background) {
	}

	private static List<Case> cases() {
		List<Case> cases = new ArrayList<>();
		for (int world : new int[] {WHITE, BLACK}) {
			String behind = world == WHITE ? " (white behind)" : " (black behind)";
			int body = body(world);
			int panel = over(Theme.PANEL, over(VANILLA_MENU_BACKGROUND, world));
			int list = list(world);
			int popUp = over(Theme.TOAST, over(Theme.SCRIM, body));
			// Labels, hints, errors and the boost warning in the body.
			cases.add(new Case("label on the body" + behind, Theme.TEXT_DIM, body));
			cases.add(new Case("title on the body" + behind, Theme.TEXT, body));
			cases.add(new Case("field error on the body" + behind, Theme.ERROR, body));
			cases.add(new Case("boost warning on the body" + behind, Theme.WARNING, body));
			// Header, tab bar, footer and dialogs.
			cases.add(new Case("header subtitle on the panel" + behind, Theme.TEXT_DIM, panel));
			cases.add(new Case("dialog message on the panel" + behind, Theme.TEXT_DIM, over(Theme.PANEL, body)));
			// List rows: plain, under the mouse, with keyboard focus.
			for (int row : new int[] {list, over(Theme.ROW_HOVER, list), over(Theme.ROW_FOCUSED, list)}) {
				cases.add(new Case("row text" + behind, Theme.TEXT, row));
				cases.add(new Case("row second line" + behind, Theme.TEXT_DIM, row));
			}
			// Pop-ups (Level, cue and limit; Like this…; Attach to…) and toasts.
			cases.add(new Case("pop-up text" + behind, Theme.TEXT_DIM, popUp));
			cases.add(new Case("pop-up error" + behind, Theme.ERROR, popUp));
			cases.add(new Case("pop-up boost warning" + behind, Theme.WARNING, popUp));
			cases.add(new Case("toast" + behind, Theme.TEXT, over(Theme.TOAST, body)));
			// The Bypass label on the HUD (FR-13.1) and the zone marking hint, on the panel color over the world.
			cases.add(new Case("Bypass label on the HUD" + behind, Theme.WARNING, over(Theme.PANEL, world)));
			cases.add(new Case("marking hint on the HUD" + behind, Theme.TEXT_DIM, over(Theme.PANEL, world)));
			// FR-7.3, A-7: every cue color offered in Settings, on the cue box over the world.
			for (Palette.Swatch swatch : CueColors.PRESETS) {
				cases.add(new Case("cue color " + swatch.id() + behind, swatch.rgb(), over(VANILLA_SUBTITLE_BACKGROUND, world)));
			}
		}
		// Vanilla's own buttons, which Sound Sculptor's buttons, toggles and sliders are drawn with.
		cases.add(new Case("button label", Theme.TEXT, VANILLA_BUTTON));
		cases.add(new Case("focused button label", Theme.TEXT, VANILLA_BUTTON_HIGHLIGHTED));
		cases.add(new Case("disabled button label", VANILLA_DISABLED_TEXT, VANILLA_BUTTON_DISABLED));
		return cases;
	}

	@Test
	void everyTextColorKeepsFourAndAHalfToOne() {
		List<String> low = new ArrayList<>();
		for (Case c : cases()) {
			double ratio = contrast(c.text(), c.background());
			if (ratio < MINIMUM) {
				low.add(String.format(java.util.Locale.ROOT, "%s: %.2f:1", c.what(), ratio));
			}
		}
		assertTrue(low.isEmpty(), "below 4.5:1: " + low);
	}

	@Test
	void theFormulaMatchesWcag() {
		assertTrue(Math.abs(contrast(WHITE, BLACK) - 21) < 0.01);
		assertTrue(Math.abs(contrast(0x777777, WHITE) - 4.48) < 0.01);
		assertTrue(Math.abs(contrast(0x767676, WHITE) - 4.54) < 0.01);
	}

	/** {@code argb} laid over the opaque {@code rgb}, as the GUI blends it. */
	static int over(int argb, int rgb) {
		double a = ((argb >>> 24) & 0xFF) / 255.0;
		int r = (int) Math.round(((argb >> 16) & 0xFF) * a + ((rgb >> 16) & 0xFF) * (1 - a));
		int g = (int) Math.round(((argb >> 8) & 0xFF) * a + ((rgb >> 8) & 0xFF) * (1 - a));
		int b = (int) Math.round((argb & 0xFF) * a + (rgb & 0xFF) * (1 - a));
		return r << 16 | g << 8 | b;
	}

	/** The WCAG 2 contrast ratio of two opaque colors (alpha ignored). */
	static double contrast(int a, int b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(int rgb) {
		return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF) + 0.0722 * channel(rgb & 0xFF);
	}

	private static double channel(int value) {
		double c = value / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}
}
