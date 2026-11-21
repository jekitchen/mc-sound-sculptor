package soundsculptor.gametest.mixin;

import soundsculptor.gametest.TestClock;
import soundsculptor.mc.SoundHook;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Game tests only: the clock a sound start is decided with (repeat limits count with it, PRD FR-6.2) can be set by the
 * test, so 30 seconds of villager sounds take one tick and a slow CI runner cannot stretch a window. Off unless a test
 * sets {@link TestClock#fixedNow}.
 */
@Mixin(SoundHook.class)
abstract class LimiterClock {
	@Redirect(method = "handle", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private static long soundsculptorTest$now() {
		long now = TestClock.fixedNow != null ? TestClock.fixedNow : Util.getMillis();
		TestClock.lastDecided = now;
		return now;
	}
}
