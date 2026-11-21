package soundsculptor.gametest.mixin;

import soundsculptor.gametest.VanillaSubtitles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Game tests only: records the arrows and texts vanilla's subtitle overlay draws (26.3: an arrow is drawn with the
 * String {@code text} call just before its row's Component {@code text} call), so the M9 test can compare the cue box's
 * arrow with the one vanilla really shows.
 */
@Mixin(SubtitleOverlay.class)
abstract class SubtitleOverlayRecorder {
	private static final String TEXT_STRING =
			"Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V";
	private static final String TEXT_COMPONENT =
			"Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V";

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void soundsculptorTest$startFrame(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		VanillaSubtitles.startFrame();
	}

	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = TEXT_STRING), index = 1)
	private String soundsculptorTest$arrow(String arrow) {
		VanillaSubtitles.arrow(arrow);
		return arrow;
	}

	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE", target = TEXT_COMPONENT), index = 1)
	private Component soundsculptorTest$text(Component text) {
		VanillaSubtitles.text(text.getString());
		return text;
	}
}
