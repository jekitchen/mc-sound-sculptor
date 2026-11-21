package soundsculptor.mc.ui;

import soundsculptor.core.ui.Truncation;
import soundsculptor.mc.compat.RuntimeLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The drawing adapter (PRD 6.2): every Sound Sculptor component draws through this class and nothing else, so a
 * change to Minecraft's drawing API is fixed here once. Listed in docs/PORTING.md.
 *
 * <p>Coordinates are GUI pixels; colors are ARGB.
 */
public final class Draw {
	/**
	 * {@code blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height)} as
	 * {@code (GuiGraphicsExtractor, Identifier, int, int, int, int) -> void}. Found by name when this class loads because
	 * the pipeline's type moved package in 26.3 ({@code com.mojang.blaze3d.pipeline.RenderPipeline} to
	 * {@code com.mojang.renderpearl.api.pipeline.RenderPipeline}), and naming it in code would tie the jar to one
	 * version (docs/PORTING.md, "One jar for every version").
	 */
	private static final MethodHandle BLIT_GUI_SPRITE = guiSpriteBlitter(false);
	/** The same with a tint color last: {@code (GuiGraphicsExtractor, Identifier, int, int, int, int, int) -> void}. */
	private static final MethodHandle BLIT_TINTED_GUI_SPRITE = guiSpriteBlitter(true);

	private Draw() {
	}

	public static void fill(GuiGraphicsExtractor g, int x, int y, int width, int height, int argb) {
		g.fill(x, y, x + width, y + height, argb);
	}

	/** A 1-pixel outline just inside the given rectangle. */
	public static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int argb) {
		g.outline(x, y, width, height, argb);
	}

	/** The keyboard focus ring (PRD A-1: visible, at least 1 GUI pixel), drawn 1 pixel outside the widget. */
	public static void focusRing(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.outline(x - 1, y - 1, width + 2, height + 2, Theme.FOCUS);
	}

	public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int argb) {
		g.text(font, text, x, y, argb, true);
	}

	public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int argb) {
		g.text(font, text, x, y, argb, true);
	}

	public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int argb) {
		g.text(font, text, x, y, argb, true);
	}

	/** The text renderer vanilla buttons use for their own label (pass to {@code extractDefaultLabel}). */
	public static ActiveTextCollector widgetLabel(GuiGraphicsExtractor g, AbstractWidget widget) {
		return g.textRendererForWidget(widget, GuiGraphicsExtractor.HoveredTextEffects.NONE);
	}

	public static void centeredText(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, int argb) {
		g.centeredText(font, text, centerX, y, argb);
	}

	/** Draws text cut to {@code maxWidth} with an ellipsis; returns whether it had to be cut. */
	public static boolean truncatedText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int argb) {
		String shown = truncate(font, text, maxWidth);
		g.text(font, shown, x, y, argb, true);
		return Truncation.isTruncated(text, shown);
	}

	public static String truncate(Font font, String text, int maxWidth) {
		return Truncation.truncate(text, maxWidth, font::width);
	}

	/** A sprite from the GUI atlas, e.g. {@code soundsculptor:icon/mute} for textures/gui/sprites/icon/mute.png. */
	public static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int width, int height) {
		try {
			BLIT_GUI_SPRITE.invokeExact(g, sprite, x, y, width, height);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	/**
	 * A sprite from the GUI atlas drawn in {@code argb}: a white sprite takes the color, e.g. the cue box's muted-speaker
	 * icon in the cue text color.
	 */
	public static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int width, int height, int argb) {
		try {
			BLIT_TINTED_GUI_SPRITE.invokeExact(g, sprite, x, y, width, height, argb);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	private static MethodHandle guiSpriteBlitter(boolean tinted) {
		Field pipeline = RuntimeLookup.staticField(RenderPipelines.class, "GUI_TEXTURED");
		MethodType type = MethodType.methodType(void.class, pipeline.getType(), Identifier.class, int.class, int.class, int.class, int.class);
		MethodHandle blitSprite = RuntimeLookup.virtualMethod(GuiGraphicsExtractor.class, "blitSprite",
				tinted ? type.appendParameterTypes(int.class) : type);
		return MethodHandles.insertArguments(blitSprite, 1, RuntimeLookup.staticValue(RenderPipelines.class, "GUI_TEXTURED"));
	}

	/** Restricts drawing to a rectangle until {@link #popClip}. Calls nest. */
	public static void pushClip(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		g.enableScissor(x, y, x + width, y + height);
	}

	public static void popClip(GuiGraphicsExtractor g) {
		g.disableScissor();
	}

	/** A tooltip at the mouse (or given) position this frame; vanilla keeps it on screen. */
	public static void tooltip(GuiGraphicsExtractor g, Font font, Component text, int x, int y) {
		g.setTooltipForNextFrame(font, text, x, y);
	}
}
