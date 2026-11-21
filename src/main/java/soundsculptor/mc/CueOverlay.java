package soundsculptor.mc;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.cue.CueBox;
import soundsculptor.core.cue.CueColors;
import soundsculptor.core.cue.CueDirection;
import soundsculptor.core.cue.CueLine;
import soundsculptor.core.cue.CueField;
import soundsculptor.core.cue.CueResolution;
import soundsculptor.core.cue.CueSettings;
import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.mc.mixin.HudAccessor;
import soundsculptor.mc.mixin.SubtitleOverlayAccessor;
import soundsculptor.mc.ui.Draw;
import com.mojang.blaze3d.audio.ListenerTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * The muted-sound cue box (PRD FR-7.3 to FR-7.5): a HUD element drawn right after vanilla's subtitles, in its own box
 * directly above the vanilla subtitle lines at the bottom right, whether vanilla Subtitles is on or off (FR-7.7). Each
 * line reads "(muted)", a muted-speaker icon, then the sound's subtitle (or its ID, FR-7.2) and "×N" for repeats, all in
 * italics and in the cue text color; the "(muted)" prefix and the italics tell it from a subtitle without color. The
 * box is laid out like vanilla's: one background row per line, 10 GUI pixels apart, the same width for every line,
 * vanilla's {@code <} and {@code >} arrows at its edges, worked out every frame from where the listener is (FR-7.4), and
 * a line is hidden while the listener is out of the sound's range (FR-7.1). Each line tracks its plays like a vanilla
 * subtitle ({@link CueBox}) and fades over 3 seconds times vanilla's Notification Time option. The cue text size (A-7, 50%
 * to 200%, never under one screen pixel per font pixel) grows or shrinks the box up and left from its place, and the
 * cue box offset (section 8.1) moves it; it always stays on screen.
 *
 * <p>{@link #add} is called by the sound hook for each muted sound whose cue is due, on the hot path: it allocates
 * nothing once a sound ID's text is cached. Drawing may allocate. Render thread only.
 */
public final class CueOverlay {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(SoundSculptorClient.MOD_ID, "muted_sound_cues");
	/** A white 9 × 7 muted speaker, drawn in the cue text color. */
	private static final Identifier ICON = Identifier.fromNamespaceAndPath(SoundSculptorClient.MOD_ID, "icon/cue_muted");
	private static final int ICON_WIDTH = 9;
	private static final int ICON_HEIGHT = 7;
	/** Vanilla's subtitle rows: the bottom row's center is this far above the bottom, each row this tall. */
	static final int BOTTOM_ROW_OFFSET = 35;
	static final int ROW_HEIGHT = 10;
	/** Room between vanilla's subtitle box and the cue box, so they read as two boxes. */
	static final int GAP_ABOVE_SUBTITLES = 2;
	private static final int RIGHT_MARGIN = 2;

	private static final CueBox<Component> BOX = new CueBox<>();
	/** "(muted)" texts for sounds without a subtitle (FR-7.2), so a repeat makes no new text. */
	private static final Map<String, Component> ID_TEXTS = new HashMap<>();
	private static List<Shown> shown = List.of();


	/**
	 * One line as last drawn, for tests: the text after "(muted)" without the count, the count, the arrow and the line's
	 * box in GUI pixels (left, top, width, height) after the cue text scale, and the text color.
	 */
	public record Shown(String text, int count, CueDirection arrow, int x, int y, int width, int height, int argb) {
	}

	private CueOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, ID, CueOverlay::extract);
	}

	/**
	 * A muted sound with its cue due (FR-7.1): its line appears, or its line's count goes up. Hot path.
	 *
	 * @param subtitle the sound's subtitle, or {@code null} to show the sound ID (FR-7.2)
	 * @param range    the sound's vanilla hearing range, infinite for sounds without a position or attenuation
	 */
	static void add(Component subtitle, String soundId, boolean positional, boolean looping, double x, double y, double z,
			float range, long nowMs) {
		Component text = subtitle;
		if (text == null) {
			text = ID_TEXTS.get(soundId);
			if (text == null) {
				text = Component.literal(soundId);
				ID_TEXTS.put(soundId, text);
			}
		}
		BOX.add(text, positional, looping, x, y, z, range, nowMs, timeScale(Minecraft.getInstance()));
	}

	/** Vanilla's Notification Time option, which scales how long subtitles, and so cues, stay up. */
	private static double timeScale(Minecraft minecraft) {
		return minecraft.options.notificationDisplayTime().get();
	}

	/** Removes every line, e.g. for a test. */
	public static void clear() {
		BOX.clear();
		shown = List.of();
	}

	/** The lines drawn in the last frame, bottom first; empty when none were. Render thread. */
	public static List<Shown> shown() {
		return shown;
	}

	/**
	 * The optional hotkey (FR-7.7): turns "Show muted-sound cues" on or off in the place that decides it where the player
	 * stands (the smallest zone there that sets it, or Everywhere), saves, and says which in the action bar, naming the
	 * zone ("Muted-sound cues: Off in Main base"). Does nothing outside a world.
	 *
	 * @return the message shown, or {@code null} outside a world
	 */
	public static Component toggleShowCues(Minecraft minecraft) {
		if (!ProfileManager.isLoaded()) {
			return null;
		}
		CueResolution cues = SoundHook.cuesNow();
		PlaceRef place = cues.source(CueField.SHOW_CUES);
		boolean on = !cues.settings().showCues();
		ProfileManager.update(profile -> {
			PlaceCues current = profile.cues(place);
			return current == null ? profile : profile.withCues(place, current.withShowCues(on));
		});
		String key = on ? "soundsculptor.cue.hotkey.on" : "soundsculptor.cue.hotkey.off";
		Component message = place.isEverywhere() ? Component.translatable(key)
				: Component.translatable(key + "_in", SetDeleteDialog.placeName(ProfileManager.profile(), place));
		minecraft.gui.hud.setOverlayMessage(message, false);
		return message;
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();
		double timeScale = timeScale(minecraft);
		if (!ProfileManager.isLoaded() || minecraft.level == null) {
			BOX.clear();
			shown = List.of();
			return;
		}
		// The look (color, text size, position) follows where the player stands now, for the whole box at once.
		CueSettings settings = SoundHook.cuesNow().settings();
		// FR-7.7: the master switch hides every cue, and lines do not wait to come back when it is turned on again. Whether
		// a sound gets a cue is decided when it starts; walking into a place that turns cues off hides those shown too.
		if (!settings.showCues()) {
			BOX.clear();
			shown = List.of();
			return;
		}
		if (BOX.isEmpty(now, timeScale)) {
			shown = List.of();
			return;
		}
		ListenerTransform listener = minecraft.getSoundManager().getListenerTransform();
		Vec3 at = listener.position();
		Vec3 forward = listener.forward();
		Vec3 right = listener.right();
		// Each line's best play for where the listener is now; lines out of range are left out (FR-7.1). Oldest first.
		List<CueLine<Component>> lines = BOX.lines(now, timeScale, at.x(), at.y(), at.z());
		if (lines.isEmpty()) {
			shown = List.of();
			return;
		}

		Font font = minecraft.font;
		Component prefix = Component.translatable("soundsculptor.cue.prefix").withStyle(ChatFormatting.ITALIC);
		int prefixWidth = font.width(prefix);
		int space = font.width(" ");
		List<Component> bodies = new ArrayList<>(lines.size());
		int widest = 0;
		for (CueLine<Component> line : lines) {
			Component body = (line.count() > 1 ? Component.translatable("soundsculptor.cue.count", line.text(), line.count())
					: line.text().copy()).withStyle(ChatFormatting.ITALIC);
			bodies.add(body);
			widest = Math.max(widest, contentWidth(prefixWidth, space, font.width(body)));
		}
		// Room for the arrows, exactly as vanilla adds it.
		int width = widest + font.width("<") + space + font.width(">") + space;
		int halfWidth = width / 2;
		int halfHeight = font.lineHeight / 2;
		// Each row's background, as vanilla fills it: from -half - 1 to half + 1 around the row's center.
		int boxWidth = 2 * halfWidth + 2;
		int boxHeight = 2 * halfHeight + 2;

		// The box is laid out around an anchor: its right edge and the bottom row's center. Unscaled and with no offset it
		// is where vanilla's next subtitle row would go; the text scale (A-7) grows it up and left from there, and the
		// offset (section 8.1) moves it, but never off the screen.
		// Below 100% the text never gets smaller than one screen pixel per font pixel: at GUI scale 1, 50% would blur.
		float scale = Math.max(settings.textScale() / 100F, 1F / (float) minecraft.getWindow().getGuiScale());
		int subtitleRows = vanillaSubtitleRows(minecraft);
		float anchorX = g.guiWidth() - RIGHT_MARGIN + settings.offsetX();
		float anchorY = g.guiHeight() - BOTTOM_ROW_OFFSET - subtitleRows * ROW_HEIGHT - (subtitleRows > 0 ? GAP_ABOVE_SUBTITLES : 0)
				+ settings.offsetY();
		// In the anchor's units: rows go up from 0; the box spans x from -(boxWidth - 1) to 1.
		int relLeft = -boxWidth + 1;
		int relTop = -(lines.size() - 1) * ROW_HEIGHT - halfHeight - 1;
		int relBottom = halfHeight + 1;
		anchorX = clamp(anchorX, -relLeft * scale, g.guiWidth() - scale);
		anchorY = clamp(anchorY, -relTop * scale, g.guiHeight() - relBottom * scale);
		int background = minecraft.options.getBackgroundColor(0.8F);

		List<Shown> drawn = new ArrayList<>(lines.size());
		g.nextStratum();
		g.pose().pushMatrix();
		g.pose().translate(anchorX, anchorY);
		g.pose().scale(scale, scale);
		int centerX = -halfWidth;
		for (int row = 0; row < lines.size(); row++) {
			CueLine<Component> line = lines.get(row);
			Component body = bodies.get(row);
			int centerY = -row * ROW_HEIGHT;
			int color = CueColors.faded(settings.color(), line.fadeFraction());
			int left = centerX - halfWidth - 1;
			int top = centerY - halfHeight - 1;
			Draw.fill(g, left, top, boxWidth, boxHeight, background);
			CueDirection arrow = line.direction(at.x(), at.y(), at.z(), forward.x(), forward.y(), forward.z(), right.x(), right.y(), right.z());
			int textY = centerY - halfHeight;
			if (arrow == CueDirection.RIGHT) {
				Draw.text(g, font, ">", centerX + halfWidth - font.width(">"), textY, color);
			} else if (arrow == CueDirection.LEFT) {
				Draw.text(g, font, "<", centerX - halfWidth, textY, color);
			}
			int bodyWidth = font.width(body);
			int x = centerX - contentWidth(prefixWidth, space, bodyWidth) / 2;
			Draw.text(g, font, prefix, x, textY, color);
			x += prefixWidth + space;
			// The icon sits on the text's baseline, with a shadow like the text's.
			Draw.sprite(g, ICON, x + 1, textY + 1, ICON_WIDTH, ICON_HEIGHT, shadow(color));
			Draw.sprite(g, ICON, x, textY, ICON_WIDTH, ICON_HEIGHT, color);
			x += ICON_WIDTH + space;
			Draw.text(g, font, body, x, textY, color);
			drawn.add(new Shown(line.text().getString(), line.count(), arrow, Math.round(anchorX + left * scale),
					Math.round(anchorY + top * scale), Math.round(boxWidth * scale), Math.round(boxHeight * scale), color));
		}
		g.pose().popMatrix();
		shown = List.copyOf(drawn);
	}

	/** {@code value} kept within {@code min} and {@code max}; {@code min} wins when the box is larger than the screen. */
	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	/** "(muted)", a space, the icon, a space and the text. */
	private static int contentWidth(int prefixWidth, int space, int bodyWidth) {
		return prefixWidth + space + ICON_WIDTH + space + bodyWidth;
	}

	/** The text shadow's color, as the font makes it: a quarter of the brightness. */
	private static int shadow(int argb) {
		int r = ((argb >> 16) & 0xFF) / 4;
		int gr = ((argb >> 8) & 0xFF) / 4;
		int b = (argb & 0xFF) / 4;
		return (argb & 0xFF000000) | r << 16 | gr << 8 | b;
	}

	/**
	 * How many lines vanilla's subtitle box showed in its last frame, so the cue box sits directly above it; 0 with
	 * Subtitles off. Vanilla keeps its last list when Subtitles is turned off, so the option is checked first.
	 */
	static int vanillaSubtitleRows(Minecraft minecraft) {
		if (!minecraft.options.showSubtitles().get()) {
			return 0;
		}
		return ((SubtitleOverlayAccessor) ((HudAccessor) minecraft.gui.hud).soundsculptor$subtitleOverlay())
				.soundsculptor$audibleSubtitles().size();
	}
}
