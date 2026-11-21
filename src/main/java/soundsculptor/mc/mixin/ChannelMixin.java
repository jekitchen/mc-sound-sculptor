package soundsculptor.mc.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundsculptor.mc.BoostDevice;

/**
 * Boost (PRD 7.2, "Channel gain"): OpenAL caps each source's gain at {@code AL_MAX_GAIN}, 1.0 by default, so a gain of
 * 2.0 would play at 1.0. Each new channel's source gets a cap of 2.0 as soon as vanilla creates it. Vanilla creates
 * channels only on the sound engine's own thread ({@code ChannelAccess.createHandle} runs {@code Library.acquireChannel}
 * on its executor, which calls {@code Channel.create}), so the OpenAL calls stay on that thread. The cap changes
 * nothing for vanilla's own gains, which are never above 1.0, and nothing about range: that is the source's
 * {@code AL_MAX_DISTANCE}, set by {@code linearAttenuation} from the un-boosted volume.
 */
@Mixin(Channel.class)
abstract class ChannelMixin {
	@Inject(method = "<init>(I)V", at = @At("TAIL"))
	private void soundsculptor$raiseMaxGain(int source, CallbackInfo ci) {
		BoostDevice.onChannelCreated(source);
	}
}
