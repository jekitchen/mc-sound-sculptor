package soundsculptor.mc;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import soundsculptor.core.editor.EntryDraft;
import soundsculptor.core.editor.EntryDraft.Field;
import soundsculptor.core.editor.FieldError;
import soundsculptor.core.sets.Limit;
import soundsculptor.mc.ui.SculptText;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Tooltips;

/**
 * An entry's repeat limit (PRD FR-6.1) as one row of controls: "Limit: On/Off", the count, "per", the window in
 * seconds, "s", and "Per source: On/Off". Used by the entry editor, the Sounds tab's pop-up and the trace's Limit…
 * pop-up, so all three look and behave the same.
 *
 * <p>The values live in an {@link EntryDraft}, which checks them like every other editor field (FR-12.7): the count is
 * 1 to 100, the window 0.05 to 10 seconds. The limit is off by default; turning it on starts at 3 per 1 second, shared
 * (or what was typed before it was turned off). Typing a count turns it on, emptying the count turns it off, as an
 * empty Level means "Unchanged". While it is off the window and Per source are greyed out. Every valid change is handed
 * to {@code onChange}: the limit, or {@code null} when it is off; an invalid value shows its error and changes nothing.
 */
public final class LimitControls {
	private static final int COUNT_WIDTH = 30;
	private static final int WINDOW_WIDTH = 40;

	private final EntryDraft draft;
	private final Consumer<Limit> onChange;
	/** While set, field changes come from {@link #show} or {@link #refresh}, not the player: they are not reported. */
	private boolean quiet;
	/** Runs after every change the player makes, valid or not (the entry editor updates Save with it). */
	private Runnable afterEdit = () -> { };
	/** Where error messages end; 0 for the row's last control. */
	private int messagesRight;
	public final SculptToggle toggle;
	public final SculptTextField countField;
	public final SculptText per;
	public final SculptTextField windowField;
	public final SculptText seconds;
	public final SculptToggle perSourceToggle;

	/**
	 * @param draft    holds the values; the entry editor passes its own, so Save and the unsaved-changes check see them
	 * @param onChange called with each valid limit, or {@code null} when the limit is turned off
	 */
	public LimitControls(EntryDraft draft, Consumer<Limit> onChange) {
		this.draft = draft;
		this.onChange = onChange;
		Minecraft mc = Minecraft.getInstance();
		Component limitLabel = Component.translatable("soundsculptor.limit");
		toggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(limitLabel), limitLabel, draft.hasLimit(), this::setOn),
				Component.translatable("soundsculptor.limit.tip"));
		countField = Tooltips.describe(new SculptTextField(mc.font, COUNT_WIDTH, Component.translatable("soundsculptor.limit.count")),
				Component.translatable("soundsculptor.limit.count.tip"));
		countField.setMaxLength(3);
		countField.setValue(draft.text(Field.LIMIT_COUNT));
		countField.placeholder(Component.translatable("soundsculptor.limit.count.hint"));
		countField.validator(text -> {
			setText(Field.LIMIT_COUNT, text);
			return message(draft.error(Field.LIMIT_COUNT));
		});
		countField.onChange(text -> {
			syncState();
			changed();
		});
		per = new SculptText(Component.translatable("soundsculptor.limit.per"));
		windowField = Tooltips.describe(new SculptTextField(mc.font, WINDOW_WIDTH, Component.translatable("soundsculptor.limit.window")),
				Component.translatable("soundsculptor.limit.window.tip"));
		windowField.setMaxLength(6);
		windowField.setValue(draft.text(Field.LIMIT_WINDOW));
		windowField.validator(text -> {
			setText(Field.LIMIT_WINDOW, text);
			return message(draft.error(Field.LIMIT_WINDOW));
		});
		windowField.onChange(text -> changed());
		seconds = new SculptText(Component.translatable("soundsculptor.limit.seconds"));
		Component perSourceLabel = Component.translatable("soundsculptor.limit.per_source");
		perSourceToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(perSourceLabel), perSourceLabel, draft.perSource(), value -> {
			draft.setPerSource(value);
			changed();
		}), Component.translatable("soundsculptor.limit.per_source.tip"));
		// The fields are narrow: their messages use the room under the row, from the count to the row's end. The count's
		// comes first; the window's shows when the count has none, so the two never overlap (the outline says which).
		countField.errorMessage(countField::getX, () -> messagesRight() - countField.getX(), () -> true);
		windowField.errorMessage(countField::getX, () -> messagesRight() - countField.getX(), () -> !countField.isErrorShown());
		syncState();
	}

	/** Error messages may run up to {@code right} (the body's or the pop-up's edge) instead of the row's last control. */
	public LimitControls messagesUpTo(int right) {
		this.messagesRight = right;
		return this;
	}

	private int messagesRight() {
		return messagesRight > 0 ? messagesRight : perSourceToggle.getRight();
	}

	/** The controls left to right, in reading and Tab order. */
	public List<AbstractWidget> widgets() {
		return List.of(toggle, countField, per, windowField, seconds, perSourceToggle);
	}

	/** Width of the row with {@code gap} between controls. */
	public int width(int gap) {
		int width = 0;
		for (AbstractWidget widget : widgets()) {
			width += widget.getWidth() + gap;
		}
		return width - gap;
	}

	/** Places the controls on one row from {@code (x, y)}. */
	public void place(int x, int y, int gap) {
		int at = x;
		for (AbstractWidget widget : widgets()) {
			widget.setPosition(at, y);
			at += widget.getWidth() + gap;
		}
	}

	/** Turns the limit on or off, as pressing the toggle does. */
	public void setOn(boolean on) {
		draft.setLimitOn(on);
		// Through the field, so its text, error and the draft agree; its change handler then reports the change.
		countField.setValue(draft.text(Field.LIMIT_COUNT));
		if (on && !draft.limitValid()) {
			countField.revealError();
			windowField.revealError();
		}
	}

	/** Shows {@code limit} (or off) without reporting a change, e.g. after another set's limit took over. */
	public void show(Limit limit) {
		quiet = true;
		try {
			if (limit == null) {
				draft.setLimitOn(false);
				countField.setValue("");
			} else {
				countField.setValue(Integer.toString(limit.count()));
				windowField.setValue(EntryDraft.seconds(limit.windowMs()));
				draft.setPerSource(limit.perSource());
				perSourceToggle.setValue(limit.perSource());
			}
		} finally {
			quiet = false;
		}
		syncState();
	}

	/** The controls' values, after a reset of the draft's limit (FR-12.6). */
	public void refresh() {
		quiet = true;
		try {
			countField.setValue(draft.text(Field.LIMIT_COUNT));
			windowField.setValue(draft.text(Field.LIMIT_WINDOW));
			perSourceToggle.setValue(draft.perSource());
		} finally {
			quiet = false;
		}
		syncState();
	}

	/** Reveals the errors of both fields, e.g. when Save is pressed. */
	public void revealErrors() {
		countField.revealError();
		windowField.revealError();
	}

	/** The toggle follows the count; the window and Per source matter only while the limit is on. */
	private void syncState() {
		boolean on = draft.hasLimit();
		toggle.setValue(on);
		windowField.active = on;
		windowField.setEditable(on);
		perSourceToggle.active = on;
	}

	private void changed() {
		if (quiet) {
			return;
		}
		afterEdit.run();
		if (draft.limitValid()) {
			onChange.accept(draft.limit());
		}
	}

	/** Runs {@code action} after every change the player makes, valid or not; returns this row. */
	public LimitControls afterEdit(Runnable action) {
		this.afterEdit = action;
		return this;
	}

	/** Only a real change counts as an edit, so an untouched field shows no error (6.2). */
	private void setText(Field field, String text) {
		if (!text.equals(draft.text(field))) {
			draft.set(field, text);
		}
	}

	private static Component message(FieldError error) {
		return error == null ? null : Component.translatable(error.translationKey(), error.args().toArray());
	}

}
