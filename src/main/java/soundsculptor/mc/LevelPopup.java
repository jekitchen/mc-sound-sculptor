package soundsculptor.mc;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import soundsculptor.core.boost.BoostWarning;
import soundsculptor.core.editor.EntryDraft;
import soundsculptor.core.editor.FieldError;
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
 * The Level pop-up shared by the Sounds tab's Level… (PRD FR-1.5) and the sound trace's inline Level… (FR-14.4): a
 * slider (5% steps, Shift for 1%) and a number field over the screen, with Done. Each valid change is handed to the
 * caller at once; an empty field hands {@code null} (the entry sets no level). Values are checked like the entry
 * editor's Level field (FR-12.7), so an out-of-range number shows its error and changes nothing. The slider starts
 * focused; Esc, Done or a click outside closes it.
 *
 * <p>The Sounds tab's pop-up also has a Cue on/off toggle beside Done (FR-1.5, FR-7): six icon buttons do not fit a
 * row at 854 × 480, so Cue lives here rather than on the row. The trace has its own Cue button on each line.
 */
public final class LevelPopup {
	private static final int PANEL_WIDTH = 240;
	private static final int FIELD_WIDTH = 40;

	public final SculptSlider slider;
	public final SculptTextField field;
	/** The Cue toggle, or {@code null} in a pop-up without one. */
	public final SculptToggle cue;

	private LevelPopup(SculptSlider slider, SculptTextField field, SculptToggle cue) {
		this.slider = slider;
		this.field = field;
		this.cue = cue;
	}

	/** A pop-up with no Cue toggle (the trace's Level…). */
	public static LevelPopup open(SculptScreen host, SetEntry entry, Consumer<Integer> apply, Runnable onClosed) {
		return open(host, entry, apply, onClosed, false, null);
	}

	/**
	 * Opens the pop-up on {@code host}.
	 *
	 * @param entry    the entry whose level it starts from (100% when it sets none) and checks values like
	 * @param apply    called with each valid new level, or {@code null} when the field is emptied
	 * @param onClosed runs after the pop-up closes for any reason but a rebuild of the screen
	 * @param cueOn    the Cue toggle's state when it opens
	 * @param toggleCue turns the cue on or off and sets the toggle to the state that results; {@code null} for no toggle
	 */
	public static LevelPopup open(SculptScreen host, SetEntry entry, Consumer<Integer> apply, Runnable onClosed, boolean cueOn,
			Runnable toggleCue) {
		int max = BoostWarning.sliderMax(false);
		int panelWidth = Math.min(PANEL_WIDTH, host.width - 2 * Theme.PADDING);
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

		int panelHeight = 2 * Theme.WIDGET_HEIGHT + SculptTextField.ERROR_SLOT_HEIGHT + 3 * Theme.GAP;
		int x = (host.width - panelWidth) / 2;
		int y = Math.max(Theme.PADDING, (host.height - panelHeight) / 2);
		slider.setPosition(x + Theme.GAP, y + Theme.GAP);
		field.setPosition(slider.getRight() + Theme.GAP, y + Theme.GAP);
		int secondRow = y + 2 * Theme.GAP + Theme.WIDGET_HEIGHT + SculptTextField.ERROR_SLOT_HEIGHT;
		done.setPosition(x + panelWidth - Theme.GAP - done.getWidth(), secondRow);
		List<net.minecraft.client.gui.components.AbstractWidget> widgets = List.of(slider, field, done);
		if (cue != null) {
			cue.setPosition(x + Theme.GAP, secondRow);
			widgets = List.of(slider, field, cue, done);
		}
		host.openOverlay(x, y, panelWidth, panelHeight, widgets, slider, onClosed);
		return new LevelPopup(slider, field, cue);
	}
}
