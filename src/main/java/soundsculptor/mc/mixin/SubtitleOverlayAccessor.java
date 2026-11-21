package soundsculptor.mc.mixin;

import java.util.List;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The subtitle lines vanilla drew in its last frame, so the cue box sits directly above them (FR-7.3). The list is
 * refilled each frame while Subtitles is on and left as it was when Subtitles is turned off. 26.3 field name.
 */
@Mixin(SubtitleOverlay.class)
public interface SubtitleOverlayAccessor {
	@Accessor("audibleSubtitles")
	List<?> soundsculptor$audibleSubtitles();
}
