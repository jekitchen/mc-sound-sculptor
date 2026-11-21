package soundsculptor.mc.mixin;

import soundsculptor.mc.SoundHook;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.WeighedSoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sound hooks (PRD 7.2). The same methods and calls exist in 26.2 and 26.3 (see docs/PORTING.md).
 *
 * <ul>
 *   <li><b>Start:</b> just before {@code play} calls {@code Sound.getAttenuationDistance(float)}. By then the sound
 *   is resolved and the unknown/empty-sound early returns are behind us, and the subtitle listeners are not notified
 *   yet. A muted sound returns {@code NOT_STARTED} here: no channel is created and no subtitle shows.</li>
 *   <li><b>Subtitles:</b> {@code play} notifies the subtitle listeners only for a sound Sound Sculptor does not
 *   silence. A muted loop, which still starts silently, gets no vanilla subtitle either (decided 2026-11-21: a muted
 *   sound shows at most its cue, FR-7). Vanilla's own zero sliders keep vanilla behaviour.</li>
 *   <li><b>Start volume:</b> the result of that same call is multiplied by the rule volume (FR-5.3). Vanilla then
 *   skips a sound whose volume became zero.</li>
 *   <li><b>Later volume:</b> {@code calculateVolume(SoundInstance)} is what vanilla calls to recalculate a playing
 *   sound (moving sounds every tick, all sounds of a category after a slider change); its result gets the same
 *   factor. {@code play} does not call it, so the factor is never applied twice.</li>
 * </ul>
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	private static final String PLAY =
			"play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;";
	private static final String ATTENUATION_DISTANCE =
			"Lnet/minecraft/client/resources/sounds/Sound;getAttenuationDistance(F)F";
	private static final String NOTIFY_LISTENERS = "Lnet/minecraft/client/sounds/SoundEngine;notifyListeners("
			+ "Lnet/minecraft/client/resources/sounds/SoundInstance;Lnet/minecraft/client/sounds/WeighedSoundEvents;F)V";
	private static final String LIBRARY_INIT =
			"Lcom/mojang/blaze3d/audio/Library;init(Ljava/lang/String;Lcom/mojang/blaze3d/audio/DeviceList;Z)V";
	private static final String CALCULATE_VOLUME =
			"Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F";

	@Inject(method = PLAY, at = @At(value = "INVOKE", target = ATTENUATION_DISTANCE), cancellable = true)
	private void soundsculptor$onSoundStart(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (SoundHook.onSoundStart(instance)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@WrapWithCondition(method = PLAY, at = @At(value = "INVOKE", target = NOTIFY_LISTENERS))
	private boolean soundsculptor$subtitles(SoundEngine engine, SoundInstance instance, WeighedSoundEvents events, float range) {
		return SoundHook.notifiesSubtitles();
	}

	@ModifyExpressionValue(method = PLAY, at = @At(value = "INVOKE", target = CALCULATE_VOLUME))
	private float soundsculptor$startVolume(float vanillaVolume) {
		return SoundHook.applyStartVolume(vanillaVolume);
	}

	/**
	 * Boost (PRD 7.2): the sound engine opens an audio device again (a reload, or the device changed), so what the last
	 * device said about the raised gain cap no longer holds. The next channel asks the new device.
	 */
	@Inject(method = "loadLibrary()V", at = @At(value = "INVOKE", target = LIBRARY_INIT))
	private void soundsculptor$newDevice(CallbackInfo ci) {
		soundsculptor.core.boost.BoostSupport.reset();
	}

	@ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
	private float soundsculptor$volume(float vanillaVolume, @Local(argsOnly = true) SoundInstance instance) {
		return SoundHook.applyVolume(instance, vanillaVolume);
	}
}
