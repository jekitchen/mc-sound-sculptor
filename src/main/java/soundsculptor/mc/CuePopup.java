package soundsculptor.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import soundsculptor.core.cue.CueColors;
import soundsculptor.core.cue.CueField;
import soundsculptor.core.cue.CueResolution;
import soundsculptor.core.cue.CueResolver;
import soundsculptor.core.cue.CueSettings;
import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.ui.Palette;
import soundsculptor.core.ui.RowFlow;
import soundsculptor.core.ui.SliderModel;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptText;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Texts;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;

/**
 * A place's Cues pop-up (PRD FR-7, decided 2026-11-21): Show cues, Sounds without subtitles, New mutes get Cue, the text
 * color, the text size (50% to 200%) and the box position, over the screen like the Level pop-up. Opened from the zone
 * editor's Cues…, the Zones tab's Everywhere row and Settings.
 *
 * <p>In a zone every setting can be <b>Unchanged</b>: it then passes on to the next bigger zone, and finally Everywhere,
 * as entries do under FR-9. The control says what it would get ("Unchanged (On)", with "from Everywhere" when there is
 * room), and its tooltip names the place. The dropdowns offer Unchanged as their first choice; the sliders have an
 * Unchanged button, and move off Unchanged as soon as they are moved. Everywhere is the base, so it has no Unchanged.
 *
 * <p>A dropdown choice or a button is handed to the caller at once; a slider while it is dragged only through a preview,
 * and for good when it is let go or moved by a key, so Everywhere's profile is not saved on every step. The zone editor
 * keeps the settings in its draft until Save; Everywhere saves them straight away (like the Settings pages did).
 * Keyboard: Tab goes through the controls in reading order; Esc, Done or a click outside closes it.
 */
public final class CuePopup {
	/** The widest the pop-up grows. */
	private static final int MAX_WIDTH = 420;
	private static final Identifier RESET_ICON = Identifier.fromNamespaceAndPath("soundsculptor", "icon/reset");
	/** The dropdowns' "Unchanged" choice for a color; colors themselves are 0 to 0xFFFFFF. */
	public static final int UNCHANGED_COLOR = -1;

	/** A switch's choice: Unchanged (a zone only), On or Off. */
	public enum Choice {
		UNCHANGED, ON, OFF;

		static Choice of(Boolean value) {
			return value == null ? UNCHANGED : value ? ON : OFF;
		}

		Boolean value() {
			return this == UNCHANGED ? null : this == ON;
		}
	}

	public SculptDropdown<Choice> showCues;
	public SculptDropdown<Choice> withoutSubtitles;
	public SculptDropdown<Choice> newMutesGetCue;
	public SculptDropdown<Integer> color;
	public SculptSlider textScale;
	/** Puts the text size back to Unchanged; {@code null} for Everywhere. */
	public SculptButton textScaleUnchanged;
	public SculptSlider offsetX;
	public SculptSlider offsetY;
	/** "Put the cue box back above the subtitles". */
	public SculptButton offsetReset;
	/** Puts the box position back to Unchanged; {@code null} for Everywhere. */
	public SculptButton offsetUnchanged;
	public SculptButton done;

	private final boolean everywhere;
	private final CueResolution inherited;
	private final Profile profile;
	private final Consumer<PlaceCues> apply;
	private final Consumer<PlaceCues> preview;
	/** The settings last handed to {@link #apply}. */
	private PlaceCues applied;
	private PlaceCues cues;
	/** Set while a slider is moved to show an inherited value, so that move is not taken as the player's. */
	private boolean showingInherited;

	private CuePopup(SculptScreen host, PlaceRef where, String placeName, PlaceCues initial, CueResolution inherited,
			Consumer<PlaceCues> apply, Consumer<PlaceCues> preview,
			Runnable onClosed) {
		this.everywhere = where.isEverywhere();
		this.inherited = inherited;
		this.profile = ProfileManager.profile();
		this.apply = apply;
		this.preview = preview != null ? preview : apply;
		this.applied = initial;
		this.cues = initial;
		Font font = Minecraft.getInstance().font;
		int room = Math.min(MAX_WIDTH, host.width - 2 * Theme.PADDING) - 2 * Theme.GAP;

		showCues = choice(CueField.SHOW_CUES, "soundsculptor.cues.show", initial.showCues(), room,
				Component.translatable("soundsculptor.cues.show.tip", hotkeyName()), PlaceCues::withShowCues);
		withoutSubtitles = choice(CueField.WITHOUT_SUBTITLES, "soundsculptor.cues.without_subtitles", initial.withoutSubtitles(), room,
				Component.translatable("soundsculptor.cues.without_subtitles.tip"), PlaceCues::withWithoutSubtitles);
		newMutesGetCue = choice(CueField.NEW_MUTES_GET_CUE, "soundsculptor.cues.new_mutes", initial.newMutesGetCue(), room,
				Component.translatable("soundsculptor.cues.new_mutes.tip"), PlaceCues::withNewMutesGetCue);
		color = colorDropdown(room);

		int shownScale = initial.textScale() != null ? initial.textScale() : inheritedSettings().textScale();
		Component scaleLabel = Component.translatable("soundsculptor.cues.size");
		textScale = Tooltips.describe(new SculptSlider(sliderWidth(font, scaleLabel, Texts.percent(PlaceCues.MAX_TEXT_SCALE), room), scaleLabel,
				new SliderModel(PlaceCues.MIN_TEXT_SCALE, PlaceCues.MAX_TEXT_SCALE, PlaceCues.TEXT_SCALE_STEP, 1, Double.NaN, shownScale),
				value -> shown(CueField.TEXT_SCALE, Texts.percent((int) value)), null,
				value -> {
					if (!showingInherited) {
						slide(cues.withTextScale((int) value));
					}
				}).onCommit(this::commit), tip("soundsculptor.cues.size.tip", CueField.TEXT_SCALE, Texts.percent(inheritedSettings().textScale())));
		textScaleUnchanged = everywhere ? null : unchangedButton(CueField.TEXT_SCALE, Texts.percent(inheritedSettings().textScale()));

		CueSettings now = initial.boxOffset() != null ? null : inheritedSettings();
		int shownX = now == null ? initial.boxOffset().x() : now.offsetX();
		int shownY = now == null ? initial.boxOffset().y() : now.offsetY();
		Component offsetInherited = Component.translatable("soundsculptor.cues.offset.both", inheritedSettings().offsetX(),
				inheritedSettings().offsetY());
		offsetX = offsetSlider("soundsculptor.cues.offset_x", shownX, room, offsetInherited, true);
		offsetY = offsetSlider("soundsculptor.cues.offset_y", shownY, room, offsetInherited, false);
		offsetReset = SculptButton.icon(RESET_ICON, Component.translatable("soundsculptor.cues.offset.reset"), () -> {
			change(cues.withBoxOffset(PlaceCues.Offset.ZERO));
			show(offsetX, 0);
			show(offsetY, 0);
			refreshButtons();
		});
		offsetUnchanged = everywhere ? null : unchangedButton(CueField.BOX_OFFSET, offsetInherited);
		done = SculptButton.text(Component.translatable("soundsculptor.ui.done"), host::closeOverlay);
		refreshButtons();

		// Lay out: the title, then each group of controls flowing onto as many lines as it needs, Done at the end.
		String name = placeName;
		SculptText title = new SculptText(Component.translatable("soundsculptor.cues.title", name));
		if (title.getWidth() > room) {
			title = new SculptText(Component.translatable("soundsculptor.cues.title.short"));
		}
		List<List<AbstractWidget>> groups = new ArrayList<>();
		groups.add(List.of(showCues, withoutSubtitles, newMutesGetCue, color));
		groups.add(everywhere ? List.of(textScale) : List.of(textScale, textScaleUnchanged));
		List<AbstractWidget> position = new ArrayList<>(List.of(offsetX, offsetY, offsetReset));
		if (offsetUnchanged != null) {
			position.add(offsetUnchanged);
		}
		position.add(done);
		groups.add(position);

		int width = title.getWidth();
		for (List<AbstractWidget> group : groups) {
			for (AbstractWidget widget : group) {
				width = Math.max(width, widget.getWidth());
			}
		}
		width = Math.max(width, Math.min(room, naturalWidth(groups.get(0))));
		int panelWidth = width + 2 * Theme.GAP;
		int x = (host.width - panelWidth) / 2;
		// First the heights, so the panel can be centred.
		int contentHeight = 0;
		List<List<RowFlow.Spot>> spots = new ArrayList<>();
		for (List<AbstractWidget> group : groups) {
			int[] widths = group.stream().mapToInt(AbstractWidget::getWidth).toArray();
			List<RowFlow.Spot> placed = RowFlow.place(widths, width, Theme.GAP);
			spots.add(placed);
			contentHeight += RowFlow.lines(placed) * (Theme.WIDGET_HEIGHT + Theme.GAP);
		}
		boolean withTitle = contentHeight + Theme.WIDGET_HEIGHT + Theme.GAP + Theme.GAP <= host.height - 2 * Theme.PADDING;
		int panelHeight = Theme.GAP + (withTitle ? Theme.WIDGET_HEIGHT + Theme.GAP : 0) + contentHeight;
		int y = Math.max(Theme.PADDING, (host.height - panelHeight) / 2);
		List<AbstractWidget> widgets = new ArrayList<>();
		int lineY = y + Theme.GAP;
		if (withTitle) {
			title.setPosition(x + Theme.GAP, lineY);
			widgets.add(title);
			lineY += Theme.WIDGET_HEIGHT + Theme.GAP;
		}
		for (int g = 0; g < groups.size(); g++) {
			List<AbstractWidget> group = groups.get(g);
			List<RowFlow.Spot> placed = spots.get(g);
			for (int i = 0; i < group.size(); i++) {
				AbstractWidget widget = group.get(i);
				RowFlow.Spot spot = placed.get(i);
				int wx = x + Theme.GAP + spot.x();
				if (widget == done) {
					// Done goes at the right end of its line.
					wx = Math.max(wx, x + panelWidth - Theme.GAP - done.getWidth());
				}
				widget.setPosition(wx, lineY + spot.line() * (Theme.WIDGET_HEIGHT + Theme.GAP));
				widgets.add(widget);
			}
			lineY += RowFlow.lines(placed) * (Theme.WIDGET_HEIGHT + Theme.GAP);
		}
		// Closing also saves a slider move not yet saved (a drag the pop-up closed during).
		host.openOverlay(x, y, panelWidth, panelHeight, widgets, showCues, () -> {
			commit();
			onClosed.run();
		});
	}

	/**
	 * Opens a place's Cues pop-up on {@code host}.
	 *
	 * @param where     Everywhere, or the zone
	 * @param placeName the name in the title: "Everywhere", or the zone's name as edited
	 * @param initial   the place's settings now
	 * @param zone      the zone as edited (for what Unchanged passes on to), or {@code null} for Everywhere or a zone whose
	 *                  values cannot be read yet, which then pass on to Everywhere
	 * @param apply     called with the place's new settings after each finished change: a choice, a button, and a slider
	 *                  when it is let go or moved by a key (and when the pop-up closes, if a move is not handed on yet)
	 * @param preview   called with every slider value while it is dragged, for a live look without saving; {@code null}
	 *                  to use {@code apply} for those too (cheap callers, such as the zone editor's draft)
	 * @param onClosed  runs after the pop-up closes, e.g. to put focus back on the button that opened it
	 */
	public static CuePopup open(SculptScreen host, PlaceRef where, String placeName, PlaceCues initial, Zone zone,
			Consumer<PlaceCues> apply, Consumer<PlaceCues> preview, Runnable onClosed) {
		Profile profile = ProfileManager.profile();
		CueResolution inherited = where.isEverywhere() ? null
				: zone != null ? CueResolver.inheritedBy(profile, zone) : CueResolver.resolve(List.of(), profile.everywhereCues());
		return new CuePopup(host, where, placeName, initial, inherited, apply, preview, onClosed);
	}

	/**
	 * Everywhere's Cues pop-up. Each finished change is saved at once; while a slider is dragged the cue box shows its
	 * value through a preview, without saving the profile on every step. {@code onClosed} runs after it closes.
	 */
	public static CuePopup openEverywhere(SculptScreen host, Runnable onClosed) {
		return open(host, PlaceRef.EVERYWHERE, SetDeleteDialog.placeName(ProfileManager.profile(), PlaceRef.EVERYWHERE),
				ProfileManager.profile().everywhereCues(), null, cues -> {
					ProfileManager.update(p -> p.withEverywhereCues(cues));
					SoundHook.previewEverywhereCues(null);
				}, SoundHook::previewEverywhereCues, () -> {
					SoundHook.previewEverywhereCues(null);
					onClosed.run();
				});
	}

	/** Everywhere's Cues pop-up; focus goes back to {@code opener} when it closes. */
	public static CuePopup openEverywhere(SculptScreen host, AbstractWidget opener) {
		return openEverywhere(host, () -> host.setFocused(opener));
	}

	/** The settings the zone's Unchanged ones pass on to; Everywhere's own for Everywhere. */
	private CueSettings inheritedSettings() {
		return inherited != null ? inherited.settings() : CueSettings.of(cues);
	}

	/** A finished change (a dropdown choice, a button): handed to the caller at once. */
	private void change(PlaceCues value) {
		cues = value;
		commit();
	}

	/** A slider moving: shown at once through the preview; saved when the move is done ({@link #commit}). */
	private void slide(PlaceCues value) {
		cues = value;
		preview.accept(value);
	}

	/** Hands the settings to the caller if they changed since last time. */
	private void commit() {
		if (!cues.equals(applied)) {
			applied = cues;
			apply.accept(cues);
		}
	}

	/** The settings as changed so far. */
	public PlaceCues cues() {
		return cues;
	}

	// ---- The switches ----

	private SculptDropdown<Choice> choice(CueField field, String key, Boolean initial, int room, Component tip,
			java.util.function.BiFunction<PlaceCues, Boolean, PlaceCues> with) {
		Component label = Component.translatable(key);
		List<Choice> choices = everywhere ? List.of(Choice.ON, Choice.OFF) : List.of(Choice.UNCHANGED, Choice.ON, Choice.OFF);
		Component inheritedState = inherited == null ? null : SculptToggle.stateText(boolValue(inherited.settings(), field));
		boolean withSource = inherited != null && fits(label, choices, c -> choiceName(c, inheritedState, field, true), room);
		java.util.function.Function<Choice, Component> names = c -> choiceName(c, inheritedState, field, withSource);
		List<Component> shownNames = choices.stream().map(names).toList();
		int width = Math.min(room, SculptDropdown.widthFor(label, shownNames));
		return Tooltips.describe(new SculptDropdown<>(width, label, choices, names, Choice.of(initial),
				c -> change(with.apply(cues, c.value()))), withInherited(tip, field, inheritedState));
	}

	private Component choiceName(Choice choice, Component inheritedState, CueField field, boolean withSource) {
		return switch (choice) {
			case ON -> SculptToggle.stateText(true);
			case OFF -> SculptToggle.stateText(false);
			case UNCHANGED -> unchanged(inheritedState, field, withSource);
		};
	}

	/** "Unchanged (On)", or "Unchanged (On, from Everywhere)" when there is room for the place. */
	private Component unchanged(Component value, CueField field, boolean withSource) {
		return withSource ? Component.translatable("soundsculptor.cues.unchanged.from", value, sourceName(field))
				: Component.translatable("soundsculptor.cues.unchanged", value);
	}

	private String sourceName(CueField field) {
		return SetDeleteDialog.placeName(profile, inherited.source(field));
	}

	private static boolean boolValue(CueSettings settings, CueField field) {
		return switch (field) {
			case SHOW_CUES -> settings.showCues();
			case WITHOUT_SUBTITLES -> settings.cueSoundsWithoutSubtitles();
			case NEW_MUTES_GET_CUE -> settings.newMuteRulesCueOn();
			default -> throw new IllegalArgumentException(field.name());
		};
	}

	private static <T> boolean fits(Component label, List<T> choices, java.util.function.Function<T, Component> names, int room) {
		return SculptDropdown.widthFor(label, choices.stream().map(names).toList()) <= room;
	}

	/** The setting's tooltip; in a zone with what Unchanged gives now and where from. */
	private Component tip(String key, CueField field, Component inheritedValue) {
		return withInherited(Component.translatable(key), field, inheritedValue);
	}

	private Component withInherited(Component tip, CueField field, Component inheritedValue) {
		if (inherited == null) {
			return tip;
		}
		return Component.translatable("soundsculptor.cues.tip_unchanged", tip,
				Component.translatable("soundsculptor.cues.unchanged.tip", inheritedValue, sourceName(field)));
	}

	// ---- The color ----

	private SculptDropdown<Integer> colorDropdown(int room) {
		Component label = Component.translatable("soundsculptor.cues.color");
		int inheritedRgb = inheritedSettings().color();
		List<Integer> choices = new ArrayList<>();
		if (!everywhere) {
			choices.add(UNCHANGED_COLOR);
		}
		choices.addAll(CueColors.choices(cues.color() != null ? cues.color() : CueColors.PRESETS.get(0).rgb()));
		Component inheritedName = colorName(inheritedRgb);
		boolean withSource = inherited != null && fits(label, choices, rgb -> colorChoiceName(rgb, inheritedName, true), room);
		java.util.function.Function<Integer, Component> names = rgb -> colorChoiceName(rgb, inheritedName, withSource);
		int width = Math.min(room, SculptDropdown.widthFor(label, choices.stream().map(names).toList()));
		int initial = cues.color() != null ? cues.color() : UNCHANGED_COLOR;
		return Tooltips.describe(new SculptDropdown<>(width, label, choices, names, initial,
				rgb -> change(cues.withColor(rgb == UNCHANGED_COLOR ? null : rgb))),
				withInherited(Component.translatable("soundsculptor.cues.color.tip"), CueField.COLOR, inheritedName));
	}

	private Component colorChoiceName(int rgb, Component inheritedName, boolean withSource) {
		return rgb == UNCHANGED_COLOR ? unchanged(inheritedName, CueField.COLOR, withSource) : colorName(rgb);
	}

	/**
	 * A cue color's name ("Light blue-gray"); a color no longer offered, chosen before it was withdrawn, by its name with
	 * "(low contrast)"; any other color (from an edited file) as "Custom (#123456)".
	 */
	static Component colorName(int rgb) {
		Palette.Swatch preset = CueColors.preset(rgb);
		if (preset != null) {
			return Component.translatable("soundsculptor.color." + preset.id());
		}
		Palette.Swatch withdrawn = CueColors.withdrawn(rgb);
		return withdrawn != null
				? Component.translatable("soundsculptor.cues.color.low_contrast", Component.translatable("soundsculptor.color." + withdrawn.id()))
				: Component.translatable("soundsculptor.cues.color.custom", Palette.formatHex(rgb));
	}

	// ---- Text size and box position ----

	/** A value as the slider shows it: "Unchanged (100%)" while the zone leaves it Unchanged. */
	private Component shown(CueField field, Component value) {
		return everywhere || cues.has(field) ? value : Component.translatable("soundsculptor.cues.unchanged", value);
	}

	/** Wide enough for "Label: Unchanged (value)" with its widest value, within {@code room}. */
	private int sliderWidth(Font font, Component label, Component widestValue, int room) {
		Component value = everywhere ? widestValue : Component.translatable("soundsculptor.cues.unchanged", widestValue);
		return Math.min(room, font.width(Component.translatable("soundsculptor.ui.slider", label, value)) + 16);
	}

	/** A cue box offset slider in GUI pixels, 5 at a time (1 with Shift); positive is right or down. */
	private SculptSlider offsetSlider(String key, int initial, int room, Component inheritedValue, boolean isX) {
		Component label = Component.translatable(key);
		Font font = Minecraft.getInstance().font;
		int width = sliderWidth(font, label, Component.translatable("soundsculptor.cues.offset.value", -PlaceCues.MAX_OFFSET), room);
		SculptSlider[] self = new SculptSlider[1];
		self[0] = Tooltips.describe(new SculptSlider(width, label,
				new SliderModel(-PlaceCues.MAX_OFFSET, PlaceCues.MAX_OFFSET, 5, 1, Double.NaN, initial),
				value -> shown(CueField.BOX_OFFSET, Component.translatable("soundsculptor.cues.offset.value", (int) value)), null,
				value -> {
					if (showingInherited) {
						return;
					}
					// The two parts are one setting: moving one sets both, the other where it is shown now.
					boolean wasUnchanged = !cues.has(CueField.BOX_OFFSET);
					int other = (int) (isX ? offsetY : offsetX).modelValue();
					slide(cues.withBoxOffset(isX ? new PlaceCues.Offset((int) value, other) : new PlaceCues.Offset(other, (int) value)));
					if (wasUnchanged) {
						show(isX ? offsetY : offsetX, other);
					}
					refreshButtons();
				}).onCommit(this::commit), tip(key + ".tip", CueField.BOX_OFFSET, inheritedValue));
		return self[0];
	}

	/** Moves a slider to show {@code value} without taking it as a change, and refreshes its text. */
	private void show(SculptSlider slider, int value) {
		showingInherited = true;
		try {
			slider.setTypedValue(value);
		} finally {
			showingInherited = false;
		}
	}

	/** "Unchanged": the zone leaves the setting to the next bigger zone or Everywhere again. */
	private SculptButton unchangedButton(CueField field, Component inheritedValue) {
		Component label = Component.translatable("soundsculptor.cues.unchanged.button");
		SculptButton[] self = new SculptButton[1];
		self[0] = SculptButton.text(label, () -> {
			change(cues.cleared(field));
			CueSettings base = inheritedSettings();
			if (field == CueField.TEXT_SCALE) {
				show(textScale, base.textScale());
			} else {
				show(offsetX, base.offsetX());
				show(offsetY, base.offsetY());
			}
			refreshButtons();
			// The button is now off; keep focus on the slider it belongs to.
			if (Minecraft.getInstance().gui.screen() instanceof SculptScreen screen) {
				screen.setFocused(field == CueField.TEXT_SCALE ? textScale : offsetX);
			}
		});
		return Tooltips.describe(self[0], Component.translatable("soundsculptor.cues.unchanged.tip", inheritedValue, sourceName(field)));
	}

	/** The Unchanged buttons are on only while the zone sets their setting. */
	private void refreshButtons() {
		if (textScaleUnchanged != null) {
			textScaleUnchanged.active = cues.has(CueField.TEXT_SCALE);
		}
		if (offsetUnchanged != null) {
			offsetUnchanged.active = cues.has(CueField.BOX_OFFSET);
		}
	}

	/** The width of the switches side by side, so the pop-up can hold two to a line when there is room. */
	private static int naturalWidth(List<AbstractWidget> group) {
		int total = 0;
		for (int i = 0; i < Math.min(2, group.size()); i++) {
			total += group.get(i).getWidth() + (i > 0 ? Theme.GAP : 0);
		}
		return total;
	}

	/** The cues hotkey's key, or "not set (Options → Controls)". */
	private static Component hotkeyName() {
		net.minecraft.client.KeyMapping key = soundsculptor.SoundSculptorClient.cuesKey;
		return key == null || key.isUnbound() ? Component.translatable("soundsculptor.settings.outlines.key_unbound")
				: key.getTranslatedKeyMessage();
	}
}
