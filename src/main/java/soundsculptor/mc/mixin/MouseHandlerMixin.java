package soundsculptor.mc.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundsculptor.mc.PointerPresence;

/**
 * A pointer move means the pointer is in the game window, whether or not the window said it entered (PRD FR-1.3):
 * the window may miss the enter event, for example when it was created with the pointer already outside. Only a move
 * to another position counts: some systems repeat the last position, which says nothing about where the pointer is.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "onMove(JDDDD)V", at = @At("HEAD"))
	private void soundsculptor$pointerInWindow(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
		MouseHandler mouse = (MouseHandler) (Object) this;
		if (x != mouse.xpos() || y != mouse.ypos()) {
			PointerPresence.entered();
		}
	}
}
