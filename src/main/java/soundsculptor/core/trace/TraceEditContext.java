package soundsculptor.core.trace;

import java.util.Objects;
import java.util.function.ToIntFunction;

import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Precedence;

/**
 * The sound a trace panel is open for, and what is needed to recompute its trace after an edit.
 *
 * @param play           the sound's last play (where the player stood), or {@code null} if not heard yet
 * @param now            where the player stands now, for a sound not heard yet
 * @param bypass         whether Bypass Sound Sculptor is on
 * @param usedElsewhere  for a library set ID, how many places in other profiles use the set (FR-4.11); added to this
 *                       profile's places for "used in N places". 0 while that is not known yet.
 */
public record TraceEditContext(String soundId, LastPlay play, Standing now, boolean bypass,
		ToIntFunction<String> usedElsewhere) {
	public TraceEditContext {
		Objects.requireNonNull(soundId, "soundId");
		Objects.requireNonNull(now, "now");
		Objects.requireNonNull(usedElsewhere, "usedElsewhere");
	}

	/** A context that counts only this profile's places. */
	public TraceEditContext(String soundId, LastPlay play, Standing now, boolean bypass) {
		this(soundId, play, now, bypass, setId -> 0);
	}

	/** The trace for this profile and library, exactly as {@link SoundTracer} builds it. */
	public SoundTrace trace(Workspace ws) {
		Profile profile = ws.profile();
		Precedence p = new Precedence(profile.everywhere(), profile.everywhereCues(), profile.zones(), ws.library());
		return SoundTracer.trace(p, ws.library(), setId -> profile.placesUsing(setId).size() + usedElsewhere.applyAsInt(setId),
				soundId, play, now, bypass, soundsculptor.core.boost.BoostSupport.allows(profile));
	}
}
