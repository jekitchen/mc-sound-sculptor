package soundsculptor.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import soundsculptor.core.boost.BoostSupport;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryLocation;
import soundsculptor.core.trace.TraceText;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.gametest.compat.NarrationCompat;
import soundsculptor.gametest.mixin.ChannelSource;
import soundsculptor.mc.AttachSetScreen;
import soundsculptor.mc.BoostUi;
import soundsculptor.mc.CueOverlay;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.PlaceSetsScreen;
import soundsculptor.mc.PlayingChannels;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.mixin.SoundEngineAccessor;
import soundsculptor.mc.mixin.SoundManagerAccessor;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;

/**
 * Automated version of the M10 milestone test (PRD 11.1): the FR-5 v1 acceptance criteria, the 11.3 boost-range test
 * and matrix case P8 (11.4), as far as they can be checked without listening. Loudness and range are read from the
 * engine and from each channel's OpenAL source on the sound thread ({@code ChannelSource}): its gain, its gain cap and
 * its fade-off distance.
 *
 * <ul>
 *   <li>The audio device's answer to the raised gain cap (logged for CI).</li>
 *   <li>Level 150% in the entry editor: the warning icon and "May distort. Protect your hearing." under the slider, read
 *   by the narrator; Save asks "Louder than vanilla?" once per profile; Cancel saves nothing.</li>
 *   <li>The Sounds row's pop-up and the trace's Level… panel go to 200%; the first step above 100% by keyboard asks,
 *   Cancel keeps the pop-up at 100%; 250 is an error; the trace says "Above 100%: boost applied."</li>
 *   <li>Master at 0% silences a 200% entry.</li>
 *   <li>11.3: a villager (vanilla range 16) and a loud harp (volume 3, range 48) at 100%, 200% and bypassed: the
 *   channel's fade-off distance, the vanilla subtitle, the recently played list and the cue box all stop at the same
 *   distance; at 200% the channel's gain is twice the gain at 100%.</li>
 *   <li>A device that keeps no raised cap: levels above 100% play at 100% and the controls and trace say why.</li>
 *   <li>A library set at 200% attached in a second world asks there too, once; a set made louder in another world asks
 *   when the Sound Sculptor screen opens and plays at 100% until confirmed.</li>
 *   <li>P8: Everywhere villagers 150%, zone A sets only Cue on: 150%, no cue.</li>
 * </ul>
 */
public final class M10BoostClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String VILLAGERS = "minecraft:entity.villager.*";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String HARP = "minecraft:block.note_block.harp";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final PlaceRef EVERYWHERE = PlaceRef.EVERYWHERE;
	private static final String WARNING = "May distort. Protect your hearing.";
	private static final String TITLE = "Louder than vanilla?";
	private static final String SECOND = "m10-second.example.net";
	private static final SoundSet LOUD = new SoundSet("m10-loud", "Loud villagers", List.of(SetEntry.of("e1", VILLAGER).withVolume(200)));
	private static final SoundSet LOUDER = new SoundSet("m10-louder", "Loud cows", List.of(SetEntry.of("e1", COW).withVolume(150)));
	/** Frames for the HUD and the sound thread to catch up with the last step. */
	private static final int FRAMES = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		boolean subtitlesBefore = context.computeOnClient(mc -> mc.options.showSubtitles().get());
		double musicBefore = context.computeOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get());
		double masterBefore = context.computeOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
		double neutralBefore = context.computeOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).get());
		double recordsBefore = context.computeOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).get());
		String key = null;
		String name = null;
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			// Facing north (yaw 180).
			world.getServer().runCommand("tp @a 0.5 -60 0.5 180 0");
			context.waitTicks(3);
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
				// Background music shows in the Sounds list and plays on its own channel; keep it out of the way.
				mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
				mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
				mc.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(1.0);
				mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).set(1.0);
				mc.options.showSubtitles().set(false);
				mc.getSoundManager().stop();
				CueOverlay.clear();
			});
			key = context.computeOnClient(mc -> ProfileManager.key());
			name = context.computeOnClient(mc -> ProfileManager.profile().name());

			BoostSupport.Device device = checkDevice(context);
			checkEntryEditor(context);
			checkSoundsPopup(context);
			checkTraceLevel(context);
			checkMasterZero(context);
			checkRange(context, device);
			checkNoDeviceSupport(context, device);
			checkP8(context);
			checkSecondWorld(context, key, name);
			checkAskOnOpen(context, key, name);
		} finally {
			String rejoin = key;
			String rejoinName = name;
			context.runOnClient(mc -> {
				mc.options.showSubtitles().set(subtitlesBefore);
				mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(musicBefore);
				mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(masterBefore);
				mc.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(neutralBefore);
				mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).set(recordsBefore);
				ProfileManager.setBypass(false);
				if (rejoin != null && !rejoin.equals(ProfileManager.key())) {
					ProfileManager.flushSaves();
					ProfileManager.leave();
					ProfileManager.join(rejoin, rejoinName);
				}
				ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()), ws.library().remove(LOUD.id()).remove(LOUDER.id())));
				ProfileManager.flushSaves();
				if (ProfileManager.storedProfile(SECOND) != null) {
					ProfileManager.delete(SECOND);
					ProfileManager.flushSaves();
				}
				CueOverlay.clear();
			});
		}
	}

	/** Section 7.2: the first channel tells whether the audio device keeps a gain cap of 2.0. */
	private static BoostSupport.Device checkDevice(ClientGameTestContext context) {
		SoundInstance sound = play(context, COW, 3, 0);
		AlState state = alState(context, sound);
		BoostSupport.Device device = BoostSupport.device();
		System.out.println("M10 boost: audio device " + context.computeOnClient(mc -> mc.getSoundManager().getChannelDebugString())
				+ "; boost " + device + "; a new channel reads " + state);
		check(device != BoostSupport.Device.UNKNOWN, "a channel was made, so the device's answer is known");
		check(state.error() == AL10.AL_NO_ERROR, "reading the source left no OpenAL error, was " + state.error());
		if (device == BoostSupport.Device.SUPPORTED) {
			check(Math.abs(state.maxGain() - 2F) < 1e-4, "each new channel's gain cap is 2.0, was " + state.maxGain());
		} else {
			check(Math.abs(state.maxGain() - 1F) < 1e-4, "a device that cannot boost keeps the default cap, was " + state.maxGain());
		}
		return device;
	}

	/** FR-5 AC 4 in the entry editor: the warning at 150%, and the dialog once per profile at Save. */
	private static void checkEntryEditor(ClientGameTestContext context) {
		check(!context.computeOnClient(mc -> ProfileManager.profile().boostConfirmed()), "a new profile has not confirmed boost");
		openMain(context);
		context.runOnClient(mc -> mc.gui.setScreen(EntryEditorScreen.forSound(main(mc), EVERYWHERE, VILLAGER)));
		context.waitForScreen(EntryEditorScreen.class);
		context.runOnClient(mc -> {
			EntryEditorScreen e = editor(mc);
			check(e.volumeSlider.note() == null, "no warning at 100%");
			e.setFocused(e.volumeField);
			e.volumeField.setValue("150");
		});
		context.waitTicks(1);
		context.runOnClient(mc -> {
			EntryEditorScreen e = editor(mc);
			check(e.volumeSlider.modelValue() == 150 && e.volumeSlider.isWarning(), "the slider follows to 150% and is in its warning zone");
			check(e.volumeSlider.note() != null && e.volumeSlider.note().getString().equals(BoostUi.note(150).getString()),
					"the warning shows under the slider");
			check(e.volumeField.error() == null && e.saveButton.active, "150 is a valid level and Save is on");
			String sliderNarration = NarrationCompat.collect(e.volumeSlider::updateNarration);
			String fieldNarration = NarrationCompat.collect(e.volumeField::updateNarration);
			check(sliderNarration.contains(expectedNote()), "the narrator reads the warning on the slider: " + sliderNarration);
			check(fieldNarration.contains(expectedNote()), "and on the number field: " + fieldNarration);
			e.volumeField.setValue("250");
			check(e.volumeField.error() != null && e.volumeField.error().getString().contains("200") && !e.saveButton.active,
					"250 is above the maximum 200, was " + e.volumeField.error());
			check(e.volumeSlider.note() == null, "the error takes the warning's place under the row");
			e.volumeField.setValue("150");
		});
		context.takeScreenshot("m10-entry-editor-warning");

		// Save asks first; Cancel goes back to the editor and saves nothing.
		context.runOnClient(mc -> editor(mc).save());
		context.waitForScreen(SculptDialog.class);
		context.runOnClient(mc -> {
			SculptDialog dialog = (SculptDialog) mc.gui.screen();
			check(dialog.getTitle().getString().equals(TITLE), "the boost question, was " + dialog.getTitle().getString());
			String message = dialog.message().getString();
			check(message.contains("crackle or distort") && message.contains("harm your hearing"), "it explains clipping and hearing safety: " + message);
			check(dialog.buttons().get(0).getMessage().getString().equals("Allow boost") && dialog.buttons().get(1).getMessage().getString().equals("Cancel"),
					"Allow boost and Cancel");
			check(dialog.getFocused() == dialog.buttons().get(1), "Cancel has focus, so Enter changes nothing");
			check(dialog.getNarrationMessage().getString().contains(TITLE), "the narrator reads the dialog");
		});
		context.takeScreenshot("m10-boost-dialog");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(EntryEditorScreen.class);
		check(context.computeOnClient(mc -> QuickActions.quickEntry(ProfileManager.workspace(), EVERYWHERE, VILLAGER)) == null,
				"Cancel saves nothing");
		check(!context.computeOnClient(mc -> ProfileManager.profile().boostConfirmed()), "and does not confirm boost");
		check(context.computeOnClient(mc -> editor(mc).volumeField.getValue()).equals("150"), "the editor still holds 150");
		context.runOnClient(mc -> editor(mc).save());
		context.waitForScreen(SculptDialog.class);
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> volumeAt(VILLAGER)) == 150, "Allow boost saves the entry at 150%");
		check(context.computeOnClient(mc -> ProfileManager.profile().boostConfirmed()), "and confirms boost for this profile");
		check(context.computeOnClient(mc -> {
			ProfileManager.flushSaves();
			return ProfileManager.storedProfile(ProfileManager.key()).boostConfirmed();
		}), "the confirmation is saved with the profile");

		// Asked once per profile: another level above 100% saves without asking.
		context.runOnClient(mc -> mc.gui.setScreen(EntryEditorScreen.forSound(main(mc), EVERYWHERE, COW)));
		context.waitForScreen(EntryEditorScreen.class);
		context.runOnClient(mc -> {
			editor(mc).volumeField.setValue("180");
			editor(mc).save();
		});
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> volumeAt(COW)) == 180, "the second boost saves without the dialog");
		closeScreen(context);
	}

	/**
	 * The Sounds row's "Level, cue and limit…" pop-up in a profile that never confirmed boost: Right from 100% asks; Cancel
	 * keeps the pop-up open at 100% with nothing saved; Allow boost sets 105% and keeps the pop-up; Right again asks no
	 * more.
	 */
	private static void checkSoundsPopup(ClientGameTestContext context) {
		context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name())));
		openMain(context);
		context.runOnClient(mc -> {
			main(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS);
			main(mc).sounds().openLevel(VILLAGER);
		});
		context.waitTicks(1);
		check(context.computeOnClient(mc -> main(mc).getFocused() == main(mc).sounds().levelSlider), "the slider starts focused");
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitForScreen(SculptDialog.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals(TITLE), "the first step above 100% asks");
		check(context.computeOnClient(mc -> volumeAt(VILLAGER)) == -1, "nothing is applied while it asks");
		context.takeScreenshot("m10-popup-dialog");
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
		context.waitForScreen(SoundSculptorScreen.class);
		context.runOnClient(mc -> {
			SoundSculptorScreen s = main(mc);
			check(s.hasOverlay() && s.sounds().levelSlider != null, "Cancel returns to the pop-up, still open");
			check(s.sounds().levelSlider.modelValue() == 100, "at 100%, was " + s.sounds().levelSlider.modelValue());
			check(s.getFocused() == s.sounds().levelSlider, "with the slider focused again");
			check(volumeAt(VILLAGER) == -1 && !ProfileManager.profile().boostConfirmed(), "nothing changed");
		});
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitForScreen(SculptDialog.class);
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(SoundSculptorScreen.class);
		context.runOnClient(mc -> {
			SoundSculptorScreen s = main(mc);
			check(s.hasOverlay() && s.sounds().levelSlider.modelValue() == 105, "Allow boost sets 105% in the open pop-up");
			check(volumeAt(VILLAGER) == 105 && ProfileManager.profile().boostConfirmed(), "saved in Quick changes, boost confirmed");
			check(s.sounds().levelSlider.note() != null, "the warning shows in the pop-up");
		});
		context.takeScreenshot("m10-popup-warning");
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitTicks(1);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof SoundSculptorScreen && volumeAt(VILLAGER) == 110),
				"the next step asks no more: 110%");
		// The slider ends at 200%.
		context.runOnClient(mc -> {
			main(mc).setFocused(main(mc).sounds().levelField);
			main(mc).sounds().levelField.setValue("200");
		});
		check(context.computeOnClient(mc -> main(mc).sounds().levelSlider.modelValue() == 200 && volumeAt(VILLAGER) == 200), "200% is the top");
		context.runOnClient(mc -> main(mc).closeOverlay());
		String toast = context.computeOnClient(mc -> main(mc).toast() == null ? "" : main(mc).toast().message().getString());
		check(toast.startsWith("Changed the level"), "one Undo toast for the pop-up, was " + toast);
		closeScreen(context);
	}

	/** FR-14.3, FR-14.4 and FR-14 AC "type 250": the trace's Level… panel goes to 200%, and the trace says boost applied. */
	private static void checkTraceLevel(ClientGameTestContext context) {
		openMain(context);
		context.runOnClient(mc -> mc.gui.setScreen(new TraceScreen(main(mc), VILLAGER)));
		context.waitForScreen(TraceScreen.class);
		context.runOnClient(mc -> {
			TraceScreen t = (TraceScreen) mc.gui.screen();
			SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), EVERYWHERE, VILLAGER);
			SculptList.Row row = t.row(new TraceText.EntryTarget(new EntryLocation(EVERYWHERE, SoundSet.QUICK_ID, entry.id()), entry));
			check(row != null, "the trace lists the Quick changes entry");
			SculptButton level = row.buttons().stream().filter(b -> b.getMessage().getString().equals("Level…")).findFirst().orElse(null);
			check(level != null, "the entry line has Level…");
			level.onPress(null);
			check(t.hasOverlay() && t.volumeSlider.modelValue() == 200, "the panel opens at the entry's 200%");
			t.setFocused(t.volumeField);
			t.volumeField.setValue("150");
			check(volumeAt(VILLAGER) == 150 && t.volumeSlider.note() != null, "150% applies at once, with the warning");
			t.volumeField.setValue("250");
			check(t.volumeField.error() != null && t.volumeField.error().getString().contains("200"), "250 is an error naming 200");
			check(volumeAt(VILLAGER) == 150, "and nothing changes");
			t.volumeField.revealError();
			check(t.volumeSlider.note() == null, "the error shows instead of the warning");
		});
		context.takeScreenshot("m10-trace-level-error");
		context.runOnClient(mc -> ((TraceScreen) mc.gui.screen()).volumeField.setValue("150"));
		context.takeScreenshot("m10-trace-level");
		context.runOnClient(mc -> {
			TraceScreen t = (TraceScreen) mc.gui.screen();
			t.volumeField.setValue("150");
			t.closeOverlay();
			check(t.text().contains("Above 100%: boost applied."), "the trace leads with the boost:\n" + t.text());
		});
		closeScreen(context);
	}

	/** FR-5 AC 3: Master at 0% silences everything, even an entry at 200%. */
	private static void checkMasterZero(ClientGameTestContext context) {
		setLevel(context, VILLAGER, 200);
		context.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
		boolean started = context.computeOnClient(mc -> {
			SoundInstance sound = instance(VILLAGER, mc, 4, 0);
			mc.getSoundManager().play(sound);
			check(SoundHook.lastStartFactor() == 2F, "the entry decided 200%");
			return mc.getSoundManager().isActive(sound);
		});
		check(!started, "with Master at 0% the 200% villager does not play");
		context.runOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0));
	}

	/**
	 * Test 11.3 and FR-5 AC 5 and 6 (FR-5.5): a villager (vanilla volume 1, range 16) and a loud harp (volume 3, range
	 * 48) bypassed, at 100% and at 200%. The channel's fade-off distance ({@code AL_MAX_DISTANCE}, where the gain reaches
	 * 0) is the same in every case, and so is where the vanilla subtitle, the recently played list and the cue box stop;
	 * at 200% the channel's gain is twice the gain at 100%, inside range.
	 */
	private static void checkRange(ClientGameTestContext context, BoostSupport.Device device) {
		context.runOnClient(mc -> mc.options.showSubtitles().set(true));
		context.waitTicks(FRAMES);
		String mumbles = context.computeOnClient(mc -> mc.getSoundManager().getSoundEvent(Identifier.parse(VILLAGER)).getSubtitle().getString());
		float[] gains = new float[3];
		float[] distances = new float[3];
		String[] modes = {"bypassed", "100%", "200%"};
		for (int mode = 0; mode < 3; mode++) {
			int m = mode;
			context.runOnClient(mc -> {
				ProfileManager.setBypass(m == 0);
				ProfileManager.update(p -> QuickActions.apply(new Workspace(p, ProfileManager.library()), EVERYWHERE, VILLAGER,
						new soundsculptor.core.trace.EntryEdit.Volume(m == 2 ? 200 : 100)).after().profile());
			});
			// Inside range: the channel's own gain and fade-off distance.
			SoundInstance near = play(context, VILLAGER, 8, 0);
			AlState state = alState(context, near);
			gains[mode] = state.gain();
			distances[mode] = state.maxDistance();
			float tracked = context.computeOnClient(mc -> PlayingChannels.get().gain(near));
			System.out.println("M10 boost-range: villager " + modes[mode] + ": " + state + ", tracked gain " + tracked);
			check(state.error() == AL10.AL_NO_ERROR, "no OpenAL error reading the source");
			if (mode > 0) {
				check(Math.abs(tracked - state.gain()) < 1e-4, "the channel has the gain the mod set (" + modes[mode] + ")");
			}
			// The cut-off: just inside and just outside vanilla range.
			check(cutOff(context, VILLAGER, 15.5, mumbles, modes[mode]), "heard at 15.5 blocks (" + modes[mode] + ")");
			check(!cutOff(context, VILLAGER, 16.5, mumbles, modes[mode]), "not heard at 16.5 blocks (" + modes[mode] + ")");
		}
		check(distances[0] == 16F && distances[1] == 16F && distances[2] == 16F,
				"the villager fades to silence at 16 blocks in every case, was " + java.util.Arrays.toString(distances));
		check(Math.abs(gains[1] - gains[0]) < 1e-4, "100% is vanilla's gain");
		check(Math.abs(gains[2] - Math.min(2F, 2 * gains[1])) < 1e-4, "200% is twice the gain at 100%: " + java.util.Arrays.toString(gains));
		if (device == BoostSupport.Device.SUPPORTED) {
			check(gains[2] > 1.99F, "with Master, Friendly Creatures and the villager at full volume, 200% is a gain of 2.0");
		}
		context.takeScreenshot("m10-range-subtitles");

		// The cue box (FR-5 AC 6): a muted villager with Cue on stops at the same distance.
		context.runOnClient(mc -> {
			ProfileManager.setBypass(false);
			ProfileManager.update(p -> QuickActions.apply(new Workspace(p, ProfileManager.library()), EVERYWHERE, VILLAGER,
					new soundsculptor.core.trace.EntryEdit.MuteWithCue(true)).after().profile());
			mc.options.showSubtitles().set(false);
		});
		context.runOnClient(mc -> CueOverlay.clear());
		playAt(context, VILLAGER, 16.5);
		check(context.computeOnClient(mc -> CueOverlay.shown()).isEmpty(), "no cue at 16.5 blocks");
		playAt(context, VILLAGER, 15.5);
		check(context.computeOnClient(mc -> CueOverlay.shown()).size() == 1, "a cue at 15.5 blocks");
		context.runOnClient(mc -> CueOverlay.clear());

		// 11.3 step 5: a loud sound (vanilla volume 3, range 48) keeps its range at 200% too.
		float[] harp = new float[3];
		float[] harpGain = new float[3];
		for (int mode = 0; mode < 3; mode++) {
			int m = mode;
			context.runOnClient(mc -> {
				ProfileManager.setBypass(m == 0);
				ProfileManager.update(p -> QuickActions.apply(new Workspace(Profile.empty(p.name()).withBoostConfirmed(true), ProfileManager.library()),
						EVERYWHERE, HARP, new soundsculptor.core.trace.EntryEdit.Volume(m == 2 ? 200 : 100)).after().profile());
			});
			SoundInstance near = context.computeOnClient(mc -> {
				SoundInstance sound = new SimpleSoundInstance(Identifier.parse(HARP), SoundSource.RECORDS, 3.0F, 1.0F, RandomSource.create(), false, 0,
						SoundInstance.Attenuation.LINEAR, listener(mc).x() + 30, listener(mc).y(), listener(mc).z(), false);
				mc.getSoundManager().play(sound);
				return sound;
			});
			AlState state = alState(context, near);
			harp[mode] = state.maxDistance();
			harpGain[mode] = state.gain();
			check(recordedAt(context, HARP, 47.5, modes[mode], 3.0F), "the harp is listed at 47.5 blocks (" + modes[mode] + ")");
			check(!recordedAt(context, HARP, 48.5, modes[mode], 3.0F), "and not at 48.5 blocks (" + modes[mode] + ")");
		}
		System.out.println("M10 boost-range: harp distances " + java.util.Arrays.toString(harp) + ", gains " + java.util.Arrays.toString(harpGain));
		check(harp[0] == 48F && harp[1] == 48F && harp[2] == 48F, "the harp fades to silence at 48 blocks in every case");
		check(Math.abs(harpGain[2] - Math.min(2F, 2 * harpGain[1])) < 1e-4, "the harp at 200% is twice as loud as at 100%");
		context.runOnClient(mc -> {
			ProfileManager.setBypass(false);
			ProfileManager.update(p -> Profile.empty(p.name()).withBoostConfirmed(true));
		});
	}

	/**
	 * A sound {@code distance} blocks east of the listener: whether vanilla's subtitle shows it (Subtitles on) and the
	 * recently played list lists it. The two must agree, as both use vanilla's range from the un-boosted volume.
	 */
	private static boolean cutOff(ClientGameTestContext context, String id, double distance, String subtitle, String mode) {
		// Let an earlier subtitle run out first (3 s), so only this play can show.
		context.waitTicks(70);
		context.runOnClient(mc -> RecentLog.get().sounds().clear());
		playAt(context, id, distance);
		boolean subtitled = context.computeOnClient(mc -> VanillaSubtitles.lastFrame().stream().anyMatch(r -> r.text().equals(subtitle)));
		boolean listed = context.computeOnClient(mc -> row(id) != null);
		check(subtitled == listed, "the subtitle and the recently played list agree at " + distance + " blocks (" + mode + "): subtitle "
				+ subtitled + ", listed " + listed);
		if (listed && mode.equals("200%")) {
			check(context.computeOnClient(mc -> row(id).status()) == PlayStatus.BOOSTED, "the list says Boosted");
		}
		return listed;
	}

	private static boolean recordedAt(ClientGameTestContext context, String id, double distance, String mode, float volume) {
		context.runOnClient(mc -> {
			RecentLog.get().sounds().clear();
			mc.getSoundManager().play(new SimpleSoundInstance(Identifier.parse(id), SoundSource.RECORDS, volume, 1.0F, RandomSource.create(), false, 0,
					SoundInstance.Attenuation.LINEAR, listener(mc).x() + distance, listener(mc).y(), listener(mc).z(), false));
		});
		context.waitTicks(FRAMES);
		return context.computeOnClient(mc -> row(id) != null);
	}

	/**
	 * Section 7.2 fallback: with a device that does not keep the raised cap, levels above 100% play at 100%; the slider's
	 * note and the trace say why. The device's real answer is put back afterwards.
	 */
	private static void checkNoDeviceSupport(ClientGameTestContext context, BoostSupport.Device device) {
		setLevel(context, VILLAGER, 200);
		float factor = context.computeOnClient(mc -> {
			BoostSupport.report(false);
			try {
				check(BoostUi.note(150).getString().equals("Your audio device can't boost. It plays at 100%."),
						"the level controls say why, was " + BoostUi.note(150).getString());
				String trace = new TraceScreen(null, VILLAGER).text();
				check(trace.contains("Above 100%: boost is off here, so it plays at 100%."), "the trace says it plays at 100%:\n" + trace);
				// Last: the new channel reports the real device again, on the sound thread, after this start was decided.
				mc.getSoundManager().play(instance(VILLAGER, mc, 4, 0));
				return SoundHook.lastStartFactor();
			} finally {
				BoostSupport.report(device != BoostSupport.Device.UNSUPPORTED);
			}
		});
		check(factor == 1F, "a 200% entry plays at 100% without device support, was " + factor);
		if (device == BoostSupport.Device.UNSUPPORTED) {
			context.runOnClient(mc -> BoostSupport.report(false));
		}
	}

	/** Matrix case P8 (11.4): Everywhere villagers 150%; zone A sets Cue on only. Result: 150%, and no cue (not muted). */
	private static void checkP8(ClientGameTestContext context) {
		String key = context.computeOnClient(mc -> ProfileManager.key());
		Vec3 player = context.computeOnClient(mc -> mc.player.position());
		Zone a = new Zone("m10-p8", "A", true, key, OVERWORLD, new Point(player.x, player.y, player.z), new Shape.Box(10, 10, 10),
				PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("c", VILLAGERS).withCue(true))));
		context.runOnClient(mc -> {
			ProfileManager.update(p -> Profile.empty(p.name()).withBoostConfirmed(true)
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("v", VILLAGERS).withVolume(150))))
					.withZones(List.of(a)));
			CueOverlay.clear();
		});
		context.waitTicks(2);
		float factor = context.computeOnClient(mc -> {
			mc.getSoundManager().play(instance(VILLAGER, mc, 3, 0));
			return SoundHook.lastStartFactor();
		});
		context.waitTicks(FRAMES);
		check(factor == 1.5F, "P8: 150% from Everywhere while in A, was " + factor);
		check(context.computeOnClient(mc -> CueOverlay.shown()).isEmpty(), "P8: no cue, the sound is not muted");
		String trace = context.computeOnClient(mc -> new TraceScreen(null, VILLAGER).text());
		check(trace.contains("Above 100%: boost applied.") && trace.contains("Cue on"), "P8: the trace shows the boost and A's cue:\n" + trace);
		context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name()).withBoostConfirmed(true)));
	}

	/**
	 * FR-5 AC 4, second half: a library set with a 200% entry attached in this world (confirmed, no question) and then in a
	 * second world, which asks there too, once. Cancel attaches nothing.
	 */
	private static void checkSecondWorld(ClientGameTestContext context, String key, String name) {
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()).withBoostConfirmed(true),
				ws.library().put(LOUD).put(LOUDER))));
		openPlace(context);
		context.runOnClient(mc -> check(places(mc).panel.attach(LOUD.id()), "a confirmed profile attaches the loud set without asking"));
		context.waitTicks(1);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof PlaceSetsScreen), "no question in the first world");

		switchProfile(context, SECOND, "M10 second world");
		check(!context.computeOnClient(mc -> ProfileManager.profile().boostConfirmed()), "the second world has not confirmed boost");
		openPlace(context);
		context.runOnClient(mc -> places(mc).panel.openAttach());
		context.waitForScreen(AttachSetScreen.class);
		context.runOnClient(mc -> ((AttachSetScreen) mc.gui.screen()).attach(LOUD.id()));
		context.waitForScreen(SculptDialog.class);
		check(context.computeOnClient(mc -> mc.gui.screen().getTitle().getString()).equals(TITLE), "attaching the loud set asks in the second world");
		context.takeScreenshot("m10-second-world-dialog");
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
		context.waitForScreen(PlaceSetsScreen.class);
		check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().attached().isEmpty()), "Cancel attaches nothing");
		context.runOnClient(mc -> places(mc).panel.openAttach());
		context.waitForScreen(AttachSetScreen.class);
		context.runOnClient(mc -> ((AttachSetScreen) mc.gui.screen()).attach(LOUD.id()));
		context.waitForScreen(SculptDialog.class);
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(0));
		context.waitForScreen(PlaceSetsScreen.class);
		check(context.computeOnClient(mc -> ProfileManager.profile().everywhere().uses(LOUD.id()) && ProfileManager.profile().boostConfirmed()),
				"Allow boost attaches it and confirms boost in the second world");
		check(context.computeOnClient(mc -> ((SculptScreen) mc.gui.screen()).toast().message().getString()).equals("Attached 'Loud villagers'"),
				"with the usual toast");
		context.runOnClient(mc -> check(places(mc).panel.attach(LOUDER.id()), "once confirmed, another loud set attaches without asking"));
		context.waitTicks(1);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof PlaceSetsScreen), "no second question");
		float factor = context.computeOnClient(mc -> {
			mc.getSoundManager().play(instance(VILLAGER, mc, 4, 0));
			return SoundHook.lastStartFactor();
		});
		check(factor == 2F, "the loud set plays at 200% in the second world, was " + factor);
		closeScreen(context);
	}

	/**
	 * A linked set made louder elsewhere: the second world has the loud set attached but never confirmed boost. It plays
	 * at 100%, the trace says so, and the Sound Sculptor screen asks once when it opens there.
	 */
	private static void checkAskOnOpen(ClientGameTestContext context, String key, String name) {
		context.runOnClient(mc -> ProfileManager.update(p -> p.withBoostConfirmed(false)));
		float factor = context.computeOnClient(mc -> {
			mc.getSoundManager().play(instance(VILLAGER, mc, 4, 0));
			return SoundHook.lastStartFactor();
		});
		check(factor == 1F, "unconfirmed, the 200% entry plays at 100%, was " + factor);
		String trace = context.computeOnClient(mc -> new TraceScreen(null, VILLAGER).text());
		check(trace.contains("Above 100%: boost is off here, so it plays at 100%."), "the trace says so:\n" + trace);
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SculptDialog.class);
		String message = context.computeOnClient(mc -> ((SculptDialog) mc.gui.screen()).message().getString());
		check(message.startsWith("A Sound Set here sets a sound above 100%."), "the question says why it asks now: " + message);
		context.takeScreenshot("m10-ask-on-open");
		context.runOnClient(mc -> ((SculptDialog) mc.gui.screen()).choose(1));
		context.waitForScreen(SoundSculptorScreen.class);
		closeScreen(context);
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		context.waitTicks(3);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof SoundSculptorScreen), "asked once this session, not on every open");
		closeScreen(context);
		switchProfile(context, key, name);
	}

	// ---- Helpers ----

	/** What a channel's OpenAL source holds, read on the sound thread. */
	private record AlState(float gain, float maxGain, float maxDistance, int error) {
	}

	/** Reads the sound's channel: queued on the sound thread after vanilla's own setup of the channel. */
	private static AlState alState(ClientGameTestContext context, SoundInstance sound) {
		AtomicReference<AlState> out = new AtomicReference<>();
		context.runOnClient(mc -> {
			Map<SoundInstance, ChannelAccess.ChannelHandle> channels = ((SoundEngineAccessor) ((SoundManagerAccessor) mc.getSoundManager())
					.soundsculptor$soundEngine()).soundsculptor$instanceToChannel();
			ChannelAccess.ChannelHandle handle = channels.get(sound);
			check(handle != null, "the sound has a channel");
			handle.execute(channel -> {
				int source = ((ChannelSource) channel).soundsculptorTest$source();
				float gain = AL10.alGetSourcef(source, AL10.AL_GAIN);
				float maxGain = AL10.alGetSourcef(source, AL10.AL_MAX_GAIN);
				float maxDistance = AL10.alGetSourcef(source, AL10.AL_MAX_DISTANCE);
				out.set(new AlState(gain, maxGain, maxDistance, AL10.alGetError()));
			});
		});
		for (int i = 0; i < 40 && out.get() == null; i++) {
			context.waitTick();
		}
		check(out.get() != null, "the sound thread read the channel");
		return out.get();
	}

	private static Vec3 listener(Minecraft mc) {
		return mc.getSoundManager().getListenerTransform().position();
	}

	private static SoundInstance instance(String id, Minecraft mc, double dx, double dz) {
		Vec3 at = listener(mc);
		return new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(), false, 0,
				SoundInstance.Attenuation.LINEAR, at.x() + dx, at.y(), at.z() + dz, false);
	}

	private static SoundInstance play(ClientGameTestContext context, String id, double dx, double dz) {
		SoundInstance sound = context.computeOnClient(mc -> {
			SoundInstance s = instance(id, mc, dx, dz);
			mc.getSoundManager().play(s);
			return s;
		});
		context.waitTicks(1);
		return sound;
	}

	private static void playAt(ClientGameTestContext context, String id, double distance) {
		context.runOnClient(mc -> mc.getSoundManager().play(instance(id, mc, distance, 0)));
		context.waitTicks(FRAMES);
	}

	private static RecentRow row(String id) {
		return RecentLog.get().sounds().snapshot(Util.getMillis(), SortOrder.MOST_RECENT, null).stream()
				.filter(r -> r.id().equals(id)).findFirst().orElse(null);
	}

	/** The level of the sound's Quick changes entry at Everywhere, or -1 if it has none. */
	private static int volumeAt(String soundId) {
		SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), EVERYWHERE, soundId);
		return entry == null || entry.volume() == null ? -1 : entry.volume();
	}

	/** Sets the sound's level in Everywhere's Quick changes, in a profile that confirmed boost. */
	private static void setLevel(ClientGameTestContext context, String soundId, int level) {
		context.runOnClient(mc -> ProfileManager.change(ws -> QuickActions.apply(
				new Workspace(Profile.empty(ws.profile().name()).withBoostConfirmed(true), ws.library()), EVERYWHERE, soundId,
				new soundsculptor.core.trace.EntryEdit.Volume(level)).after()));
	}

	private static String expectedNote() {
		return BoostUi.note(150).getString();
	}

	private static void switchProfile(ClientGameTestContext context, String key, String name) {
		closeScreen(context);
		context.runOnClient(mc -> {
			ProfileManager.flushSaves();
			ProfileManager.leave();
			ProfileManager.join(key, name);
		});
		context.waitTicks(3);
	}

	private static void openMain(ClientGameTestContext context) {
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
	}

	private static void openPlace(ClientGameTestContext context) {
		context.setScreen(() -> new PlaceSetsScreen(null, EVERYWHERE));
		context.waitForScreen(PlaceSetsScreen.class);
	}

	private static void closeScreen(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.waitTicks(1);
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static EntryEditorScreen editor(Minecraft mc) {
		check(mc.gui.screen() instanceof EntryEditorScreen, "expected the entry editor, found " + mc.gui.screen());
		return (EntryEditorScreen) mc.gui.screen();
	}

	private static PlaceSetsScreen places(Minecraft mc) {
		check(mc.gui.screen() instanceof PlaceSetsScreen, "expected a place's Sound Sets, found " + mc.gui.screen());
		return (PlaceSetsScreen) mc.gui.screen();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
