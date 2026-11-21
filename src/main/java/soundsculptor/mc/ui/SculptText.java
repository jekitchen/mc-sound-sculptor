package soundsculptor.mc.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A short piece of text between controls on one row (PRD 6.2), such as the units in "3 per 1 s": exactly as wide as
 * its text, so it never cuts it, and as tall as a control, with the text centred on the row. It is not a control: Tab
 * skips it and the narrator does not stop on it, so the controls beside it carry the full meaning in their own labels.
 */
public class SculptText extends AbstractWidget {
	public SculptText(Component text) {
		super(0, 0, Minecraft.getInstance().font.width(text), Theme.WIDGET_HEIGHT, text);
		this.active = false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
		Draw.text(g, font, getMessage(), getX(), getY() + (getHeight() - font.lineHeight) / 2 + 1, Theme.TEXT);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		// Not a control: the fields beside it say what they hold.
	}
}
