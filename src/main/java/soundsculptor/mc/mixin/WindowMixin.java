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
 *
 * <p>Priority 500 (the default is 1000) applies it before other mods' injections at the same point, so it sees the
 * events before one cancels them: Fabric's client game test framework cancels exactly these two, to keep the desktop
 * pointer out of tests. Sound Sculptor only reads the event and never cancels it.
 */
@Mixin(value = Window.class, priority = 500)
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
