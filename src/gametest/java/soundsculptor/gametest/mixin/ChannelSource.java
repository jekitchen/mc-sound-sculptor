package soundsculptor.gametest.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Game tests only: a channel's OpenAL source, so the boost test (PRD 11.3) can read the gain, the gain cap and the
 * fade-off distance the channel really has, on the sound thread, instead of measuring audio.
 */
@Mixin(Channel.class)
public interface ChannelSource {
	@Accessor("source")
	int soundsculptorTest$source();
}
