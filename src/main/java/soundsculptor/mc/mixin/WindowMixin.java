package soundsculptor.mc.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundsculptor.mc.PointerPresence;

/**
 * Tells {@link PointerPresence} when the mouse pointer leaves or enters the game window, so the Sounds list is not held
 * after the pointer left it (PRD FR-1.3). Vanilla handles both events the same way and keeps no state for them.
 */
@Mixin(Window.class)
abstract class WindowMixin {
	@Inject(method = "handleEvent(Lorg/lwjgl/sdl/SDL_Event;)V", at = @At("HEAD"))
	private void soundsculptor$pointerPresence(SDL_Event event, CallbackInfo ci) {
		int type = event.type();
		if (type == SDLEvents.SDL_EVENT_WINDOW_MOUSE_LEAVE) {
			PointerPresence.left();
		} else if (type == SDLEvents.SDL_EVENT_WINDOW_MOUSE_ENTER) {
			PointerPresence.entered();
		}
	}
}
