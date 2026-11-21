package soundsculptor.mc;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTGainClampEx;
import soundsculptor.SoundSculptorClient;
import soundsculptor.core.boost.BoostSupport;
import soundsculptor.core.boost.Gain;

/**
 * Raises each new OpenAL source's gain cap to 2.0 and checks that the audio device keeps it (PRD 7.2, FR-5.6). Called
 * on the sound engine's thread right after vanilla creates a channel (see {@code ChannelMixin}).
 *
 * <p>The device honours the cap when setting {@code AL_MAX_GAIN} raises no OpenAL error, reading it back gives 2.0, and,
 * where the device reports a mixing limit ({@code AL_SOFT_gain_clamp_ex}), that limit is at least 2.0. If not, boost is
 * off ({@link BoostSupport}): levels above 100% play at 100%, the level controls say why, and the source keeps the
 * default cap of 1.0. Every OpenAL error raised here is read back here, so vanilla's own error checks never see one
 * and the log gets no "AL error" line.
 */
public final class BoostDevice {
	/** Used once per change of state, so the log says it once. */
	private static volatile boolean loggedFailure;

	private BoostDevice() {
	}

	/** Sound thread: vanilla just created the OpenAL source {@code source} for a new channel. */
	public static void onChannelCreated(int source) {
		boolean supported;
		String reason = null;
		try {
			AL10.alSourcef(source, AL10.AL_MAX_GAIN, Gain.MAX_GAIN);
			int setError = AL10.alGetError();
			float read = AL10.alGetSourcef(source, AL10.AL_MAX_GAIN);
			int readError = AL10.alGetError();
			supported = setError == AL10.AL_NO_ERROR && readError == AL10.AL_NO_ERROR && Math.abs(read - Gain.MAX_GAIN) < 1e-3F;
			if (!supported) {
				reason = setError != AL10.AL_NO_ERROR ? "setting the cap failed with code " + setError
						: readError != AL10.AL_NO_ERROR ? "reading the cap failed with code " + readError
						: "the cap reads back as " + read;
			} else if (BoostSupport.device() != BoostSupport.Device.SUPPORTED) {
				// Checked when boost is not known to work yet: the context's own mixing limit, where the device has one.
				float limit = mixingLimit();
				if (limit < Gain.MAX_GAIN) {
					supported = false;
					reason = "the device mixes at most " + limit + "x";
				}
			}
			if (!supported) {
				AL10.alSourcef(source, AL10.AL_MAX_GAIN, Gain.OPENAL_DEFAULT_MAX_GAIN);
				AL10.alGetError();
			}
		} catch (RuntimeException | LinkageError e) {
			supported = false;
			reason = e.toString();
		}
		if (BoostSupport.report(supported)) {
			if (supported) {
				SoundSculptorClient.LOGGER.info("Sound Sculptor: the audio device keeps a gain cap of 2.0, so boost above 100% is available");
			} else if (!loggedFailure) {
				loggedFailure = true;
				SoundSculptorClient.LOGGER.warn("Sound Sculptor: the audio device does not keep a raised gain cap ({}), "
						+ "so boost is off and levels above 100% play at 100%", reason);
			}
		}
	}

	/** {@code AL_GAIN_LIMIT_SOFT}, or infinity when the device does not report one. */
	private static float mixingLimit() {
		if (!AL10.alIsExtensionPresent("AL_SOFT_gain_clamp_ex")) {
			AL10.alGetError();
			return Float.POSITIVE_INFINITY;
		}
		float limit = AL10.alGetFloat(SOFTGainClampEx.AL_GAIN_LIMIT_SOFT);
		return AL10.alGetError() == AL10.AL_NO_ERROR ? limit : Float.POSITIVE_INFINITY;
	}
}
