package soundsculptor.mc;

import soundsculptor.mc.ui.Texts;
import soundsculptor.core.diag.DiagnosticsReport;
import soundsculptor.core.diag.MemoryEstimate;
import soundsculptor.core.diag.TimingWindow;
import soundsculptor.core.cue.CueColors;
import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.settings.AppSetting;
import soundsculptor.core.settings.AppSettings;
import soundsculptor.core.ui.SliderModel;
import soundsculptor.core.ui.Palette;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptTabButton;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Tooltips;
import soundsculptor.mc.ui.demo.UiKitDemoScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The main Sound Sculptor screen (FR-12.1). Header: active profile and the Bypass toggle (FR-13.1). Tabs: Sounds (the
 * one sound browser, FR-1 and FR-3, {@link SoundsPanel}), Sound Sets (the library, FR-4.7), Zones (Everywhere first,
 * FR-8.10), Profiles (FR-11.2, FR-11.3) and Settings; Inspect arrives with M12. Sounds replaced the earlier Recently
 * Played and Search tabs. Lives in {@code soundsculptor.mc} because it uses Minecraft's GUI classes.
 */
public final class SoundSculptorScreen extends SculptScreen {
	public static final int TAB_SOUNDS = 0;
	public static final int TAB_SETS = 1;
	public static final int TAB_ZONES = 2;
	public static final int TAB_PROFILES = 3;
	public static final int TAB_SETTINGS = 4;

	private static final int PAGE_TAB_WIDTH = 100;
	/** Room on each side of a page tab's name. */
	private static final int PAGE_TAB_PADDING = 16;
	/** The least width of the Settings › Zones controls; they grow to fit a longer label. */
	private static final int OUTLINE_MODE_WIDTH = 240;
	/** The least width of the Settings › Accessibility sliders. */
	private static final int ACCESSIBILITY_SLIDER_WIDTH = 200;

	// Remembered while the game runs, like the last used tab.
	private static SettingsPage settingsPage = SettingsPage.GENERAL;

	/** Settings pages (PRD 6.1). */
	public enum SettingsPage {
		GENERAL, ZONES, ACCESSIBILITY, DIAGNOSTICS
	}

	public SculptToggle bypassToggle;
	private SoundsPanel soundsPanel;
	private SetLibraryPanel setsPanel;
	private ZoneListPanel zonesPanel;
	private ProfileListPanel profilesPanel;
	/** The Settings tab's page tabs, in {@link SettingsPage} order. */
	public final List<SculptTabButton> pageTabs = new ArrayList<>();
	public SculptButton copyReportButton;
	/** The Diagnostics page's readout, one read-only row per line; {@code null} on other pages. */
	public SculptList diagnosticsList;
	public SculptDropdown<OutlineMode> outlineModeDropdown;
	// Settings › General, FR-7.
	public SculptToggle showCuesToggle;
	public SculptToggle cuesWithoutSubtitlesToggle;
	/** {@code null} outside a world: the Cue default belongs to the world's profile. */
	public SculptToggle cueDefaultToggle;
	// Settings › Accessibility, A-7 and section 8.1.
	public SculptDropdown<Integer> cueColorDropdown;
	public SculptSlider cueScaleSlider;
	public SculptSlider cueOffsetXSlider;
	public SculptSlider cueOffsetYSlider;
	public SculptButton cueOffsetReset;
	public SculptToggle reducedMotionToggle;
	public SculptToggle throughBlocksToggle;
	private DiagnosticsReport.Data diagnostics;
	private int ticks;

	public SoundSculptorScreen() {
		this(null);
	}

	public SoundSculptorScreen(Screen parent) {
		super(Component.translatable("screen.soundsculptor.title"), parent);
	}

	@Override
	protected List<Tab> tabs() {
		return List.of(
				// FR-1.6: the Sounds tab does not pause a singleplayer game, so sounds keep arriving.
				new Tab(Component.translatable("soundsculptor.tab.sounds"), false),
				new Tab(Component.translatable("soundsculptor.tab.sets"), true),
				new Tab(Component.translatable("soundsculptor.tab.zones"), true),
				new Tab(Component.translatable("soundsculptor.tab.profiles"), true),
				new Tab(Component.translatable("soundsculptor.tab.settings"), true));
	}

	@Override
	protected Component headerSubtitle() {
		return ProfileManager.isLoaded()
				? Component.translatable("soundsculptor.header.profile", ProfileManager.profile().name())
				: Component.translatable("soundsculptor.header.no_world");
	}

	@Override
	protected void addHeaderWidgets(List<AbstractWidget> header) {
		Component label = Component.translatable("soundsculptor.bypass");
		bypassToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(label), label, ProfileManager.isBypassed(), ProfileManager::setBypass),
				Component.translatable("soundsculptor.bypass.tip", bypassKeyName()));
		header.add(bypassToggle);
	}

	private static Component bypassKeyName() {
		return soundsculptor.SoundSculptorClient.bypassKey.getTranslatedKeyMessage();
	}

	@Override
	protected void addFooterWidgets(List<AbstractWidget> footer) {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			footer.add(SculptButton.text(Component.translatable("screen.soundsculptor.ui_demo"),
					() -> minecraft.gui.setScreen(new UiKitDemoScreen(this))));
		}
		footer.add(SculptButton.text(Component.translatable("soundsculptor.ui.done"), this::onClose));
	}

	@Override
	protected void buildTab(int index, Body body) {
		diagnosticsList = null;
		if (index != TAB_SETTINGS && index != TAB_PROFILES && !ProfileManager.isLoaded()) {
			// Opened outside a world (e.g. from Mod Menu on the title screen): sounds, Sound Sets and zones belong to a
			// world's profile (FR-11.1), so there is nothing to show or change yet.
			body.label(() -> Component.translatable("soundsculptor.no_world"));
			return;
		}
		if (index == TAB_SOUNDS) {
			// Back from the boost question (FR-5.4) the Level pop-up is put back: keep the panel it belongs to.
			if (soundsPanel == null || !restoringPopup()) {
				soundsPanel = new SoundsPanel(this);
			}
			soundsPanel.build(body);
		} else if (index == TAB_SETS) {
			setsPanel = new SetLibraryPanel(this);
			setsPanel.build(body);
		} else if (index == TAB_ZONES) {
			buildZones(body);
		} else if (index == TAB_PROFILES) {
			profilesPanel = new ProfileListPanel(this);
			profilesPanel.build(body);
		} else {
			buildSettings(body);
		}
	}

	// ---- Sounds (FR-1, FR-3) ----

	/** The Sounds tab's panel, while that tab is shown. */
	public SoundsPanel sounds() {
		return soundsPanel;
	}

	// ---- Sound Sets (FR-4.7) ----

	public SetLibraryPanel setsPanel() {
		return setsPanel;
	}

	// ---- Zones (PRD 6.1, FR-8) ----

	private void buildZones(Body body) {
		zonesPanel = new ZoneListPanel(this);
		zonesPanel.build(body);
	}

	public ZoneListPanel zonesPanel() {
		return zonesPanel;
	}

	// ---- Profiles (FR-11.2, FR-11.3) ----

	public ProfileListPanel profilesPanel() {
		return profilesPanel;
	}

	/** FR-11.5: a profile file dropped on the Profiles tab is imported into this world's profile (the first one, if several). */
	@Override
	public void onFilesDrop(List<java.nio.file.Path> paths) {
		if (currentTab() != TAB_PROFILES || paths.isEmpty() || hasOverlay()) {
			return;
		}
		ProfileTransfer.importWithDialog(this, paths.get(0));
	}

	// ---- Settings ----

	private void buildSettings(Body body) {
		// Page tabs (M8 review): every page visible, the current one underlined, like the main tab bar.
		SettingsPage[] pages = SettingsPage.values();
		pageTabs.clear();
		int[] widths = pageTabWidths(pages, body.width());
		for (int i = 0; i < pages.length; i++) {
			SettingsPage page = pages[i];
			pageTabs.add(new SculptTabButton(widths[i], pageTitle(page), i, pages.length, page == settingsPage, () -> setSettingsPage(page)));
		}
		// A longer translation makes a tab wider, and the tabs then wrap onto a second line rather than cut their names.
		body.flow(pageTabs.toArray(new AbstractWidget[0]));
		if (settingsPage == SettingsPage.DIAGNOSTICS) {
			buildDiagnostics(body);
			return;
		}
		if (settingsPage == SettingsPage.ZONES) {
			buildZoneSettings(body);
			return;
		}
		if (settingsPage == SettingsPage.ACCESSIBILITY) {
			buildAccessibilitySettings(body);
			return;
		}
		body.label(() -> Component.translatable("soundsculptor.settings.bypass_hint", bypassKeyName()));
		// FR-7: the cue settings (PRD 6.1 "cue defaults"). The master switch and cues without subtitles are app-wide; "New
		// mutes get Cue" and Show distance belong to the world's profile. The controls flow onto as many lines as they
		// need. The cue's look (color, text scale, position) is on the Accessibility page.
		Component showCues = Component.translatable("soundsculptor.settings.show_cues");
		showCuesToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(showCues), showCues, SoundSculptorSettings.showCues(),
				SoundSculptorSettings::setShowCues), Component.translatable("soundsculptor.settings.show_cues.tip", keyName(soundsculptor.SoundSculptorClient.cuesKey)));
		Component withoutSubtitles = Component.translatable("soundsculptor.settings.cues_without_subtitles");
		cuesWithoutSubtitlesToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(withoutSubtitles), withoutSubtitles,
				SoundSculptorSettings.cuesWithoutSubtitles(), SoundSculptorSettings::setCuesWithoutSubtitles),
				Component.translatable("soundsculptor.settings.cues_without_subtitles.tip"));
		if (!ProfileManager.isLoaded()) {
			cueDefaultToggle = null;
			body.flow(showCuesToggle, cuesWithoutSubtitlesToggle);
			body.label(() -> Component.translatable("soundsculptor.settings.no_world"));
			return;
		}
		Component cueDefault = Component.translatable("soundsculptor.settings.cue_default");
		cueDefaultToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(cueDefault), cueDefault, SoundSculptorSettings.cueDefault(),
				SoundSculptorSettings::setCueDefault), Component.translatable("soundsculptor.settings.cue_default.tip"));
		Component distance = Component.translatable("soundsculptor.settings.show_distance");
		SculptToggle showDistance = Tooltips.describe(new SculptToggle(Math.max(160, SculptToggle.widthFor(distance)), distance,
				SoundSculptorSettings.showDistance(), SoundSculptorSettings::setShowDistance),
				Component.translatable("soundsculptor.settings.show_distance.tip"));
		body.flow(showCuesToggle, cuesWithoutSubtitlesToggle, cueDefaultToggle, showDistance);
	}

	/**
	 * Settings → Accessibility (PRD 6.1, A-6, A-7, section 8.1): the cue text color, the cue text scale (100% to 200%),
	 * the cue box position (moves it away from subtitle and HUD mods) and Reduced motion. All app-wide; each change shows
	 * on the next frame.
	 */
	private void buildAccessibilitySettings(Body body) {
		AppSettings settings = AppSettingsManager.get();
		Component color = Component.translatable("soundsculptor.settings.cue_color");
		List<Integer> colors = CueColors.choices(SoundSculptorSettings.cueColor());
		List<Component> colorNames = new ArrayList<>();
		for (int rgb : colors) {
			colorNames.add(cueColorName(rgb));
		}
		cueColorDropdown = Tooltips.describe(new SculptDropdown<>(SculptDropdown.widthFor(color, colorNames), color, colors,
				SoundSculptorScreen::cueColorName, SoundSculptorSettings.cueColor(), SoundSculptorSettings::setCueColor),
				Component.translatable("soundsculptor.settings.cue_color.tip"));
		Component scale = Component.translatable("soundsculptor.settings.cue_scale");
		cueScaleSlider = Tooltips.describe(new SculptSlider(sliderWidth(scale, Texts.percent(AppSettings.MAX_CUE_SCALE)), scale,
				new SliderModel(AppSettings.MIN_CUE_SCALE, AppSettings.MAX_CUE_SCALE, 10, 1, Double.NaN, settings.cueTextScale()),
				value -> Texts.percent((int) value), null,
				value -> AppSettingsManager.update(s -> s.withCueTextScale((int) value))),
				Component.translatable("soundsculptor.settings.cue_scale.tip"));
		cueOffsetXSlider = offsetSlider("soundsculptor.settings.cue_offset_x", settings.cueOffsetX(),
				value -> AppSettingsManager.update(s -> s.withCueOffset(value, s.cueOffsetY())));
		cueOffsetYSlider = offsetSlider("soundsculptor.settings.cue_offset_y", settings.cueOffsetY(),
				value -> AppSettingsManager.update(s -> s.withCueOffset(s.cueOffsetX(), value)));
		cueOffsetReset = SculptButton.icon(Identifier.fromNamespaceAndPath("soundsculptor", "icon/reset"),
				Component.translatable("soundsculptor.settings.cue_offset.reset"), () -> {
					AppSettingsManager.update(s -> s.reset(AppSetting.CUE_OFFSET));
					rebuildWidgets();
					setInitialFocus(cueOffsetReset);
				});
		// A-6: the only motion Sound Sculptor has is a toast sliding in; its lists scroll and update without animation.
		Component motion = Component.translatable("soundsculptor.settings.reduced_motion");
		reducedMotionToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(motion), motion, settings.reducedMotion(),
				value -> AppSettingsManager.update(s -> s.withReducedMotion(value))),
				Component.translatable("soundsculptor.settings.reduced_motion.tip"));
		body.flow(cueColorDropdown, cueScaleSlider);
		body.flow(cueOffsetXSlider, cueOffsetYSlider, cueOffsetReset);
		body.flow(reducedMotionToggle);
	}

	/** A slider wide enough for "Label: value" with its widest value, and at least {@value #ACCESSIBILITY_SLIDER_WIDTH} px. */
	private static int sliderWidth(Component label, Component widestValue) {
		net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
		return Math.max(ACCESSIBILITY_SLIDER_WIDTH, font.width(Component.translatable("soundsculptor.ui.slider", label, widestValue)) + 16);
	}

	/** A cue box offset slider in GUI pixels, 5 at a time (1 with Shift); positive is right or down. */
	private static SculptSlider offsetSlider(String key, int initial, java.util.function.IntConsumer apply) {
		Component label = Component.translatable(key);
		int width = sliderWidth(label, Component.translatable("soundsculptor.settings.cue_offset.value", -AppSettings.MAX_CUE_OFFSET));
		return Tooltips.describe(new SculptSlider(width, label,
				new SliderModel(-AppSettings.MAX_CUE_OFFSET, AppSettings.MAX_CUE_OFFSET, 5, 1, Double.NaN, initial),
				value -> Component.translatable("soundsculptor.settings.cue_offset.value", (int) value), null,
				value -> apply.accept((int) value)), Component.translatable(key + ".tip"));
	}

	/** A cue color's name ("Light blue-gray"), or "Custom (#123456)" for a color set outside the game. */
	private static Component cueColorName(int rgb) {
		Palette.Swatch preset = CueColors.preset(rgb);
		if (preset != null) {
			return Component.translatable("soundsculptor.color." + preset.id());
		}
		// A color Settings no longer offers, chosen before it was withdrawn: its name, marked low contrast.
		Palette.Swatch withdrawn = CueColors.withdrawn(rgb);
		return withdrawn != null
				? Component.translatable("soundsculptor.settings.cue_color.low_contrast", Component.translatable("soundsculptor.color." + withdrawn.id()))
				: Component.translatable("soundsculptor.settings.cue_color.custom", Palette.formatHex(rgb));
	}

	/** A hotkey's key, or "not set (Options → Controls)". */
	private static Component keyName(net.minecraft.client.KeyMapping key) {
		return key.isUnbound() ? Component.translatable("soundsculptor.settings.outlines.key_unbound") : key.getTranslatedKeyMessage();
	}

	private static Component pageTitle(SettingsPage page) {
		return Component.translatable("soundsculptor.settings.page." + page.name().toLowerCase(Locale.ROOT));
	}

	/**
	 * Page tab widths: {@value #PAGE_TAB_WIDTH} px each when that fits; otherwise each tab fits its name and they share
	 * what room is left, so a longer translation keeps them on one line; only when the names alone are too wide do the
	 * tabs wrap onto a second line.
	 */
	private int[] pageTabWidths(SettingsPage[] pages, int room) {
		int[] widths = new int[pages.length];
		int natural = 0;
		int wide = 0;
		for (int i = 0; i < pages.length; i++) {
			widths[i] = font.width(pageTitle(pages[i])) + PAGE_TAB_PADDING;
			natural += widths[i];
			wide += Math.max(PAGE_TAB_WIDTH, widths[i]);
		}
		int gaps = (pages.length - 1) * Theme.GAP;
		if (wide + gaps <= room) {
			for (int i = 0; i < pages.length; i++) {
				widths[i] = Math.max(PAGE_TAB_WIDTH, widths[i]);
			}
		} else if (natural + gaps <= room) {
			int extra = (room - natural - gaps) / pages.length;
			for (int i = 0; i < pages.length; i++) {
				widths[i] += extra;
			}
		}
		return widths;
	}

	public void setSettingsPage(SettingsPage page) {
		if (page == settingsPage) {
			return;
		}
		settingsPage = page;
		rebuildWidgets();
		setInitialFocus(pageTabs.get(page.ordinal()));
	}

	public SettingsPage settingsPage() {
		return settingsPage;
	}

	/** Settings → Zones (PRD 6.1): FR-10.1 "Show zone outlines" and FR-10.5 "Outlines visible through blocks". */
	private void buildZoneSettings(Body body) {
		// "Outlines visible through blocks" is app-wide; "Show zone outlines" belongs to a world's profile.
		Component throughBlocks = Component.translatable("soundsculptor.settings.through_blocks");
		throughBlocksToggle = Tooltips.describe(new SculptToggle(Math.max(OUTLINE_MODE_WIDTH, SculptToggle.widthFor(throughBlocks)), throughBlocks,
				SoundSculptorSettings.outlinesThroughBlocks(), SoundSculptorSettings::setOutlinesThroughBlocks),
				Component.translatable("soundsculptor.settings.through_blocks.tip"));
		if (!ProfileManager.isLoaded()) {
			body.row(throughBlocksToggle);
			body.label(() -> Component.translatable("soundsculptor.settings.no_world"));
			return;
		}
		Component outlines = Component.translatable("soundsculptor.settings.outlines");
		List<Component> modeNames = new ArrayList<>();
		for (OutlineMode mode : OutlineMode.values()) {
			modeNames.add(outlineModeName(mode));
		}
		outlineModeDropdown = Tooltips.describe(new SculptDropdown<>(Math.max(OUTLINE_MODE_WIDTH, SculptDropdown.widthFor(outlines, modeNames)),
				outlines, List.of(OutlineMode.values()), SoundSculptorScreen::outlineModeName,
				SoundSculptorSettings.outlineMode(), SoundSculptorSettings::setOutlineMode),
				Component.translatable("soundsculptor.settings.outlines.tip", outlinesKeyName()));
		body.row(outlineModeDropdown);
		body.row(throughBlocksToggle);
	}

	private static Component outlineModeName(OutlineMode mode) {
		return Component.translatable("soundsculptor.settings.outlines." + mode.name().toLowerCase(Locale.ROOT));
	}

	private static Component outlinesKeyName() {
		return keyName(soundsculptor.SoundSculptorClient.outlinesKey);
	}

	/**
	 * FR-13.3: Copy report, then the live numbers, refreshed every tick. The numbers are a read-only list, one row per
	 * line, so in a small window or a longer language the page scrolls instead of running past the footer (PRD 9.3); a
	 * line too long for the row is cut with "…" and shown whole in its tooltip, read out by the narrator, and copied in
	 * full by Copy report.
	 */
	private void buildDiagnostics(Body body) {
		diagnostics = Diagnostics.data();
		copyReportButton = Tooltips.describe(SculptButton.text(Component.translatable("soundsculptor.diag.copy"), this::copyReport),
				Component.translatable("soundsculptor.diag.copy.tip"));
		body.row(copyReportButton);
		List<Supplier<Component>> lines = List.of(
				() -> Component.translatable("soundsculptor.diag.sounds", diagnostics.sounds().count()),
				() -> Component.translatable("soundsculptor.diag.per_sound", micros(diagnostics.sounds().averageMicros()),
						micros(diagnostics.sounds().maxMicros()), micros(diagnostics.peakSoundMicros())),
				() -> timing("soundsculptor.diag.tick", diagnostics.tick()),
				() -> timing("soundsculptor.diag.outlines", diagnostics.outlines()),
				() -> Component.translatable("soundsculptor.diag.active", diagnostics.activeSets(),
						diagnostics.librarySets(), diagnostics.activeEntries(), diagnostics.zones()),
				() -> Double.isNaN(diagnostics.cacheHitRate())
						? Component.translatable("soundsculptor.diag.cache.none")
						: Component.translatable("soundsculptor.diag.cache", oneDecimal(diagnostics.cacheHitRate() * 100)),
				() -> Component.translatable("soundsculptor.diag.errors", diagnostics.errors()),
				// FR-6 AC: "never more than 3 starting in any one second (diagnostics readout)".
				() -> limitsText(diagnostics.limits()),
				() -> Component.translatable("soundsculptor.diag.memory", MemoryEstimate.megabytes(Diagnostics.memoryBytes()),
						MemoryEstimate.megabytes(MemoryEstimate.BUDGET_BYTES)));
		diagnosticsList = new SculptList(body.width(), SculptList.ROW_HEIGHT, Component.translatable("soundsculptor.diag.list"));
		List<SculptList.Row> rows = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			Supplier<Component> line = lines.get(i);
			rows.add(new SculptList.Row(i, List.of(new SculptList.Row.Column(() -> line.get().getString(), 0)), List.of()));
		}
		// Placed first, so the rows are laid out where the list is. Its numbers change every tick, and a line that grows
		// past the width wraps, so the rows' heights are kept up to date.
		diagnosticsList.setLiveText(true);
		body.fill(diagnosticsList);
		diagnosticsList.setRows(rows);
	}

	/** The Diagnostics page's lines as shown now (tests). */
	public List<String> diagnosticsLines() {
		List<String> texts = new ArrayList<>();
		if (diagnosticsList != null) {
			for (SculptList.Row row : diagnosticsList.children()) {
				texts.addAll(row.columnTexts());
			}
		}
		return texts;
	}

	/**
	 * "Repeat limits: 90 played, 510 dropped. Most in one window: 3 (limit 3 per 1 s)": the most plays one limit let
	 * start within its window, against that limit (FR-6).
	 */
	private static Component limitsText(soundsculptor.core.limit.RepeatLimiter.Stats s) {
		if (s.fullest().isEmpty()) {
			return s.dropped() == 0 ? Component.translatable("soundsculptor.diag.limits.none")
					: Component.translatable("soundsculptor.diag.limits.counts", s.allowed(), s.dropped());
		}
		net.minecraft.network.chat.MutableComponent windows = Component.empty();
		for (soundsculptor.core.limit.RepeatLimiter.Fullest f : s.fullest()) {
			if (!windows.getSiblings().isEmpty()) {
				windows.append(Component.translatable("soundsculptor.diag.limits.separator"));
			}
			windows.append(Component.translatable("soundsculptor.diag.limits.window", f.most(), f.limit().count(),
					soundsculptor.core.lists.Numbers.plain(f.limit().windowMs() / 1000.0, 3)));
		}
		return Component.translatable("soundsculptor.diag.limits", s.allowed(), s.dropped(), windows);
	}

	private static Component timing(String key, TimingWindow.Stats stats) {
		return Component.translatable(key, micros(stats.averageMicros()), micros(stats.maxMicros()));
	}

	private static String micros(double value) {
		return oneDecimal(value);
	}

	/** A number with one decimal: "5.0". */
	private static String oneDecimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** The numbers on the Diagnostics page, as last refreshed. */
	public DiagnosticsReport.Data diagnostics() {
		return diagnostics;
	}

	public void copyReport() {
		minecraft.keyboardHandler.setClipboard(Diagnostics.report());
		showToast(Component.translatable("soundsculptor.diag.copied"), null);
	}

	// ---- Ticks and keys ----

	@Override
	public void tick() {
		super.tick();
		if (ticks == 0) {
			// FR-5.4: a profile with a level above 100% it never confirmed (a linked set made louder in another world)
			// asks once, the first time this screen opens there this session.
			BoostUi.askOnOpen(this);
		}
		// Bypass can also change through the hotkey or the error budget; keep the header toggle in step.
		if (bypassToggle != null && bypassToggle.value() != ProfileManager.isBypassed()) {
			bypassToggle.setValue(ProfileManager.isBypassed());
		}
		ticks++;
		if (currentTab() == TAB_SETTINGS && settingsPage == SettingsPage.DIAGNOSTICS) {
			diagnostics = Diagnostics.data();
		}
		if (currentTab() == TAB_SETS && setsPanel != null && ticks % SoundsPanel.REFRESH_TICKS == 0) {
			// The usage index reads other worlds' profiles in the background; show its counts once it has them.
			setsPanel.refreshIfUsageChanged();
		}
		if (currentTab() == TAB_SOUNDS && soundsPanel != null && soundsPanel.tick()) {
			// The player walked into or out of a zone: offer the zones they now stand in.
			boolean focused = getFocused() == soundsPanel.applyToDropdown;
			rebuildWidgets();
			if (focused) {
				setInitialFocus(soundsPanel.applyToDropdown);
			}
		}
	}

	/** Rebuilds the open tab's list from the profile, e.g. after a quick action or Undo. */
	public void refreshLists() {
		if (soundsPanel != null && currentTab() == TAB_SOUNDS) {
			soundsPanel.refresh();
		}
		if (setsPanel != null && currentTab() == TAB_SETS) {
			setsPanel.refresh();
		}
		if (zonesPanel != null && currentTab() == TAB_ZONES) {
			zonesPanel.refresh();
		}
	}

	/** The Bypass hotkey works here too (FR-13.1); P pauses the Sounds list (FR-1.3). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (soundsculptor.SoundSculptorClient.bypassKey.matches(event) && !hasOverlay() && !(getFocused() instanceof SculptTextField)) {
			ProfileManager.toggleBypass();
			bypassToggle.setValue(ProfileManager.isBypassed());
			return true;
		}
		if (currentTab() == TAB_SOUNDS && soundsPanel != null && soundsPanel.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}
}
