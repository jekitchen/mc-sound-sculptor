package soundsculptor.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import soundsculptor.core.boost.BoostWarning;
import soundsculptor.core.editor.EntryDraft;
import soundsculptor.core.editor.FieldError;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.ui.SliderModel;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;

/**
 * The Level pop-up shared by the Sounds tab's "Level, cue and limit…" (PRD FR-1.5) and the sound trace's inline Level…
 * (FR-14.4): a slider (5% steps, Shift for 1%) and a number field over the screen, with Done. Each valid change is
 * handed to the caller at once; an empty field hands {@code null} (the entry sets no level). Values are checked like the
 * entry editor's Level field (FR-12.7), so an out-of-range number shows its error and changes nothing. The slider
 * starts focused; Esc, Done or a click outside closes it.
 *
 * <p>The Sounds tab's pop-up also has the repeat limit ({@link LimitControls}, FR-6) and a Cue on/off toggle beside Done
 * (FR-7): more icon buttons do not fit a row at 854 × 480, so Limit and Cue live here rather than on the row. The
 * trace's pop-up has the line's limit too (a sixth button does not fit its lines either); its lines have their own Cue.
 */
public final class LevelPopup {
	private static final int PANEL_WIDTH = 240;
	private static final int FIELD_WIDTH = 40;

	/** The level slider. */
	public final SculptSlider slider;
	/** The level field. */
	public final SculptTextField field;
	/** The Cue toggle, or {@code null} in a pop-up without one. */
	public final SculptToggle cue;
	/** The limit row, or {@code null} in a pop-up without one. */
	public final LimitControls limit;

	private LevelPopup(SculptSlider slider, SculptTextField field, SculptToggle cue, LimitControls limit) {
		this.slider = slider;
		this.field = field;
		this.cue = cue;
		this.limit = limit;
	}

	/** A pop-up with the level only (the trace's Level…). */
	public static LevelPopup open(SculptScreen host, SetEntry entry, Consumer<Integer> apply, Runnable onClosed) {
		return open(host, entry, apply, onClosed, false, null, null, null);
	}

	/**
	 * Opens the pop-up on {@code host}.
	 *
	 * @param entry      the entry whose level it starts from (100% when it sets none) and checks values like
	 * @param apply      called with each valid new level, or {@code null} when the field is emptied
	 * @param onClosed   runs after the pop-up closes for any reason but a rebuild of the screen
	 * @param cueOn      the Cue toggle's state when it opens
	 * @param toggleCue  turns the cue on or off and sets the toggle to the state that results; {@code null} for no toggle
	 * @param shownLimit the limit the row starts from (the sound's actual limit at the place); unused without a row
	 * @param applyLimit called with each complete, valid limit, or {@code null} when it is turned off; {@code null} for no
	 *                   row
	 */
	public static LevelPopup open(SculptScreen host, SetEntry entry, Consumer<Integer> apply, Runnable onClosed, boolean cueOn,
			Runnable toggleCue, Limit shownLimit, Consumer<Limit> applyLimit) {
		int max = BoostWarning.sliderMax(false);
		LimitControls limit = applyLimit == null ? null
				: new LimitControls(EntryDraft.of(entry.withLimit(shownLimit), false), applyLimit);
		int panelWidth = panelWidth(host, limit);
		int sliderWidth = panelWidth - FIELD_WIDTH - 3 * Theme.GAP;
		double initial = entry.volume() != null ? entry.volume() : 100;
		SculptTextField field = Tooltips.describe(new SculptTextField(Minecraft.getInstance().font, FIELD_WIDTH,
				Component.translatable("soundsculptor.rule_editor.volume_typed")), Component.translatable("soundsculptor.trace.edit.volume_typed.tip"));
		field.setMaxLength(3);
		field.setValue(entry.volume() == null ? "" : entry.volume().toString());
		field.placeholder(Component.translatable("soundsculptor.trace.edit.volume_not_set"));
		EntryDraft check = EntryDraft.of(entry, false);
		field.validator(value -> {
			check.set(EntryDraft.Field.VOLUME, value);
			FieldError error = check.error(EntryDraft.Field.VOLUME);
			return error == null ? null : Component.translatable(error.translationKey(), error.args().toArray());
		});
		SculptSlider slider = Tooltips.describe(new SculptSlider(sliderWidth, Component.translatable("soundsculptor.rule_editor.volume"),
				new SliderModel(SetEntry.MIN_VOLUME, max, 5, 1, 100, Math.min(max, initial)),
				value -> Component.literal((int) value + "%"), null,
				value -> {
					if (!field.isFocused()) {
						field.setValue(Integer.toString((int) value));
					}
				}), Component.translatable("soundsculptor.rule_editor.volume.tip"));
		field.onChange(value -> {
			if (!field.isValid()) {
				return;
			}
			Integer level = value.isBlank() ? null : Integer.valueOf(value.trim());
			if (level != null && field.isFocused()) {
				slider.setTypedValue(level);
			}
			apply.accept(level);
		});
		SculptButton done = SculptButton.text(Component.translatable("soundsculptor.ui.done"), host::closeOverlay);
		SculptToggle cue = null;
		if (toggleCue != null) {
			Component label = Component.translatable("soundsculptor.sounds.level.cue");
			int cueWidth = Math.min(SculptToggle.widthFor(label), panelWidth - done.getWidth() - 3 * Theme.GAP);
			cue = Tooltips.describe(new SculptToggle(cueWidth, label, cueOn, value -> toggleCue.run()),
					Component.translatable("soundsculptor.sounds.level.cue.tip"));
		}

		int fieldRow = Theme.WIDGET_HEIGHT + SculptTextField.ERROR_SLOT_HEIGHT + Theme.GAP;
		int panelHeight = Theme.GAP + fieldRow * (limit == null ? 1 : 2) + Theme.WIDGET_HEIGHT + Theme.GAP;
		int x = (host.width - panelWidth) / 2;
		int y = Math.max(Theme.PADDING, (host.height - panelHeight) / 2);
		slider.setPosition(x + Theme.GAP, y + Theme.GAP);
		field.setPosition(slider.getRight() + Theme.GAP, y + Theme.GAP);
		List<AbstractWidget> widgets = new ArrayList<>(List.of(slider, field));
		int nextRow = y + Theme.GAP + fieldRow;
		if (limit != null) {
			limit.place(x + Theme.GAP, nextRow, Theme.GAP);
			limit.messagesUpTo(x + panelWidth - Theme.GAP);
			widgets.addAll(limit.widgets());
			nextRow += fieldRow;
		}
		done.setPosition(x + panelWidth - Theme.GAP - done.getWidth(), nextRow);
		if (cue != null) {
			cue.setPosition(x + Theme.GAP, nextRow);
			widgets.add(cue);
		}
		widgets.add(done);
		// Closing (Done, Esc, a click outside) completes a count or window still being typed before the caller finishes up.
		host.openOverlay(x, y, panelWidth, panelHeight, widgets, slider, () -> {
			if (limit != null) {
				limit.commit();
			}
			onClosed.run();
		});
		return new LevelPopup(slider, field, cue, limit);
	}

	/** Wide enough for the limit row when there is one, and never wider than the screen. */
	private static int panelWidth(SculptScreen host, LimitControls limit) {
		int wanted = limit == null ? PANEL_WIDTH : Math.max(PANEL_WIDTH, limit.width(Theme.GAP) + 2 * Theme.GAP);
		return Math.min(wanted, host.width - 2 * Theme.PADDING);
	}
}
