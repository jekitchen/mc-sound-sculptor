package soundsculptor.gametest;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SetEditorScreen;
import soundsculptor.mc.SoundPickerScreen;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundSculptorSettings;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.ZoneEditorScreen;

/**
 * Screenshots for the mod page (docs/MOD_PAGE.md, docs/RELEASING.md). Off unless the run is started with
 * {@code -Pscreenshots=<folder>}: then it builds a small scene (a villager trading hall and a cow field, each a zone with
 * its own Sound Set, and a set on Everywhere), plays the sounds you would hear there, and saves 1920 x 1080 screenshots
 * at GUI scale 3 to that folder. It checks nothing; the pictures are for a person to choose from.
 */
public final class ModPageScreenshotsClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final SoundSet HALL = new SoundSet("shots-hall", "Quiet trading hall", List.of(
			SetEntry.of("e1", "minecraft:entity.villager.*").withVolume(25),
			SetEntry.of("e2", "minecraft:entity.villager.work_*").withMute(true),
			SetEntry.of("e3", "minecraft:block.anvil.*").withMute(true)));
	private static final SoundSet FARM = new SoundSet("shots-farm", "Farm animals", List.of(
			SetEntry.of("e1", "minecraft:entity.cow.*").withVolume(40),
			SetEntry.of("e2", "minecraft:entity.chicken.*").withMute(true),
			SetEntry.of("e3", "minecraft:entity.pig.ambient").withVolume(60)));
	private static final SoundSet WEATHER = new SoundSet("shots-weather", "Softer weather", List.of(
			SetEntry.of("e1", "minecraft:weather.rain*").withVolume(30),
			SetEntry.of("e2", "minecraft:entity.lightning_bolt.thunder").withVolume(50)));
	private static final SoundSet MOBS = new SoundSet("shots-mobs", "Mob farm extras", List.of(
			SetEntry.of("e1", "minecraft:entity.zombie.*").withVolume(35),
			SetEntry.of("e2", "minecraft:entity.skeleton.ambient").withMute(true)));

	@Override
	public void runTest(ClientGameTestContext context) {
		String dir = System.getProperty("soundsculptor.screenshotsDir", "");
		if (dir.isEmpty()) {
			return;
		}
		Path out = Path.of(dir);
		context.getInput().resizeWindow(1920, 1080);
		int originalScale = context.computeOnClient(mc -> mc.options.guiScale().get());
		OutlineMode originalOutlines = context.computeOnClient(mc -> SoundSculptorSettings.outlineMode());
		context.runOnClient(mc -> {
			mc.options.guiScale().set(3);
			mc.resizeGui();
		});
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			BlockPos start = context.computeOnClient(mc -> mc.player.blockPosition());
			int x = start.getX();
			int y = start.getY();
			int z = start.getZ();
			// The trading hall is centred on the start; the cow field is 16 blocks east.
			String[] commands = {
					"time set noon", "weather clear",
					fence(x - 5, y, z - 5, x + 5, z - 5), fence(x - 5, y, z + 5, x + 5, z + 5),
					fence(x - 5, y, z - 5, x - 5, z + 5), fence(x + 5, y, z - 5, x + 5, z + 5),
					fence(x + 11, y, z - 5, x + 21, z - 5), fence(x + 11, y, z + 5, x + 21, z + 5),
					fence(x + 11, y, z - 5, x + 11, z + 5), fence(x + 21, y, z - 5, x + 21, z + 5),
					"setblock %d %d %d minecraft:lectern".formatted(x - 3, y, z - 3),
					"setblock %d %d %d minecraft:composter".formatted(x + 3, y, z - 3),
					"setblock %d %d %d minecraft:smithing_table".formatted(x - 3, y, z + 3),
					"setblock %d %d %d minecraft:anvil".formatted(x + 3, y, z + 3),
			};
			for (String command : commands) {
				world.getServer().runCommand(command);
			}
			for (int i = 0; i < 4; i++) {
				world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:villager %d %d %d {NoAI:1b}", x - 2 + i, y, z + (i % 2 == 0 ? -1 : 1)));
				world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:cow %d %d %d {NoAI:1b}", x + 13 + 2 * i, y, z + (i % 2 == 0 ? -2 : 2)));
			}
			world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:chicken %d %d %d {NoAI:1b}", x + 15, y, z));
			context.runOnClient(mc -> setUpProfile(new Point(x + 0.5, y + 2.5, z + 0.5), new Point(x + 16.5, y + 2.5, z + 0.5)));

			// 1. The world: both zones outlined with their names, seen from outside.
			context.runOnClient(mc -> SoundSculptorSettings.setOutlineMode(OutlineMode.ALWAYS));
			context.runOnClient(mc -> setHudHidden(mc, true));
			// Spectator mode, so the player stays up in the air for the view.
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", x + 8, y + 8, z + 19));
			context.waitTicks(10);
			context.getInput().lookAt(new BlockPos(x + 8, y + 1, z));
			context.waitTicks(10);
			shot(context, out, "1-zones-in-the-world");
			world.getServer().runCommand("gamemode creative @a");

			// 2. The Sounds tab, standing at the edge of the trading hall (looking out at the sky, a calm background), after a
			// minute of the sounds there.
			world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 -25", x, y, z + 4));
			context.waitTicks(10);
			context.runOnClient(mc -> {
				RecentLog.get().sounds().clear();
				SoundsPanel.resetRememberedState();
			});
			playScene(context);
			context.setScreen(SoundSculptorScreen::new);
			context.waitForScreen(SoundSculptorScreen.class);
			context.runOnClient(mc -> ((SoundSculptorScreen) mc.gui.screen()).selectTab(SoundSculptorScreen.TAB_SOUNDS));
			context.runOnClient(ModPageScreenshotsClientGameTest::hideDevButton);
			context.waitTicks(8);
			shot(context, out, "2-sounds-tab");

			// 3. The Sound Sets library.
			context.runOnClient(mc -> ((SoundSculptorScreen) mc.gui.screen()).selectTab(SoundSculptorScreen.TAB_SETS));
			context.runOnClient(ModPageScreenshotsClientGameTest::hideDevButton);
			context.waitTicks(4);
			shot(context, out, "3-sound-sets");

			// 4. Zones, with Everywhere first.
			context.runOnClient(mc -> ((SoundSculptorScreen) mc.gui.screen()).selectTab(SoundSculptorScreen.TAB_ZONES));
			context.runOnClient(ModPageScreenshotsClientGameTest::hideDevButton);
			context.waitTicks(4);
			shot(context, out, "4-zones-tab");

			// 5. A set in the editor.
			context.runOnClient(mc -> mc.gui.setScreen(SetEditorScreen.edit(new SoundSculptorScreen(), HALL.id())));
			context.waitForScreen(SetEditorScreen.class);
			context.waitTicks(4);
			shot(context, out, "5-set-editor");

			// 6. Add sounds…, with Like this… open on a villager sound.
			context.runOnClient(mc -> ((SetEditorScreen) mc.gui.screen()).addSounds());
			context.waitForScreen(SoundPickerScreen.class);
			context.runOnClient(mc -> {
				SoundsPanel sounds = ((SoundPickerScreen) mc.gui.screen()).sounds();
				sounds.setRecentOnly(false);
				sounds.searchField.setValue("villager");
			});
			context.waitTicks(6);
			shot(context, out, "6-add-sounds");
			context.runOnClient(mc -> ((SoundPickerScreen) mc.gui.screen()).sounds().openLikeThis(VILLAGER));
			context.waitTicks(4);
			shot(context, out, "7-like-this");

			// 8. Why? for a villager sound: which zone and set decided it.
			context.setScreen(() -> null);
			context.waitTicks(2);
			playScene(context);
			context.runOnClient(mc -> mc.gui.setScreen(new TraceScreen(new SoundSculptorScreen(), VILLAGER)));
			context.waitForScreen(TraceScreen.class);
			context.waitTicks(4);
			shot(context, out, "8-why");

			// 9. The zone editor, with the outline of the zone being edited behind it.
			context.runOnClient(mc -> mc.gui.setScreen(ZoneEditorScreen.edit(new SoundSculptorScreen(), ProfileManager.profile().zone("hall"))));
			context.waitForScreen(ZoneEditorScreen.class);
			context.waitTicks(4);
			shot(context, out, "9-zone-editor");
		} finally {
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				setHudHidden(mc, false);
				SoundSculptorSettings.setOutlineMode(originalOutlines);
				ProfileManager.change(ws -> new Workspace(ws.profile(),
						ws.library().remove(HALL.id()).remove(FARM.id()).remove(WEATHER.id()).remove(MOBS.id())));
				ProfileManager.flushSaves();
				mc.options.guiScale().set(originalScale);
				mc.resizeGui();
			});
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(2);
		}
	}

	/** The main screen's "UI kit demo" button exists only in the development environment, which game tests run in. */
	private static void hideDevButton(Minecraft mc) {
		for (var child : mc.gui.screen().children()) {
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
					&& widget.getMessage().getString().equals("UI kit demo")) {
				widget.visible = false;
			}
		}
	}

	/** F1: the hand, hotbar and crosshair hidden, so only the world and the screen show. */
	private static void setHudHidden(Minecraft mc, boolean hidden) {
		if (mc.gui.hud.isHidden() != hidden) {
			mc.gui.hud.toggle();
		}
	}

	private static String fence(int x1, int y, int z1, int x2, int z2) {
		// One straight side of a pen (a fill of one block high fills the whole area, so each side is its own line).
		return String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:oak_fence", x1, y, z1, x2, y, z2);
	}

	private static void setUpProfile(Point hall, Point field) {
		String key = ProfileManager.key();
		String overworld = "minecraft:overworld";
		Zone hallZone = new Zone("hall", "Trading hall", true, key, overworld, hall, new Shape.Box(11, 5, 11),
				PlaceSets.EMPTY.attach(HALL.id()).withQuickEntries(List.of(SetEntry.of("q1", "minecraft:entity.villager.trade").withMute(true))));
		Zone fieldZone = new Zone("field", "Cow field", true, key, overworld, field, new Shape.Box(11, 5, 11), PlaceSets.EMPTY.attach(FARM.id()));
		ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
				.withEverywhere(PlaceSets.EMPTY.attach(WEATHER.id()).attach(MOBS.id()))
				.withZones(List.of(hallZone, fieldZone))
				.withZoneColor("hall", 0xE69F00)
				.withZoneColor("field", 0x56B4E9),
				ws.library().put(HALL).put(FARM).put(WEATHER).put(MOBS)));
	}

	/** The sounds of a minute in the trading hall, some near and some farther off, a few of them more than once. */
	private static void playScene(ClientGameTestContext context) {
		String[][] plays = {
				{"minecraft:entity.cow.ambient", "14"}, {"minecraft:entity.chicken.ambient", "15"},
				{"minecraft:entity.villager.work_librarian", "3"}, {"minecraft:entity.zombie.ambient", "24"},
				{"minecraft:block.anvil.use", "4"}, {"minecraft:entity.villager.trade", "2"},
				{"minecraft:entity.pig.ambient", "18"}, {VILLAGER, "2"}, {"minecraft:entity.cow.ambient", "13"},
				{"minecraft:block.chest.open", "1"}, {VILLAGER, "3"}, {"minecraft:entity.villager.yes", "2"},
				{"minecraft:entity.skeleton.ambient", "26"}, {VILLAGER, "1"},
		};
		for (String[] play : plays) {
			context.runOnClient(mc -> playAt(mc, play[0], Double.parseDouble(play[1])));
			context.waitTicks(3);
		}
	}

	private static void playAt(Minecraft mc, String id, double blocksEast) {
		Vec3 listener = mc.getSoundManager().getListenerTransform().position();
		SoundSource source = id.startsWith("minecraft:block.") ? SoundSource.BLOCKS
				: id.contains("zombie") || id.contains("skeleton") ? SoundSource.HOSTILE : SoundSource.NEUTRAL;
		mc.getSoundManager().play(new SimpleSoundInstance(Identifier.parse(id), source, 1.0F, 1.0F, RandomSource.create(),
				false, 0, SoundInstance.Attenuation.LINEAR, listener.x() + blocksEast, listener.y(), listener.z() + 1, false));
	}

	private static void shot(ClientGameTestContext context, Path out, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
	}
}
