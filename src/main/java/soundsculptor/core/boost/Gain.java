package soundsculptor.core.boost;

/**
 * Final loudness of a sound, range-safe (PRD FR-5.3 to FR-5.6, section 7.2).
 *
 * <p>The rule only scales the gain vanilla already computed, after vanilla's own clamp, so Master and category sliders
 * still apply and Master 0% silences everything. The hearing range and the distance fade-off are never touched: they
 * come from the sound instance's own volume ({@code SoundRange.vanillaRange}), which Sound Sculptor does not change. That
 * is why a boosted sound is louder where vanilla plays it, yet silent at the edge of vanilla range, exactly as in
 * vanilla. Never multiply the sound instance's volume instead: that would grow the range (PRD 7.7).
 */
public final class Gain {
	/** 200% of vanilla full volume (FR-5.6). */
	public static final float MAX_GAIN = 2.0F;
	/** OpenAL's default per-source gain cap. */
	public static final float OPENAL_DEFAULT_MAX_GAIN = 1.0F;

	private Gain() {
	}

	/** {@link #finalGain(float, float, boolean)} with boost available. */
	public static float finalGain(float vanillaGain, float rulePercent) {
		return finalGain(vanillaGain, rulePercent, true);
	}

	/**
	 * @param vanillaGain     vanilla's computed volume for the channel, including Master and category sliders
	 * @param rulePercent     the decided volume, 0 to 200 (0 when muted)
	 * @param boostSupported  false when boost is off or the audio driver ignores a raised gain cap; volumes then stop at
	 *                        100%
	 * @return the gain to give the channel, 0 to {@link #MAX_GAIN}; vanilla's gain on bad input (fail open)
	 */
	public static float finalGain(float vanillaGain, float rulePercent, boolean boostSupported) {
		if (!Float.isFinite(rulePercent) || rulePercent < 0 || !Float.isFinite(vanillaGain)) {
			return vanillaGain;
		}
		float percent = boostSupported ? rulePercent : Math.min(rulePercent, 100F);
		return Math.min(vanillaGain * percent / 100F, MAX_GAIN);
	}

	/**
	 * The gain for a channel: vanilla's gain times Sound Sculptor's factor, capped at {@link #MAX_GAIN} when the factor
	 * boosts (FR-5.6), however vanilla's own multipliers combine, and at {@link #OPENAL_DEFAULT_MAX_GAIN} otherwise. Every
	 * source's OpenAL cap is raised to 2.0, so without the second cap a gain above 1 from vanilla's own multipliers or
	 * another mod would play up to twice as loud at 100%, with Bypass on, or where boost is not allowed; with it, such a
	 * sound plays exactly as OpenAL's default cap of 1.0 would play it. Used at the start and whenever vanilla recalculates
	 * a playing sound (moving sounds, sliders). The factor is above 1 only where boost is allowed (the decider caps it).
	 *
	 * @param vanillaGain vanilla's gain for the channel ({@code calculateVolume}), including Master and category sliders
	 * @param factor      Sound Sculptor's factor, 0 to 2 (the level in percent / 100)
	 */
	public static float apply(float vanillaGain, float factor) {
		return Math.min(vanillaGain * factor, factor > 1F ? MAX_GAIN : OPENAL_DEFAULT_MAX_GAIN);
	}

	/**
	 * The OpenAL per-source gain cap ({@code AL_MAX_GAIN}) a channel needs: raised to {@link #MAX_GAIN} only when the
	 * channel's gain goes above the default 1.0.
	 */
	public static float channelMaxGain(float finalGain) {
		return finalGain > OPENAL_DEFAULT_MAX_GAIN ? MAX_GAIN : OPENAL_DEFAULT_MAX_GAIN;
	}

	/**
	 * How loud the sound is at {@code distance}, using the linear fade-off vanilla sets on each channel (gain falls to
	 * 0 at the vanilla range). For tests and diagnostics; the sound engine does this itself.
	 *
	 * @param range vanilla hearing range from the <em>un-boosted</em> instance volume; infinite for non-positional sounds
	 */
	public static float loudnessAt(float finalGain, double distance, float range) {
		if (Float.isInfinite(range)) {
			return finalGain;
		}
		double falloff = 1.0 - distance / range;
		return falloff <= 0 ? 0F : (float) (finalGain * Math.min(1.0, falloff));
	}
}
