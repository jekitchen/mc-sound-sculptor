package soundsculptor.mc.ui;

import soundsculptor.core.ui.SliderModel;
import soundsculptor.mc.compat.Keys;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Slider (PRD 6.2) on top of vanilla's slider. Keyboard: Left/Right move by one step, Shift+Left/Right by the fine
 * step. Values above the warning threshold draw a warning outline, and the narrator adds the warning text after the
 * value; the label itself shows only "Label: value" (the explanation belongs in the slider's tooltip). Typed values
 * come in through {@link #setTypedValue}.
 */
public class SculptSlider extends AbstractSliderButton {
	private final Component label;
	private final SliderModel model;
	private final DoubleFunction<Component> format;
	private final Component warningText;
	private final DoubleConsumer onChange;
	/** Text with a warning icon under the slider, or {@code null}; see {@link #noteBelow}. */
	private java.util.function.DoubleFunction<Component> note;
	private java.util.function.IntSupplier noteWidth;

	/**
	 * @param format      turns a value into display text, e.g. {@code v -> Component.literal((int) v + "%")}
	 * @param warningText narrated after the value in the warning zone, e.g. "louder than vanilla"; may be null if
	 *                    the model has no warning zone
	 */
	public SculptSlider(int width, Component label, SliderModel model, DoubleFunction<Component> format,
			Component warningText, DoubleConsumer onChange) {
		super(0, 0, width, Theme.WIDGET_HEIGHT, Component.empty(), model.fraction());
		this.label = label;
		this.model = model;
		this.format = format;
		this.warningText = warningText;
		this.onChange = onChange;
		updateMessage();
	}

	public double modelValue() {
		return model.value();
	}

	public boolean isWarning() {
		return model.isWarning();
	}

	/**
	 * Shows a warning icon and {@code note}'s text under the slider while it returns one for the value (FR-5.4: "May
	 * distort. Protect your hearing." above 100%), in the warning color. It is drawn in the error slot that a paired
	 * text field reserves on the row ({@link SculptTextField#ERROR_SLOT_HEIGHT}), and may run on under the field, up to
	 * {@code width} pixels from the slider's left edge; the field shows no error while the note shows. The narrator reads
	 * the note after the value instead of the fixed warning text.
	 */
	public SculptSlider noteBelow(java.util.function.DoubleFunction<Component> note, java.util.function.IntSupplier width) {
		this.note = note;
		this.noteWidth = width;
		return this;
	}

	/** The note shown under the slider now, or {@code null}. */
	public Component note() {
		return note == null ? null : note.apply(model.value());
	}

	/** A value typed into a paired number field. */
	public void setTypedValue(double typed) {
		double before = model.value();
		model.set(typed);
		syncFromModel(before);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		double before = model.value();
		int key = event.key();
		if (key == Keys.LEFT) {
			model.stepBy(-1, event.hasShiftDown());
		} else if (key == Keys.RIGHT) {
			model.stepBy(1, event.hasShiftDown());
		} else {
			return super.keyPressed(event);
		}
		syncFromModel(before);
		return true;
	}

	private void syncFromModel(double before) {
		this.value = model.fraction();
		updateMessage();
		if (model.value() != before) {
			onChange.accept(model.value());
		}
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.translatable("soundsculptor.ui.slider", label, format.apply(model.value())));
	}

	/** The narrator still hears the warning; it is not on the visible label. */
	@Override
	protected MutableComponent createNarrationMessage() {
		MutableComponent message = super.createNarrationMessage();
		Component shown = note();
		if (shown != null) {
			return Component.translatable("soundsculptor.ui.slider.warning", message, shown);
		}
		return model.isWarning() && warningText != null
				? Component.translatable("soundsculptor.ui.slider.warning", message, warningText)
				: message;
	}

	/** Mouse input: vanilla sets {@code value} to the pointer position; snap it to the step grid. */
	@Override
	protected void applyValue() {
		double before = model.value();
		model.setFraction(this.value);
		this.value = model.fraction();
		if (model.value() != before) {
			onChange.accept(model.value());
		}
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
		if (model.isWarning()) {
			Draw.outline(g, getX(), getY(), getWidth(), getHeight(), Theme.WARNING);
			if (isFocused()) {
				Draw.focusRing(g, getX(), getY(), getWidth(), getHeight());
			}
		}
		Component shown = note();
		if (shown != null) {
			net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
			int y = getBottom() + 2;
			Draw.sprite(g, WARNING_ICON, getX(), y, WARNING_ICON_WIDTH, WARNING_ICON_HEIGHT, Theme.WARNING);
			int textX = getX() + WARNING_ICON_WIDTH + 2;
			Draw.truncatedText(g, font, shown.getString(), textX, y, Math.max(0, noteWidth.getAsInt() - (textX - getX())), Theme.WARNING);
		}
	}

	/** The warning triangle drawn before a note (a white glyph, tinted). */
	private static final net.minecraft.resources.Identifier WARNING_ICON =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("soundsculptor", "icon/warning");
	public static final int WARNING_ICON_WIDTH = 9;
	public static final int WARNING_ICON_HEIGHT = 8;
}
