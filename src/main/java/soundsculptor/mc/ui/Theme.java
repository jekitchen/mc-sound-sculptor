package soundsculptor.mc.ui;

/**
 * Colors and sizes shared by all components. Every text color keeps a contrast ratio of at least 4.5:1 against each
 * background it is drawn on, even with a white sky behind the screen (PRD A-3); {@code ThemeContrastTest} works each
 * one out. The ratios noted below are on {@link #PANEL}.
 */
public final class Theme {
	private Theme() {
	}

	/** Header, tab bar and footer backgrounds. */
	public static final int PANEL = 0xE0101010;
	/** Separator lines. */
	public static final int SEPARATOR = 0xFF505050;

	/** Primary text, about 19:1 on PANEL. */
	public static final int TEXT = 0xFFFFFFFF;
	/** Secondary text (labels, hints), about 10:1 on PANEL. */
	public static final int TEXT_DIM = 0xFFC0C0C0;
	/** Error messages and invalid outlines, about 7:1 on PANEL. */
	public static final int ERROR = 0xFFFF7070;
	/** Warning text and outlines (e.g. volume above 100%), about 13:1 on PANEL. */
	public static final int WARNING = 0xFFFFD24D;

	/**
	 * Laid over the whole screen behind everything else, on top of vanilla's menu background, so text stays readable
	 * over a bright sky or snow (A-3): the dim gray of labels keeps about 7:1 even then.
	 */
	public static final int BODY = 0xC0000000;
	/** Scrolling list background; row text on it is about 17:1. */
	public static final int LIST_BACKGROUND = 0xC0000000;
	/**
	 * Row with keyboard focus (paired with the focus ring on its button and the narration). Light enough to see, dark
	 * enough that a row's dim second line keeps 4.5:1 on it.
	 */
	public static final int ROW_FOCUSED = 0x40FFFFFF;
	/** Row under the mouse. */
	public static final int ROW_HOVER = 0x30FFFFFF;
	/** Toast background. */
	public static final int TOAST = 0xF0202020;
	/** Laid over the whole screen behind a pop-up, so what is behind it reads as out of reach. */
	public static final int SCRIM = 0x99000000;

	/** Keyboard focus ring (PRD A-1). */
	public static final int FOCUS = 0xFFFFFFFF;
	/** Selected tab background. */
	public static final int TAB_SELECTED = 0xFF3A3A3A;
	/** Hovered or focused, unselected tab background. */
	public static final int TAB_HOVER = 0xFF2A2A2A;
	/** Underline on the selected tab. Paired with the "selected" narration, so color is not the only signal. */
	public static final int TAB_UNDERLINE = 0xFFFFFFFF;

	public static final int WIDGET_HEIGHT = 20;
	public static final int ICON_BUTTON_SIZE = 20;
	public static final int ICON_SIZE = 16;
	public static final int GAP = 4;
	public static final int PADDING = 6;
}
