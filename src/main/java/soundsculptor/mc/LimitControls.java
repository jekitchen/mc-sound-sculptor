package soundsculptor.mc;

import java.util.List;
import java.util.Objects;
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
 * seconds, "s", and "Per source: On/Off". Used by the entry editor, the Sounds tab's pop-up and the trace's Level…
 * panel, so all three look and behave the same.
 *
 * <p>The values live in an {@link EntryDraft}, which checks them like every other editor field (FR-12.7): the count is
 * 1 to 100, the window 0.05 to 10 seconds. The limit is off by default and only the Limit toggle turns it on or off;
 * turning it on starts at 3 per 1 second, shared, or brings back what it had. An empty or invalid count or window is
 * just an invalid value: its error shows and nothing changes. While the limit is off, the count, the window and Per
 * source are greyed out and show no error.
 *
 * <p>A change takes effect ({@code onChange}: the limit, or {@code null} when it is off) when it is complete: a toggle
 * at once, a typed count or window on Enter, when focus leaves the field, or when the pop-up closes ({@link #commit}).
 * So typing 10 never applies 1 first, which would also start the counters afresh. The entry editor saves only on Save,
 * and follows every keystroke through {@link #afterEdit} to keep Save up to date.
 */
public final class LimitControls {
	private static final int COUNT_WIDTH = 30;
	private static final int WINDOW_WIDTH = 40;

	private final EntryDraft draft;
	private final Consumer<Limit> onChange;
	/** While set, changes come from {@link #show} or {@link #refresh}, not the player: they are not reported. */
	private boolean quiet;
	/** Runs after every change the player makes, valid or not (the entry editor updates Save with it). */
	private Runnable afterEdit = () -> { };
	/** Where error messages end; 0 for the row's last control. */
	private int messagesRight;
	/** The limit last handed to {@code onChange} (or shown), so a commit that changes nothing does nothing. */
	private Limit committed;
	public final SculptToggle toggle;
	public final SculptTextField countField;
	public final SculptText per;
	public final SculptTextField windowField;
	public final SculptText seconds;
	public final SculptToggle perSourceToggle;

	/**
	 * @param draft    holds the values; the entry editor passes its own, so Save and the unsaved-changes check see them
	 * @param onChange called with each complete, valid limit, or {@code null} when the limit is turned off
	 */
	public LimitControls(EntryDraft draft, Consumer<Limit> onChange) {
		this.draft = draft;
		this.onChange = onChange;
		this.committed = draft.limitValid() ? draft.limit() : null;
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
		countField.onChange(text -> edited());
		countField.onCommit(this::commit);
		per = new SculptText(Component.translatable("soundsculptor.limit.per"));
		windowField = Tooltips.describe(new SculptTextField(mc.font, WINDOW_WIDTH, Component.translatable("soundsculptor.limit.window")),
				Component.translatable("soundsculptor.limit.window.tip"));
		windowField.setMaxLength(6);
		windowField.setValue(draft.text(Field.LIMIT_WINDOW));
		windowField.validator(text -> {
			setText(Field.LIMIT_WINDOW, text);
			return message(draft.error(Field.LIMIT_WINDOW));
		});
		windowField.onChange(text -> edited());
		windowField.onCommit(this::commit);
		seconds = new SculptText(Component.translatable("soundsculptor.limit.seconds"));
		Component perSourceLabel = Component.translatable("soundsculptor.limit.per_source");
		perSourceToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(perSourceLabel), perSourceLabel, draft.perSource(), value -> {
			draft.setPerSource(value);
			edited();
			commit();
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

	/** Turns the limit on or off, as pressing the toggle does; takes effect at once. */
	public void setOn(boolean on) {
		draft.setLimitOn(on);
		quiet = true;
		try {
			// Turning it on may have filled in the starting count.
			countField.setValue(draft.text(Field.LIMIT_COUNT));
		} finally {
			quiet = false;
		}
		syncState();
		if (on && !draft.limitValid()) {
			revealErrors();
		}
		edited();
		commit();
	}

	/**
	 * Hands the limit to {@code onChange} if it is complete and valid and differs from the last one: what Enter, focus
	 * leaving a field and closing a pop-up do. An invalid value keeps its error showing and changes nothing.
	 */
	public void commit() {
		if (quiet) {
			return;
		}
		if (!draft.limitValid()) {
			revealErrors();
			return;
		}
		Limit limit = draft.limit();
		if (!Objects.equals(limit, committed)) {
			committed = limit;
			onChange.accept(limit);
		}
	}

	/** Shows {@code limit} (or off) without reporting a change, e.g. after another set's limit took over. */
	public void show(Limit limit) {
		quiet = true;
		try {
			draft.setLimitOn(limit != null);
			if (limit != null) {
				countField.setValue(Integer.toString(limit.count()));
				windowField.setValue(EntryDraft.seconds(limit.windowMs()));
				draft.setPerSource(limit.perSource());
				perSourceToggle.setValue(limit.perSource());
			}
		} finally {
			quiet = false;
		}
		committed = limit;
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

	/**
	 * The toggle shows the draft; the count, window and Per source take input only while the limit is on. Both fields are
	 * checked again, so turning the limit off clears their errors (outline, message and narration) and turning it back
	 * on shows any that still apply.
	 */
	private void syncState() {
		boolean on = draft.hasLimit();
		toggle.setValue(on);
		for (SculptTextField field : List.of(countField, windowField)) {
			field.active = on;
			field.setEditable(on);
			field.revalidate();
		}
		perSourceToggle.active = on;
	}

	/** Any change the player made, valid or not. */
	private void edited() {
		if (!quiet) {
			afterEdit.run();
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
