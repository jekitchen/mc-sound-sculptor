package soundsculptor.gametest;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import soundsculptor.core.cue.CueField;
import soundsculptor.core.cue.PlaceCues;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.CueOverlay;
import soundsculptor.mc.CuePopup;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.ZoneEditorScreen;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptScreen;

/**
 * Cue settings per place (PRD FR-7, decided 2026-11-21): each zone has the cue settings, each one Unchanged by default,
 * passing on to the next bigger zone and then Everywhere.
 *
 * <ul>
 *   <li>A profile saved before the move gets the old app-wide cue settings whether it is joined or read by the Profiles
 *   tab (Rename, Copy from).</li>
 *   <li>A zone that turns "Show cues" off: a muted sound with Cue on shows no cue while the player stands in it; stepping
 *   out to Everywhere, it does.</li>
 *   <li>Two nested zones: the smaller one's text size (50%) wins inside it and makes the cue box half as big; the bigger
 *   one's (150%) applies in the rest of it. The size follows where the player stands now, for the whole box.</li>
 *   <li>The hotkey toggles "Show cues" in the place that decides it (the zone, or Everywhere when no zone sets it) and
 *   names the zone in its message.</li>
 *   <li>The zone editor's Cues… pop-up shows what an Unchanged setting gets and from where; a dropdown opened inside it
 *   comes back to the pop-up, and Save keeps the change. Everywhere's pop-up (Zones tab, Settings) has no Unchanged and
 *   saves at once; Right on the text size slider adds 10%.</li>
 * </ul>
 */
public final class ZoneCuesClientGameTest implements FabricClientGameTest {
	private static final String EXPLODE = "minecraft:entity.generic.explode";
	private static final int FRAMES = 2;
	/** Where the player stands inside both zones, and well outside them. */
	private static final String INSIDE = "tp @a 0.5 -60 0.5 180 0";
	private static final String IN_BASE_ONLY = "tp @a 8.5 -60 0.5 180 0";
	private static final String OUTSIDE = "tp @a 100.5 -60 0.5 180 0";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand(OUTSIDE);
			context.waitTicks(3);
			String key = context.computeOnClient(mc -> ProfileManager.key());
			checkTheMigrationThroughRealLoads(context, key);
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				mc.options.showSubtitles().set(false);
				ProfileManager.update(p -> Profile.empty(p.name())
						.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("q1", EXPLODE).withMute(true).withCue(true))))
						.withZones(List.of(zone("base", "Main base", 20, key, PlaceCues.UNCHANGED.withShowCues(false)),
								zone("corner", "Reading corner", 4, key, PlaceCues.UNCHANGED))));
				CueOverlay.clear();
			});
			try {
				checkAZoneTurnsCuesOff(context, world);
				checkTheHotkeyTogglesTheDecidingPlace(context, world);
				checkNestedTextSizes(context, world);
				checkTheZonePopup(context, world);
				checkEverywheresPopup(context);
			} finally {
				context.runOnClient(mc -> {
					mc.gui.setScreen(null);
					ProfileManager.update(p -> Profile.empty(p.name()));
					ProfileManager.flushSaves();
					CueOverlay.clear();
				});
			}
		}
	}

	/** What settings.json held before 2026-11-21: yellow, 150%, moved left, sound IDs on. */
	private static final PlaceCues APP_WIDE = new PlaceCues(true, true, null, 0xF0E442, 150, new PlaceCues.Offset(-20, 0));
	/** {@link #APP_WIDE} on Everywhere of a profile whose old file had "New mutes get Cue" off. */
	private static final PlaceCues MIGRATED = new PlaceCues(true, true, false, 0xF0E442, 150, new PlaceCues.Offset(-20, 0));
	private static final String OTHER = "zone-cues-old.example.net";

	/** A profile file from before 2026-11-21: no Everywhere cues, and its old cueDefault off. */
	private static String oldFile(String name, String worldKey) {
		return "{\"schemaVersion\": 3, \"name\": \"" + name + "\", \"worldKey\": \"" + worldKey + "\", "
				+ "\"settings\": {\"showDistance\": true, \"cueDefault\": false, \"outlines\": \"editing\"}, "
				+ "\"everywhere\": {\"sets\": []}, \"zones\": []}";
	}

	/**
	 * Decided 2026-11-21: a profile saved before cue settings moved to the places gets the old app-wide ones however it
	 * is read: joining its world, and the Profiles tab (Rename, Copy from), so none of them loses the player's look.
	 */
	private static void checkTheMigrationThroughRealLoads(ClientGameTestContext context, String key) {
		soundsculptor.core.settings.AppSettings before = context.computeOnClient(mc -> soundsculptor.mc.AppSettingsManager.get());
		try {
			context.runOnClient(mc -> {
				soundsculptor.mc.AppSettingsManager.update(s -> s.withLegacyCues(APP_WIDE));
				ProfileManager.flushSaves();
				write(ProfileManager.store().fileFor(OTHER), oldFile("Old world", OTHER));
			});
			// The Profiles tab shows the other world migrated; Rename saves it with that look, not the defaults.
			check(context.computeOnClient(mc -> ProfileManager.storedProfile(OTHER).everywhereCues()).equals(MIGRATED),
					"the Profiles tab reads the old profile with the old app-wide look");
			context.runOnClient(mc -> {
				ProfileManager.saveOther(OTHER, soundsculptor.core.profile.ProfileOps.rename(ProfileManager.storedProfile(OTHER), "Renamed world"));
				ProfileManager.flushSaves();
			});
			String saved = context.computeOnClient(mc -> read(ProfileManager.store().fileFor(OTHER)));
			check(saved.contains("\"color\": \"#F0E442\"") && saved.contains("\"textScale\": 150") && saved.contains("\"newMutesGetCue\": false"),
					"Rename saves the migrated look: " + saved);
			// Copy from the other world brings its migrated look.
			PlaceCues copied = context.computeOnClient(mc -> soundsculptor.core.profile.ProfileOps.copyFrom(ProfileManager.storedProfile(OTHER),
					ProfileManager.profile(), ProfileManager.library()).result().everywhereCues());
			check(copied.equals(MIGRATED), "Copy from carries the migrated look, was " + copied);
			// Joining: this world's own old file is migrated as it loads.
			context.runOnClient(mc -> {
				write(ProfileManager.store().fileFor(key), oldFile("This world", key));
				ProfileManager.reload();
			});
			PlaceCues joined = context.computeOnClient(mc -> ProfileManager.profile().everywhereCues());
			check(joined.equals(MIGRATED), "joining migrates this world's old profile, was " + joined);
		} finally {
			context.runOnClient(mc -> {
				soundsculptor.mc.AppSettingsManager.update(s -> before);
				soundsculptor.mc.AppSettingsManager.flushSaves();
				ProfileManager.delete(OTHER);
				ProfileManager.flushSaves();
			});
		}
	}

	private static void write(java.nio.file.Path file, String text) {
		try {
			java.nio.file.Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	private static String read(java.nio.file.Path file) {
		try {
			return java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	/** A zone with "Show cues: Off": no cue inside; Everywhere's On outside. */
	private static void checkAZoneTurnsCuesOff(ClientGameTestContext context, TestSingleplayerContext world) {
		stand(context, world, INSIDE);
		check(context.computeOnClient(mc -> SoundHook.cuesNow().source(CueField.SHOW_CUES)).equals(PlaceRef.zone("base")),
				"in the base the base decides Show cues");
		check(!play(context), "the explosion is muted");
		check(shown(context).isEmpty(), "inside a zone that turns cues off a muted sound with Cue on shows no cue");
		stand(context, world, OUTSIDE);
		play(context);
		check(shown(context).size() == 1, "stepping out to Everywhere the cue shows, was " + shown(context));
		context.takeScreenshot("zone-cues-everywhere");
	}

	/** FR-7.7 hotkey: it changes the place that decides Show cues and names it. */
	private static void checkTheHotkeyTogglesTheDecidingPlace(ClientGameTestContext context, TestSingleplayerContext world) {
		stand(context, world, INSIDE);
		String message = context.computeOnClient(mc -> CueOverlay.toggleShowCues(mc).getString());
		check(message.equals("Muted-sound cues: On in Main base"), "the message names the zone, was " + message);
		check(context.computeOnClient(mc -> Boolean.TRUE.equals(ProfileManager.profile().zone("base").cues().showCues())),
				"the hotkey turned the base's Show cues on");
		check(context.computeOnClient(mc -> ProfileManager.profile().everywhereCues().showCues()), "Everywhere is unchanged");
		play(context);
		check(shown(context).size() == 1, "with the base's Show cues on the cue shows inside it");
		// Outside, no zone sets it: the hotkey changes Everywhere, with the short message.
		stand(context, world, OUTSIDE);
		message = context.computeOnClient(mc -> CueOverlay.toggleShowCues(mc).getString());
		check(message.equals("Muted-sound cues: Off"), "Everywhere keeps the short message, was " + message);
		check(!context.computeOnClient(mc -> ProfileManager.profile().everywhereCues().showCues()), "Everywhere's Show cues is off");
		check(context.computeOnClient(mc -> Boolean.TRUE.equals(ProfileManager.profile().zone("base").cues().showCues())),
				"the base keeps its own");
		play(context);
		check(shown(context).isEmpty(), "Everywhere off: no cue outside");
		context.runOnClient(CueOverlay::toggleShowCues);
		check(context.computeOnClient(mc -> ProfileManager.profile().everywhereCues().showCues()), "pressed again: on");
	}

	/** Two nested zones: the smaller one's text size wins inside it; the size follows where the player stands now. */
	private static void checkNestedTextSizes(ClientGameTestContext context, TestSingleplayerContext world) {
		stand(context, world, OUTSIDE);
		play(context);
		CueOverlay.Shown normal = shown(context).get(0);
		check(normal.height() == 10, "at 100% a line is 10 px tall, was " + normal);
		context.runOnClient(mc -> ProfileManager.update(p -> p
				.withCues(PlaceRef.zone("base"), p.zone("base").cues().withTextScale(150))
				.withCues(PlaceRef.zone("corner"), PlaceCues.UNCHANGED.withTextScale(50))));
		stand(context, world, INSIDE);
		// A fresh line, so it reads as the one measured outside (no ×2); it plays 10 blocks east, in range below too.
		context.runOnClient(mc -> CueOverlay.clear());
		play(context);
		check(shown(context).size() == 1, "one line in range, was " + shown(context));
		CueOverlay.Shown half = shown(context).get(0);
		check(half.height() == normal.height() / 2 && Math.abs(half.width() - normal.width() / 2) <= 1,
				"in the corner 50% halves the same box: " + normal + " → " + half);
		check(context.computeOnClient(mc -> SoundHook.cuesNow().source(CueField.SHOW_CUES)).equals(PlaceRef.zone("base")),
				"Show cues is Unchanged in the corner, so the bigger base decides it");
		context.takeScreenshot("zone-cues-50-percent");
		// The same line, now 2 blocks away: walking out of the corner into the rest of the base, the base's 150% applies to it.
		stand(context, world, IN_BASE_ONLY);
		List<CueOverlay.Shown> lines = shown(context);
		check(lines.size() == 1 && lines.get(0).height() == 15, "in the rest of the base its 150% applies to the box, was " + lines);
		// The 50% box keeps to the screen and to its place at the bottom right.
		stand(context, world, INSIDE);
		play(context);
		int guiWidth = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
		CueOverlay.Shown line = shown(context).get(0);
		check(line.x() + line.width() <= guiWidth && line.x() + line.width() >= guiWidth - 4, "at 50% the box keeps to the right edge: " + line);
	}

	/** The zone editor's Cues… pop-up: Unchanged says what it gets; a dropdown inside it comes back to it; Save keeps it. */
	private static void checkTheZonePopup(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> mc.gui.setScreen(ZoneEditorScreen.edit(null, ProfileManager.profile().zone("corner"))));
		context.waitForScreen(ZoneEditorScreen.class);
		context.runOnClient(mc -> editor(mc).cuesButton.onPress(null));
		context.waitTicks(1);
		CuePopup popup = context.computeOnClient(mc -> editor(mc).cuePopup);
		check(popup != null && context.computeOnClient(mc -> editor(mc).hasOverlay()), "Cues… opens the pop-up");
		String show = context.computeOnClient(mc -> popup.showCues.getMessage().getString());
		check(show.startsWith("Show cues: Unchanged (On"), "Unchanged says what it gets from the base, was " + show);
		check(context.computeOnClient(mc -> popup.textScale.getMessage().getString()).equals("Text size: 50%"),
				"the corner sets 50%, was " + context.computeOnClient(mc -> popup.textScale.getMessage().getString()));
		check(context.computeOnClient(mc -> popup.offsetX.getMessage().getString()).startsWith("Box left/right: Unchanged (0 px"),
				"the box position is Unchanged");
		check(context.computeOnClient(mc -> popup.textScaleUnchanged.active && !popup.offsetUnchanged.active),
				"Unchanged buttons are on only for settings the zone sets");
		context.takeScreenshot("zone-cues-popup");
		// The dropdown's list opens over the pop-up; choosing Off brings the pop-up back with focus on the dropdown.
		context.runOnClient(mc -> popup.showCues.onPress(null));
		context.waitTicks(1);
		context.runOnClient(mc -> {
			SculptScreen screen = editor(mc);
			for (AbstractWidget option : List.copyOf(screen.overlayWidgets())) {
				if (option instanceof SculptDropdown<?>.Option o && o.option() == CuePopup.Choice.OFF) {
					o.onPress(null);
				}
			}
		});
		context.waitTicks(1);
		check(context.computeOnClient(mc -> editor(mc).hasOverlay() && editor(mc).overlayWidgets().contains(popup.showCues)),
				"after choosing, the pop-up is back");
		check(context.computeOnClient(mc -> editor(mc).getFocused() == popup.showCues), "focus is back on the dropdown");
		check(context.computeOnClient(mc -> popup.showCues.getMessage().getString()).equals("Show cues: Off"), "the dropdown shows Off");
		// Unchanged on the text size: back to what the base gives (150%).
		context.runOnClient(mc -> popup.textScaleUnchanged.onPress(null));
		check(context.computeOnClient(mc -> popup.textScale.getMessage().getString()).equals("Text size: Unchanged (150%)"),
				"Unchanged shows the base's size, was " + context.computeOnClient(mc -> popup.textScale.getMessage().getString()));
		check(context.computeOnClient(mc -> ProfileManager.profile().zone("corner").cues().textScale() == 50), "nothing is saved before Save");
		context.runOnClient(mc -> {
			editor(mc).closeOverlay();
			editor(mc).save();
		});
		context.waitTicks(1);
		PlaceCues saved = context.computeOnClient(mc -> ProfileManager.profile().zone("corner").cues());
		check(saved.equals(PlaceCues.UNCHANGED.withShowCues(false)), "Save keeps the corner's cue settings, was " + saved);
		stand(context, world, INSIDE);
		play(context);
		check(shown(context).isEmpty(), "the corner now turns cues off");
	}

	/** Everywhere's pop-up from the Zones tab and from Settings: no Unchanged, saved at once, the keyboard moves the size. */
	private static void checkEverywheresPopup(ClientGameTestContext context) {
		context.setScreen(SoundSculptorScreen::new);
		context.waitForScreen(SoundSculptorScreen.class);
		context.runOnClient(mc -> main(mc).selectTab(SoundSculptorScreen.TAB_ZONES));
		context.waitTicks(1);
		CuePopup popup = context.computeOnClient(mc -> main(mc).zonesPanel().openEverywhereCues(null));
		check(context.computeOnClient(mc -> popup.textScaleUnchanged == null && popup.offsetUnchanged == null),
				"Everywhere has no Unchanged buttons");
		check(context.computeOnClient(mc -> popup.showCues.getMessage().getString()).equals("Show cues: On"), "Everywhere's switch reads On");
		context.takeScreenshot("zone-cues-everywhere-popup");
		context.runOnClient(mc -> main(mc).setFocused(popup.textScale));
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitTicks(1);
		check(context.computeOnClient(mc -> ProfileManager.profile().everywhereCues().textScale()) == 110, "Right adds 10% and saves at once");
		context.runOnClient(mc -> popup.offsetReset.onPress(null));
		context.runOnClient(mc -> main(mc).closeOverlay());
		// Settings › General: the note and the button that opens the same pop-up.
		context.runOnClient(mc -> {
			main(mc).selectTab(SoundSculptorScreen.TAB_SETTINGS);
			main(mc).setSettingsPage(SoundSculptorScreen.SettingsPage.GENERAL);
		});
		context.waitTicks(1);
		context.runOnClient(mc -> main(mc).cuesButton.onPress(null));
		context.waitTicks(1);
		check(context.computeOnClient(mc -> main(mc).hasOverlay()), "Everywhere's cues… opens the pop-up from Settings");
		context.takeScreenshot("zone-cues-settings-popup");
		context.runOnClient(mc -> main(mc).closeOverlay());
		check(context.computeOnClient(mc -> main(mc).getFocused() == main(mc).cuesButton), "closing it puts focus back on the button");
		context.setScreen(() -> null);
	}

	// ---- Helpers ----

	private static Zone zone(String id, String name, int size, String key, PlaceCues cues) {
		return new Zone(id, name, true, key, "minecraft:overworld", new Point(0.5, -60, 0.5), new Shape.Box(size, size, size),
				PlaceSets.EMPTY, cues);
	}

	private static void stand(ClientGameTestContext context, TestSingleplayerContext world, String tp) {
		world.getServer().runCommand(tp);
		context.waitTicks(4);
	}

	/** Plays the muted explosion 10 blocks east of the listener; returns whether it started. */
	private static boolean play(ClientGameTestContext context) {
		boolean started = context.computeOnClient(mc -> {
			Vec3 at = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(EXPLODE), SoundSource.NEUTRAL, 1.0F, 1.0F,
					RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, at.x() + 10, at.y(), at.z(), false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound);
		});
		context.waitTicks(FRAMES);
		return started;
	}

	private static List<CueOverlay.Shown> shown(ClientGameTestContext context) {
		context.waitTicks(FRAMES);
		return context.computeOnClient(mc -> CueOverlay.shown());
	}

	private static ZoneEditorScreen editor(Minecraft mc) {
		check(mc.gui.screen() instanceof ZoneEditorScreen, "expected the zone editor, found " + mc.gui.screen());
		return (ZoneEditorScreen) mc.gui.screen();
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
