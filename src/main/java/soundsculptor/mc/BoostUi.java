package soundsculptor.mc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import soundsculptor.core.boost.BoostSupport;
import soundsculptor.core.boost.BoostWarning;
import soundsculptor.core.sets.Workspace;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;

/**
 * The boost safeguards in the level controls (PRD FR-5.4): the warning under a level slider above 100%, and the
 * dialog the first time a profile uses a level above 100%.
 *
 * <p>The dialog explains clipping and hearing safety and needs a confirm ("Allow boost"); Cancel changes nothing. Once
 * confirmed, the profile remembers it ({@code Profile.boostConfirmed}) and never asks again. It is asked before the
 * change that would make the profile use boost: setting a level above 100% (entry editor, Sounds pop-up, trace Level…),
 * attaching a set that has one, saving a set editor that leaves one in this profile, and importing or copying one in.
 * Outside a world there is no profile to ask for; a profile that then finds a boosted entry (a linked set edited in
 * another world) plays it at 100% and asks when the Sound Sculptor screen next opens there.
 */
public final class BoostUi {
	/** Profiles already asked when the Sound Sculptor screen opened, this session (Cancel there is not asked again). */
	private static final Set<String> ASKED_ON_OPEN = new HashSet<>();

	private BoostUi() {
	}

	/**
	 * What shows under a level slider at {@code percent}: the warning above 100%, or, when the audio device cannot
	 * boost, why it plays at 100% instead; {@code null} at 100% or less.
	 */
	public static Component note(double percent) {
		if (!BoostWarning.showWarning((int) Math.round(percent))) {
			return null;
		}
		return Component.translatable(BoostSupport.deviceAllows() ? BoostWarning.WARNING_KEY : BoostWarning.UNSUPPORTED_KEY);
	}

	/**
	 * Adds the warning to a level slider and its number field: the icon and text under the slider (up to
	 * {@code noteWidth} pixels wide), and the narrator reads it after the value of either.
	 */
	public static void warnOn(SculptSlider slider, SculptTextField field, java.util.function.IntSupplier noteWidth) {
		// The note and the field's error share the slot under the row, so only one shows: the error, while there is one.
		// The error then runs under the whole row too, so "Type a number from 0 to 200" is not cut short.
		slider.noteBelow(value -> field.error() == null ? note(value) : null, noteWidth);
		field.errorMessage(slider::getX, noteWidth, () -> true);
		field.narratedNotice(() -> field.isValid() && !field.getValue().isBlank() ? note(Integer.parseInt(field.getValue().trim())) : null);
	}

	/** Whether setting a level of {@code level} in the loaded profile must ask first. */
	public static boolean needsConfirm(Integer level) {
		return ProfileManager.isLoaded() && ProfileManager.profile().needsBoostConfirm(level);
	}

	/** Whether a change that leaves {@code after} in the loaded profile must ask first. */
	public static boolean needsConfirm(Workspace after) {
		return ProfileManager.isLoaded() && BoostSupport.needsConfirm(after.profile(), after.library());
	}

	/**
	 * Shows the boost dialog over {@code host}. "Allow boost" saves the profile as confirmed, then runs
	 * {@code confirmed}; Cancel (and Esc) runs {@code cancelled}. Either then returns to {@code host}, unless the action
	 * opened another screen. An open pop-up on a Sound Sculptor screen is kept for when the dialog closes.
	 */
	public static void ask(Screen host, Runnable confirmed, Runnable cancelled) {
		ask(host, Component.translatable(BoostWarning.CONFIRM_BODY_KEY), confirmed, cancelled);
	}

	private static void ask(Screen host, Component message, Runnable confirmed, Runnable cancelled) {
		SculptDialog dialog = new SculptDialog(host, Component.translatable(BoostWarning.CONFIRM_TITLE_KEY), message,
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.profiles.boost.confirm"), () -> {
							confirm();
							confirmed.run();
						}),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), cancelled)),
				1, 1);
		if (host instanceof SculptScreen screen) {
			screen.showKeepingPopup(dialog);
		} else {
			Minecraft.getInstance().gui.setScreen(dialog);
		}
	}

	/** Saves the loaded profile as having confirmed boost (FR-5.4: asked once per profile). */
	public static void confirm() {
		if (ProfileManager.isLoaded()) {
			ProfileManager.update(profile -> profile.withBoostConfirmed(true));
		}
	}

	/**
	 * When the Sound Sculptor screen opens: if this profile has a level above 100% it never confirmed (a linked set
	 * edited in another world, or outside a world), asks once this session. Until confirmed it plays at 100%.
	 */
	public static void askOnOpen(Screen host) {
		if (!needsConfirm(ProfileManager.workspace()) || !BoostSupport.deviceAllows() || !ASKED_ON_OPEN.add(ProfileManager.key())) {
			return;
		}
		ask(host, Component.translatable("soundsculptor.boost.confirm.attached").append("\n\n")
				.append(Component.translatable(BoostWarning.CONFIRM_BODY_KEY)), () -> { }, () -> { });
	}
}
