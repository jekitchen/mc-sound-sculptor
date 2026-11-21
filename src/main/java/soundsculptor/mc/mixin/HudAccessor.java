package soundsculptor.mc.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vanilla subtitle overlay, so the cue box can sit directly above its lines (FR-7.3). 26.3 field name. */
@Mixin(Hud.class)
public interface HudAccessor {
	@Accessor("subtitleOverlay")
	SubtitleOverlay soundsculptor$subtitleOverlay();
}
