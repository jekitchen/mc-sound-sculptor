package soundsculptor.gametest;

import soundsculptor.SoundSculptorClient;
import soundsculptor.core.cue.CueDirection;
import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.settings.AppSettings;
import soundsculptor.core.trace.EntryEdit;
import soundsculptor.mc.AppSettingsManager;
import soundsculptor.mc.CueOverlay;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Automated version of the M9 milestone test (PRD 11.1): the FR-7 acceptance criteria, as far as they can be checked
 * without listening. The cue box is read from the HUD element's last drawn frame ({@link CueOverlay#shown}), and
 * screenshots are kept for the look (italics, the "(muted)" prefix and icon, the color, the place above the subtitles).
 *
 * <ul>
 *   <li>A muted explosion with Cue on shows "(muted) Explosion" with the arrow vanilla's subtitle overlay would show,
 *   worked out every frame; turning the player turns the arrow.</li>
 *   <li>Cue off on the entry (entry editor) shows nothing; Cue on again from the Sounds tab and from the trace.</li>
 *   <li>A muted villager out of vanilla range shows no cue; in range it does.</li>
 *   <li>Ten muted villagers at once: at most 5 lines, repeats merged with a count.</li>
 *   <li>"Show muted-sound cues" off (Settings) shows nothing, on again shows cues; the same with the hotkey's action.
 *   Vanilla Subtitles on or off makes no difference, and with Subtitles on the cue box sits above the subtitle box.</li>
 *   <li>A play dropped by a repeat limit never adds a cue (FR-6.8); a sound without a subtitle shows its ID only with
 *   "Cue sounds without subtitles" on (FR-7.2); "New mutes get Cue" sets the Cue of a new mute (FR-7.6).</li>
 * </ul>
 */
public final class M9CuesClientGameTest implements FabricClientGameTest {
	private static final String EXPLODE = "minecraft:entity.generic.explode";
	private static final String VILLAGERS = "minecraft:entity.villager.*";
	private static final String VILLAGER_AMBIENT = "minecraft:entity.villager.ambient";
	private static final String[] TEN_VILLAGERS = {"minecraft:entity.villager.yes", "minecraft:entity.villager.no",
			"minecraft:entity.villager.trade", "minecraft:entity.villager.celebrate", "minecraft:entity.villager.hurt",
			"minecraft:entity.villager.hurt", VILLAGER_AMBIENT, VILLAGER_AMBIENT, VILLAGER_AMBIENT, VILLAGER_AMBIENT};
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final PlaceRef EVERYWHERE = PlaceRef.EVERYWHERE;
	/** Frames for the HUD to draw what the last step changed. */
	private static final int FRAMES = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		boolean subtitlesBefore = context.computeOnClient(mc -> mc.options.showSubtitles().get());
		double notificationTimeBefore = context.computeOnClient(mc -> mc.options.notificationDisplayTime().get());
		AppSettings settingsBefore = context.computeOnClient(mc -> AppSettingsManager.get());
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			// Facing north (yaw 180): east (+x) is to the right.
			world.getServer().runCommand("tp @a 0.5 -60 0.5 180 0");
			context.waitTicks(3);
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
				AppSettingsManager.update(s -> AppSettings.DEFAULTS.withLastTab(s.lastTab()));
				mc.options.showSubtitles().set(false);
				CueOverlay.clear();
			});

			checkHotkeyIsOptional(context);
			checkExplosion(context);
			checkArrowLikeVanilla(context);
			checkCueOff(context);
			checkRange(context);
			checkTenVillagers(context);
			checkMasterSwitch(context);
			checkNoVanillaSubtitleWhenMuted(context);
			checkVanillaSubtitles(context);
			checkNotificationTime(context);
			checkScaleAndOffset(context);
			checkRepeatLimit(context);
			checkSoundsWithoutSubtitles(context);
			checkCueDefault(context);
			checkSettingsPage(context);
		} finally {
			context.runOnClient(mc -> {
				mc.options.showSubtitles().set(subtitlesBefore);
				mc.options.notificationDisplayTime().set(notificationTimeBefore);
				AppSettingsManager.update(s -> settingsBefore);
				AppSettingsManager.flushSaves();
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
				CueOverlay.clear();
			});
		}
	}

	/** FR-7.7: the hotkey is in the mod's own category in Controls and unbound by default. */
	private static void checkHotkeyIsOptional(ClientGameTestContext context) {
		check(context.computeOnClient(mc -> SoundSculptorClient.cuesKey.isUnbound()), "the cue hotkey is unbound by default");
		check(context.computeOnClient(mc -> SoundSculptorClient.cuesKey.getCategory() == SoundSculptorClient.KEY_CATEGORY),
				"the cue hotkey is in Sound Sculptor's Controls category");
	}

	/** FR-7 AC 1: a muted explosion with Cue on shows "(muted) Explosion" with vanilla's arrow. */
	private static void checkExplosion(ClientGameTestContext context) {
		// A quick Mute, as the Sounds tab's button makes it: Cue comes from "New mutes get Cue" (FR-7.6), on by default.
		mute(context, EXPLODE);
		check(context.computeOnClient(mc -> Boolean.TRUE.equals(QuickActions.quickEntry(ProfileManager.workspace(), EVERYWHERE, EXPLODE).cue())),
				"a quick Mute starts with Cue on");
		check(!play(context, EXPLODE, 10, 0), "the explosion is muted");
		List<CueOverlay.Shown> lines = shown(context);
		check(lines.size() == 1, "one cue line, was " + lines);
		CueOverlay.Shown line = lines.get(0);
		check(line.text().equals("Explosion") && line.count() == 1, "the line reads (muted) Explosion, was " + line);
		check(line.arrow() == CueDirection.RIGHT, "east of a player facing north is to the right, was " + line.arrow());
		check((line.argb() & 0xFFFFFF) != 0xFFFFFF, "the line is in the cue color, not white");
		int guiWidth = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
		int guiHeight = context.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
		check(line.x() + line.width() <= guiWidth && line.x() + line.width() >= guiWidth - 4, "the box is at the right edge, was " + line);
		check(line.y() + line.height() <= guiHeight - 30 + 1 && line.y() >= guiHeight - 45, "with Subtitles off the box is where subtitles go, was " + line);
		context.takeScreenshot("m9-cue-explosion");

		// The arrow follows the listener every frame, as vanilla's does: face south and the same line points left.
		context.runOnClient(mc -> mc.player.setYRot(0));
		context.waitTicks(FRAMES);
		check(shown(context).get(0).arrow() == CueDirection.LEFT, "facing south, east is to the left");
		context.runOnClient(mc -> mc.player.setYRot(180));
		// In front: no arrow.
		clear(context);
		play(context, EXPLODE, 0, -10);
		check(shown(context).get(0).arrow() == CueDirection.NONE, "a sound straight ahead has no arrow");
		// A repeat merges into the line with a count.
		play(context, EXPLODE, 0, -10);
		check(shown(context).get(0).count() == 2, "a repeat counts on the same line");
		context.takeScreenshot("m9-cue-explosion-x2");
	}

	/**
	 * FR-7 AC 1 and FR-7.4: the cue's arrow is the one vanilla draws. With Subtitles on, an unmuted cow and the muted
	 * explosion play at the same place; vanilla's row for the cow (recorded by {@code SubtitleOverlayRecorder}) and the
	 * cue line must show the same arrow, facing north and facing south.
	 */
	private static void checkArrowLikeVanilla(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.options.showSubtitles().set(true));
		// Vanilla starts listening for sounds at its next frame with Subtitles on.
		context.waitTicks(FRAMES);
		String cow = subtitle(context, COW);
		for (float yaw : new float[] {180, 0}) {
			context.runOnClient(mc -> mc.player.setYRot(yaw));
			clear(context);
			check(play(context, COW, 10, 0), "the cow is not muted");
			play(context, EXPLODE, 10, 0);
			context.waitTicks(FRAMES);
			List<VanillaSubtitles.Row> rows = context.computeOnClient(mc -> VanillaSubtitles.lastFrame());
			VanillaSubtitles.Row cowRow = rows.stream().filter(r -> r.text().equals(cow)).findFirst().orElse(null);
			check(cowRow != null, "vanilla shows the cow's subtitle, rows " + rows);
			check(rows.stream().noneMatch(r -> r.text().equals("Explosion")), "vanilla shows no subtitle for the muted explosion, rows " + rows);
			CueDirection arrow = shown(context).get(0).arrow();
			check(cowRow.arrow().equals(arrowText(arrow)), "the cue's arrow " + arrow + " is vanilla's \"" + cowRow.arrow() + "\" (yaw " + yaw + ")");
			check(!cowRow.arrow().isEmpty(), "a sound to the side has an arrow (yaw " + yaw + ")");
		}
		context.runOnClient(mc -> {
			mc.player.setYRot(180);
			mc.options.showSubtitles().set(false);
		});
	}

	private static String arrowText(CueDirection arrow) {
		return arrow == CueDirection.RIGHT ? ">" : arrow == CueDirection.LEFT ? "<" : "";
	}

	/**
	 * Decided 2026-11-21: with Subtitles on, a sound Sound Sculptor mutes never shows in vanilla's subtitle box. Cue on
	 * shows only the cue; Cue off or the master switch off shows nothing at all (FR-7 AC 2 and AC 6).
	 */
	private static void checkNoVanillaSubtitleWhenMuted(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.options.showSubtitles().set(true));
		// Let earlier subtitles run out (3 s).
		context.waitTicks(70);
		check(context.computeOnClient(mc -> vanillaRows(mc)) == 0, "no subtitles left over");
		clear(context);
		play(context, EXPLODE, 10, 0);
		check(context.computeOnClient(mc -> vanillaRows(mc)) == 0, "a muted explosion with Cue on: no vanilla subtitle");
		check(shown(context).size() == 1, "only the cue");
		setExplosionCue(context, false);
		clear(context);
		play(context, EXPLODE, 10, 0);
		check(context.computeOnClient(mc -> vanillaRows(mc)) == 0, "Cue off: no vanilla subtitle");
		check(shown(context).isEmpty(), "Cue off: no cue");
		setExplosionCue(context, true);
		everywhere(context, c -> c.withShowCues(false));
		play(context, EXPLODE, 10, 0);
		check(context.computeOnClient(mc -> vanillaRows(mc)) == 0, "master switch off: no vanilla subtitle");
		check(shown(context).isEmpty(), "master switch off: no cue");
		context.runOnClient(mc -> {
			ProfileManager.update(p -> p.withEverywhereCues(p.everywhereCues().withShowCues(true)));
			mc.options.showSubtitles().set(false);
		});
	}

	private static void setExplosionCue(ClientGameTestContext context, boolean on) {
		context.runOnClient(mc -> ProfileManager.change(ws -> QuickActions.putQuickEntry(ws, EVERYWHERE,
				QuickActions.quickEntry(ws, EVERYWHERE, EXPLODE).withCue(on))));
	}

	/** FR-7.4: lines last 3 seconds times vanilla's Notification Time option, as subtitles do. */
	private static void checkNotificationTime(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.options.notificationDisplayTime().set(2.0));
		clear(context);
		play(context, EXPLODE, 10, 0);
		context.waitTicks(70);
		check(shown(context).size() == 1, "at Notification Time 2 a line is still up after 3.5 s");
		context.runOnClient(mc -> mc.options.notificationDisplayTime().set(1.0));
		context.waitTicks(FRAMES);
		check(shown(context).isEmpty(), "at Notification Time 1 it is gone after 3.5 s");
	}

	/** A-7 and section 8.1: the cue text scale grows the box, the offset moves it, and it stays on screen. */
	private static void checkScaleAndOffset(ClientGameTestContext context) {
		int guiWidth = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
		int guiHeight = context.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
		clear(context);
		play(context, EXPLODE, 10, 0);
		CueOverlay.Shown normal = shown(context).get(0);
		everywhere(context, c -> c.withTextScale(200));
		context.waitTicks(FRAMES);
		CueOverlay.Shown big = shown(context).get(0);
		check(big.height() == 2 * normal.height() && Math.abs(big.width() - 2 * normal.width()) <= 1, "200% doubles the box: " + normal + " → " + big);
		check(Math.abs(big.x() + big.width() - (normal.x() + normal.width())) <= 2, "it grows to the left from the same right edge");
		context.takeScreenshot("m9-cue-scaled");
		everywhere(context, c -> c.withTextScale(100).withBoxOffset(new PlaceCues.Offset(-100, -50)));
		context.waitTicks(FRAMES);
		CueOverlay.Shown moved = shown(context).get(0);
		check(Math.abs(moved.x() - (normal.x() - 100)) <= 1 && Math.abs(moved.y() - (normal.y() - 50)) <= 1, "the offset moves it: " + normal + " → " + moved);
		everywhere(context, c -> c.withBoxOffset(new PlaceCues.Offset(PlaceCues.MAX_OFFSET, PlaceCues.MAX_OFFSET)));
		context.waitTicks(FRAMES);
		CueOverlay.Shown edge = shown(context).get(0);
		check(edge.x() + edge.width() <= guiWidth && edge.y() + edge.height() <= guiHeight && edge.x() >= 0 && edge.y() >= 0,
				"a large offset keeps it on screen: " + edge);
		everywhere(context, c -> c.withBoxOffset(PlaceCues.Offset.ZERO));
	}

	/** FR-7 AC 2: Cue off on the entry shows nothing; on again from the Sounds tab and from the trace. */
	private static void checkCueOff(ClientGameTestContext context) {
		openMain(context);
		context.runOnClient(mc -> mc.gui.setScreen(EntryEditorScreen.forSound(main(mc), EVERYWHERE, EXPLODE)));
		context.waitForScreen(EntryEditorScreen.class);
		check(context.computeOnClient(mc -> editor(mc).cueDropdown.value() == EntryEditorScreen.CueChoice.ON), "the editor shows Cue: On");
		context.takeScreenshot("m9-entry-editor-cue");
		context.runOnClient(mc -> {
			editor(mc).setCue(EntryEditorScreen.CueChoice.OFF);
			editor(mc).save();
		});
		context.waitForScreen(SoundSculptorScreen.class);
		closeScreen(context);
		clear(context);
		check(!play(context, EXPLODE, 10, 0), "still muted");
		check(shown(context).isEmpty(), "with Cue off the explosion shows nothing");

		// Sounds tab: the row's Level, cue and limit… pop-up has Cue, showing the actual state; it turns the cue back on, and
		// closing the pop-up offers Undo.
		openMain(context);
		context.runOnClient(mc -> {
			SoundSculptorScreen screen = main(mc);
			screen.selectTab(SoundSculptorScreen.TAB_SOUNDS);
			screen.sounds().setRecentOnly(false);
		});
		context.waitTicks(1);
		String levelLabel = context.computeOnClient(mc -> rowButton(main(mc).sounds().list, EXPLODE, SoundsPanel.BUTTON_LEVEL).getMessage().getString());
		check(levelLabel.equals("Level, cue and limit…"), "the row's button names the cue too, was " + levelLabel);
		context.runOnClient(mc -> main(mc).sounds().openLevel(EXPLODE));
		context.waitTicks(1);
		check(context.computeOnClient(mc -> main(mc).sounds().levelCue != null && !main(mc).sounds().levelCue.value()),
				"the pop-up's Cue shows Off");
		context.takeScreenshot("m9-sounds-level-cue");
		context.runOnClient(mc -> main(mc).sounds().levelCue.onPress(null));
		check(context.computeOnClient(mc -> QuickActions.isCueOn(ProfileManager.workspace(), EVERYWHERE, EXPLODE)), "Cue turned the cue on");
		check(context.computeOnClient(mc -> main(mc).sounds().levelCue.value()), "the toggle shows On");
		context.runOnClient(mc -> main(mc).closeOverlay());
		context.waitTicks(1);
		String toast = context.computeOnClient(mc -> main(mc).toast() == null ? null : main(mc).toast().message().getString());
		check("Cue on for Explosion".equals(toast), "closing the pop-up offers Undo, was " + toast);
		context.runOnClient(mc -> main(mc).pressUndo());
		check(!context.computeOnClient(mc -> QuickActions.isCueOn(ProfileManager.workspace(), EVERYWHERE, EXPLODE)), "Undo puts Cue off back");
		closeScreen(context);

		// Trace: the entry line's Cue button turns it on, and the trace shows it.
		context.runOnClient(mc -> mc.gui.setScreen(new TraceScreen(null, EXPLODE)));
		context.waitForScreen(TraceScreen.class);
		SculptButton traceCue = context.computeOnClient(mc -> cueButton(((TraceScreen) mc.gui.screen()).lines));
		check(traceCue.getMessage().getString().equals("Cue: Off"), "the trace's Cue shows the actual state, was " + traceCue.getMessage().getString());
		context.runOnClient(mc -> traceCue.onPress(null));
		context.waitTicks(1);
		check(context.computeOnClient(mc -> QuickActions.isCueOn(ProfileManager.workspace(), EVERYWHERE, EXPLODE)), "the trace turned the cue on");
		check(context.computeOnClient(mc -> cueButton(((TraceScreen) mc.gui.screen()).lines).getMessage().getString()).equals("Cue: On"),
				"the trace shows Cue: On after the edit");
		check(context.computeOnClient(mc -> ((TraceScreen) mc.gui.screen()).text().contains("Cue on")), "the trace text says Cue on");
		context.takeScreenshot("m9-trace-cue");
		closeScreen(context);
		clear(context);
		play(context, EXPLODE, 10, 0);
		check(shown(context).size() == 1, "with Cue on again the explosion shows its cue");
	}

	/** FR-7 AC 3 and FR-7.1: a muted villager beyond vanilla range (16 blocks at volume 1) shows no cue. */
	private static void checkRange(ClientGameTestContext context) {
		// A group entry made in full: Mute with Cue on.
		context.runOnClient(mc -> ProfileManager.update(p -> QuickEntries.put(p, SetEntry.of(QuickEntries.newId(p), VILLAGERS).withMute(true).withCue(true))));
		clear(context);
		check(!play(context, VILLAGER_AMBIENT, 25, 0), "the villager is muted");
		check(shown(context).isEmpty(), "25 blocks away is out of range: no cue");
		play(context, VILLAGER_AMBIENT, 10, 0);
		check(shown(context).size() == 1, "10 blocks away is in range: a cue");
	}

	/** FR-7 AC 5: ten muted villagers at once give no more than 5 lines, with counts. */
	private static void checkTenVillagers(ClientGameTestContext context) {
		clear(context);
		context.runOnClient(mc -> {
			Vec3 at = mc.getSoundManager().getListenerTransform().position();
			for (int i = 0; i < TEN_VILLAGERS.length; i++) {
				mc.getSoundManager().play(instance(TEN_VILLAGERS[i], at.x() + 3 + i % 3, at.y(), at.z() - 3 + i % 4));
			}
		});
		context.waitTicks(FRAMES);
		List<CueOverlay.Shown> lines = shown(context);
		check(lines.size() == 5, "at most 5 lines, was " + lines.size() + ": " + lines);
		String mumbles = subtitle(context, VILLAGER_AMBIENT);
		String hurts = subtitle(context, "minecraft:entity.villager.hurt");
		check(lines.stream().anyMatch(l -> l.text().equals(mumbles) && l.count() == 4), "\"" + mumbles + " ×4\" is one line, was " + lines);
		check(lines.stream().anyMatch(l -> l.text().equals(hurts) && l.count() == 2), "\"" + hurts + " ×2\" is one line, was " + lines);
		context.takeScreenshot("m9-cue-ten-villagers");
	}

	/** FR-7 AC 6: the master switch hides every cue and brings them back, whatever vanilla Subtitles says. */
	private static void checkMasterSwitch(ClientGameTestContext context) {
		for (boolean subtitles : new boolean[] {false, true}) {
			context.runOnClient(mc -> mc.options.showSubtitles().set(subtitles));
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhereCues().showCues()), "Show muted-sound cues is on");
			everywhere(context, c -> c.withShowCues(false));
			clear(context);
			play(context, EXPLODE, 10, 0);
			check(shown(context).isEmpty(), "with the master switch off a muted explosion with Cue on shows nothing (Subtitles " + subtitles + ")");
			// The hotkey's action turns it back on, in Everywhere: no zone here sets it.
			context.runOnClient(CueOverlay::toggleShowCues);
			check(context.computeOnClient(mc -> ProfileManager.profile().everywhereCues().showCues()), "the hotkey turned the master switch on");
			play(context, EXPLODE, 10, 0);
			check(shown(context).size() == 1, "with the master switch on the cue returns (Subtitles " + subtitles + ")");
		}
		context.runOnClient(mc -> mc.options.showSubtitles().set(false));
	}

	/** FR-7 AC 4 and FR-7.3: with Subtitles on, a subtitle and a cue are both shown, the cue box above the subtitle box. */
	private static void checkVanillaSubtitles(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.options.showSubtitles().set(true));
		context.waitTicks(FRAMES);
		clear(context);
		check(play(context, COW, -6, 0), "the cow is not muted");
		play(context, EXPLODE, 10, 0);
		context.waitTicks(FRAMES);
		int rows = context.computeOnClient(mc -> vanillaRows(mc));
		check(rows >= 1, "vanilla shows the cow's subtitle");
		check(context.computeOnClient(mc -> VanillaSubtitles.lastFrame()).stream().noneMatch(r -> r.text().equals("Explosion")),
				"vanilla shows no subtitle for the muted explosion");
		List<CueOverlay.Shown> lines = shown(context);
		check(lines.size() == 1, "the cue shows with Subtitles on too, was " + lines);
		int guiHeight = context.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
		// Vanilla's top subtitle row starts 30 + 10 × rows pixels above the bottom.
		int subtitleTop = guiHeight - 30 - 10 * rows;
		check(lines.get(0).y() + lines.get(0).height() <= subtitleTop, "the cue box sits above the subtitle box: " + lines.get(0) + ", subtitles top " + subtitleTop);
		context.takeScreenshot("m9-cue-with-subtitles");
		context.runOnClient(mc -> mc.options.showSubtitles().set(false));
	}

	/** FR-6.8: a play dropped by a repeat limit never adds a cue. */
	private static void checkRepeatLimit(ClientGameTestContext context) {
		context.runOnClient(mc -> ProfileManager.change(ws -> QuickActions.putQuickEntry(ws, EVERYWHERE,
				QuickActions.quickEntry(ws, EVERYWHERE, EXPLODE).withLimit(new Limit(1, 10_000, false)))));
		clear(context);
		play(context, EXPLODE, 10, 0);
		play(context, EXPLODE, 10, 0);
		play(context, EXPLODE, 10, 0);
		List<CueOverlay.Shown> lines = shown(context);
		check(lines.size() == 1 && lines.get(0).count() == 1, "only the allowed play shows a cue, was " + lines);
		context.runOnClient(mc -> ProfileManager.change(ws -> QuickActions.putQuickEntry(ws, EVERYWHERE,
				QuickActions.quickEntry(ws, EVERYWHERE, EXPLODE).withLimit(null))));
	}

	/** FR-7.2: a sound without a subtitle shows its ID, only with "Cue sounds without subtitles" on. */
	private static void checkSoundsWithoutSubtitles(ClientGameTestContext context) {
		String silent = context.computeOnClient(M9CuesClientGameTest::soundWithoutSubtitle);
		mute(context, silent);
		clear(context);
		play(context, silent, 4, 0);
		check(shown(context).isEmpty(), "a sound without a subtitle shows no cue by default (" + silent + ")");
		everywhere(context, c -> c.withWithoutSubtitles(true));
		play(context, silent, 4, 0);
		List<CueOverlay.Shown> lines = shown(context);
		check(lines.size() == 1 && lines.get(0).text().equals(silent), "with the setting on, its cue shows the ID, was " + lines);
		everywhere(context, c -> c.withWithoutSubtitles(false));
	}

	/** FR-7.6: "New mutes get Cue" sets the Cue of a quick Mute and of Mute in the entry editor. */
	private static void checkCueDefault(ClientGameTestContext context) {
		everywhere(context, c -> c.withNewMutesGetCue(false));
		mute(context, COW);
		check(context.computeOnClient(mc -> Boolean.FALSE.equals(QuickActions.quickEntry(ProfileManager.workspace(), EVERYWHERE, COW).cue())),
				"with the default off a quick Mute has Cue off");
		openMain(context);
		context.runOnClient(mc -> mc.gui.setScreen(EntryEditorScreen.forSound(main(mc), EVERYWHERE, "minecraft:entity.pig.ambient")));
		context.waitForScreen(EntryEditorScreen.class);
		context.runOnClient(mc -> editor(mc).setMute(EntryEditorScreen.MuteChoice.MUTE));
		check(context.computeOnClient(mc -> editor(mc).cueDropdown.value() == EntryEditorScreen.CueChoice.OFF), "Mute in the editor takes the default: Off");
		everywhere(context, c -> c.withNewMutesGetCue(true));
		closeScreen(context);
		openMain(context);
		context.runOnClient(mc -> mc.gui.setScreen(EntryEditorScreen.forSound(main(mc), EVERYWHERE, "minecraft:entity.pig.ambient")));
		context.waitForScreen(EntryEditorScreen.class);
		context.runOnClient(mc -> editor(mc).setMute(EntryEditorScreen.MuteChoice.MUTE));
		check(context.computeOnClient(mc -> editor(mc).cueDropdown.value() == EntryEditorScreen.CueChoice.ON), "with the default on: On");
		// An explicit choice is kept: Off, then Mute again, stays Off.
		context.runOnClient(mc -> {
			editor(mc).setCue(EntryEditorScreen.CueChoice.OFF);
			editor(mc).setMute(EntryEditorScreen.MuteChoice.NOT_SET);
			editor(mc).setMute(EntryEditorScreen.MuteChoice.MUTE);
		});
		check(context.computeOnClient(mc -> editor(mc).cueDropdown.value() == EntryEditorScreen.CueChoice.OFF), "a chosen Cue is kept");
		closeScreen(context);
	}

	/**
	 * Settings › General and Accessibility no longer have the cue settings (decided 2026-11-21): each says they are set
	 * per zone. Screenshots for the layout.
	 */
	private static void checkSettingsPage(ClientGameTestContext context) {
		openMain(context);
		context.runOnClient(mc -> {
			main(mc).selectTab(SoundSculptorScreen.TAB_SETTINGS);
			main(mc).setSettingsPage(SoundSculptorScreen.SettingsPage.GENERAL);
		});
		context.waitTicks(1);
		check(context.computeOnClient(mc -> labels(main(mc)).stream().anyMatch(t -> t.startsWith("Cue settings are set per zone"))),
				"the General page says where the cue settings are");
		context.takeScreenshot("m9-settings-general");
		context.runOnClient(mc -> main(mc).setSettingsPage(SoundSculptorScreen.SettingsPage.ACCESSIBILITY));
		context.waitTicks(1);
		check(context.computeOnClient(mc -> labels(main(mc)).stream().anyMatch(t -> t.startsWith("Cue settings are set per zone"))),
				"the Accessibility page says it too");
		context.takeScreenshot("m9-settings-accessibility");
		closeScreen(context);
	}

	/** Changes Everywhere's cue settings, as its Cues pop-up does. */
	private static void everywhere(ClientGameTestContext context, java.util.function.UnaryOperator<PlaceCues> change) {
		context.runOnClient(mc -> ProfileManager.update(p -> p.withEverywhereCues(change.apply(p.everywhereCues()))));
	}

	private static List<String> labels(SoundSculptorScreen screen) {
		List<String> texts = new java.util.ArrayList<>();
		for (soundsculptor.mc.ui.SculptScreen.Body.Label label : screen.body().labels()) {
			texts.add(label.text().get().getString());
		}
		return texts;
	}

	// ---- Helpers ----

	/** A quick Mute at Everywhere, as the Sounds tab's button makes it (Cue from the profile's default). */
	private static void mute(ClientGameTestContext context, String soundId) {
		context.runOnClient(mc -> ProfileManager.change(ws -> QuickActions.apply(ws, EVERYWHERE, soundId,
				new EntryEdit.MuteWithCue(soundsculptor.mc.SoundSculptorSettings.newMutesGetCue())).after()));
	}

	private static SoundInstance instance(String id, double x, double y, double z) {
		return new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(), false, 0,
				SoundInstance.Attenuation.LINEAR, x, y, z, false);
	}

	/** Plays a sound {@code dx}, {@code dz} blocks from the listener; returns whether it started. */
	private static boolean play(ClientGameTestContext context, String id, double dx, double dz) {
		boolean started = context.computeOnClient(mc -> {
			Vec3 at = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = instance(id, at.x() + dx, at.y(), at.z() + dz);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound);
		});
		context.waitTicks(FRAMES);
		return started;
	}

	/** The cue lines in the HUD's last frame. */
	private static List<CueOverlay.Shown> shown(ClientGameTestContext context) {
		return context.computeOnClient(mc -> CueOverlay.shown());
	}

	private static void clear(ClientGameTestContext context) {
		context.runOnClient(mc -> CueOverlay.clear());
	}

	private static int vanillaRows(Minecraft mc) {
		Object overlay = ((soundsculptor.mc.mixin.HudAccessor) mc.gui.hud).soundsculptor$subtitleOverlay();
		return ((soundsculptor.mc.mixin.SubtitleOverlayAccessor) overlay).soundsculptor$audibleSubtitles().size();
	}

	private static String subtitle(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> mc.getSoundManager().getSoundEvent(Identifier.parse(id)).getSubtitle().getString());
	}

	/** A short sound with no subtitle that actually plays, for FR-7.2: a block, entity or item sound if there is one. */
	private static String soundWithoutSubtitle(Minecraft mc) {
		String fallback = null;
		for (Identifier id : mc.getSoundManager().getAvailableSounds()) {
			WeighedSoundEvents events = mc.getSoundManager().getSoundEvent(id);
			if (events == null || events.getSubtitle() != null || id.getPath().startsWith("music")) {
				continue;
			}
			net.minecraft.client.resources.sounds.Sound sound = events.getSound(RandomSource.create());
			if (sound == net.minecraft.client.sounds.SoundManager.EMPTY_SOUND
					|| sound == net.minecraft.client.sounds.SoundManager.INTENTIONALLY_EMPTY_SOUND) {
				continue;
			}
			String path = id.getPath();
			if (path.startsWith("block.") || path.startsWith("entity.") || path.startsWith("item.")) {
				return id.toString();
			}
			if (fallback == null) {
				fallback = id.toString();
			}
		}
		check(fallback != null, "no sound without a subtitle");
		return fallback;
	}

	private static SculptButton rowButton(SculptList list, String key, int index) {
		SculptList.Row row = list.findRow(key);
		check(row != null, "no row for " + key);
		return row.buttons().get(index);
	}

	/** The Cue button on the trace's first entry line. */
	private static SculptButton cueButton(SculptList lines) {
		for (SculptList.Row row : lines.children()) {
			for (SculptButton button : row.buttons()) {
				if (button.getMessage().getString().startsWith("Cue: ")) {
					return button;
				}
			}
		}
		throw new AssertionError("the trace has no Cue button");
	}

	private static void openMain(ClientGameTestContext context) {
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
