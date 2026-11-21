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
 * confirmed, the profile remembers it ({@code Profile.boostConfirmed}) and never asks again. It is asked before a change
 * that adds boost to the profile (a new or louder level above 100%, {@code BoostSupport.addsBoost}): a level in the
 * Sounds pop-up or the trace's Level… panel, Save in an entry editor that writes to Quick changes, attaching a set,
 * saving a set editor, and importing or copying one in. Keeping, lowering or removing a boost never asks. The device
 * must be able to boost.
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

	/**
	 * Whether setting one entry's level from {@code before} to {@code after} in the loaded profile must ask first: it
	 * raises a level above 100% ({@link BoostSupport#raises}), the profile has not confirmed boost, and the device can
	 * boost. For the Level pop-ups, which set one level at a time.
	 */
	public static boolean needsConfirm(Integer before, Integer after) {
		return !granting && ProfileManager.isLoaded() && BoostSupport.deviceAllows() && !ProfileManager.profile().boostConfirmed()
				&& BoostSupport.raises(before, after);
	}

	/** Whether a change from {@code before} to {@code after} in the loaded profile must ask first: it adds boost. */
	public static boolean needsConfirm(Workspace before, Workspace after) {
		return !granting && ProfileManager.isLoaded()
				&& BoostSupport.needsConfirm(before.profile(), before.library(), after.profile(), after.library());
	}

	/**
	 * Shows the boost dialog over {@code host}. "Allow boost" runs {@code retry} again, now allowed to boost: the change it
	 * makes is saved together with the profile's confirmation, in one change ({@link #withGrant}). If it then saves
	 * nothing (a full set, a cancelled confirmation), the profile stays unconfirmed. Cancel (and Esc) runs
	 * {@code cancelled}. Either then returns to {@code host}, unless the action opened another screen. An open pop-up on
	 * a Sound Sculptor screen is kept for when the dialog closes.
	 */
	public static void ask(Screen host, Runnable retry, Runnable cancelled) {
		ask(host, Component.translatable(BoostWarning.CONFIRM_BODY_KEY), () -> runGranted(true, retry), cancelled);
	}

	private static void ask(Screen host, Component message, Runnable confirmed, Runnable cancelled) {
		SculptDialog dialog = new SculptDialog(host, Component.translatable(BoostWarning.CONFIRM_TITLE_KEY), message,
				List.of(new SculptDialog.Choice(Component.translatable("soundsculptor.profiles.boost.confirm"), confirmed),
						new SculptDialog.Choice(Component.translatable("soundsculptor.ui.cancel"), cancelled)),
				1, 1);
		if (host instanceof SculptScreen screen) {
			screen.showKeepingPopup(dialog);
		} else {
			Minecraft.getInstance().gui.setScreen(dialog);
		}
	}

	/** True while a change the player allowed to boost runs: it asks nothing, and saves the confirmation with it. */
	private static boolean granting;

	/** Whether the change running now was allowed to boost; a later step of it (a confirmation) passes it on. */
	public static boolean isGranting() {
		return granting;
	}

	/** Runs {@code action} as a change the player allowed to boost ({@code grant}), or as it is. */
	public static void runGranted(boolean grant, Runnable action) {
		boolean was = granting;
		granting = was || grant;
		try {
			action.run();
		} finally {
			granting = was;
		}
	}

	/**
	 * {@code after} with the profile confirmed when the change was allowed to boost ({@link #runGranted}); unchanged
	 * otherwise. {@code ProfileManager.change} applies it to every change it saves, so the confirmation is saved only
	 * with the change it was asked for.
	 */
	static Workspace withGrant(Workspace after) {
		return granting && !after.profile().boostConfirmed()
				? new Workspace(after.profile().withBoostConfirmed(true), after.library()) : after;
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
		if (!ProfileManager.isLoaded() || !BoostSupport.hasUnconfirmedBoost(ProfileManager.profile(), ProfileManager.library())
				|| !ASKED_ON_OPEN.add(ProfileManager.key())) {
			return;
		}
		// Nothing else changes here, so Allow boost saves just the confirmation.
		ask(host, Component.translatable("soundsculptor.boost.confirm.attached").append("\n\n")
				.append(Component.translatable(BoostWarning.CONFIRM_BODY_KEY)), BoostUi::confirm, () -> { });
	}
}
