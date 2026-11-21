package soundsculptor.gametest;

import soundsculptor.core.outline.OutlineMode;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.CueOverlay;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundSculptorSettings;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.ZoneOutlines;
import soundsculptor.mc.ui.SculptList;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility smoke test (PRD 8.1, 11.5) with Sodium, Iris and Sound Physics Remastered installed. It runs only with
 * {@code ./gradlew runClientGameTest -Pcompat=true}, which installs the three mods and runs this test alone; in the
 * normal suite it prints {@code COMPAT skipped} and returns.
 *
 * <p>Like the release-day smoke test (PRD 10.5): the game starts, the Sound Sculptor screen opens, a sound is muted from
 * its Sounds tab row, one zone outline is drawn and seen in a screenshot, and the muted sound shows a cue. The log must
 * have no errors from Sound Sculptor. Iris runs without a shader pack: packs need a real GPU, so "Iris with a shader pack"
 * stays a manual check (docs/RELEASING.md).
 */
public final class CompatSmokeClientGameTest implements FabricClientGameTest {
	private static final List<String> MODS = List.of("sodium", "iris", "sound_physics_remastered");
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	/** A zone color found nowhere else in the flat test world: pure magenta. */
	private static final int COLOR = 0xFF00FF;
	private static final int FRAMES = 5;

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> loaded = MODS.stream().filter(FabricLoader.getInstance()::isModLoaded).toList();
		if (loaded.isEmpty()) {
			System.out.println("COMPAT skipped: run ./gradlew runClientGameTest -Pcompat=true to test with Sodium, Iris and Sound Physics Remastered");
			return;
		}
		System.out.println("COMPAT mods: " + MODS.stream().map(CompatSmokeClientGameTest::version).collect(Collectors.joining(", ")));
		check(loaded.size() == MODS.size(), "-Pcompat=true installs all three mods, found only " + loaded);
		System.out.println("COMPAT Iris shader pack in use: " + shaderPackInUse() + " (packs are checked by hand, see docs/RELEASING.md)");
		System.out.println("COMPAT renderer: " + context.computeOnClient(mc -> String.valueOf(com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo())));

		OutlineMode modeBefore = context.computeOnClient(mc -> SoundSculptorSettings.outlineMode());
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
			context.waitFor(mc -> mc.player != null && mc.player.position().distanceToSqr(0.5, -60, 0.5) < 0.01, 200);
			context.runOnClient(mc -> {
				// Background music would show in the Sounds list; it is not what this test looks at.
				mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
				RecentLog.get().sounds().clear();
				CueOverlay.clear();
			});
			try {
				// The screen opens; mute the villager from its row on the Sounds tab.
				check(play(context), "an unmuted villager sound starts");
				context.setScreen(SoundSculptorScreen::new);
				context.waitForScreen(SoundSculptorScreen.class);
				context.runOnClient(mc -> {
					SoundsPanel.resetRememberedState();
					main(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS);
					main(mc).sounds().searchField.setValue(VILLAGER);
				});
				context.waitTicks(SoundsPanel.REFRESH_TICKS + 1);
				context.takeScreenshot("compat-sounds-tab");
				context.runOnClient(mc -> {
					SculptList.Row row = main(mc).sounds().list.findRow(VILLAGER);
					check(row != null, "the villager is listed on the Sounds tab");
					row.buttons().get(SoundsPanel.BUTTON_MUTE).onPress(null);
				});
				context.waitTicks(2);
				check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)),
						"Mute on the row mutes the villager");
				context.setScreen(() -> null);
				context.waitTicks(2);

				// The muted villager does not start, and its cue shows (a new mute gets Cue on).
				context.runOnClient(mc -> CueOverlay.clear());
				check(!play(context), "the muted villager does not start");
				context.waitTicks(FRAMES);
				List<CueOverlay.Shown> cues = context.computeOnClient(mc -> CueOverlay.shown());
				check(cues.size() == 1 && cues.get(0).text().equals("Villager mumbles"), "one cue for the muted villager, was " + cues);
				System.out.println("COMPAT cue: " + cues.get(0).text());

				// One zone outline, 8 blocks ahead, drawn in magenta and found in the screenshot.
				Vec3 center = context.computeOnClient(mc -> mc.player.getEyePosition().add(mc.player.getLookAngle().scale(9)));
				String key = context.computeOnClient(mc -> ProfileManager.key());
				Zone zone = new Zone("compat", "Pen", true, key, "minecraft:overworld", new Point(center.x, center.y, center.z),
						new Shape.Box(6, 6, 6), PlaceSets.EMPTY);
				context.runOnClient(mc -> {
					ProfileManager.update(p -> p.withZones(List.of(zone)).withZoneColor(zone.id(), COLOR));
					SoundSculptorSettings.setOutlineMode(OutlineMode.ALWAYS);
					CueOverlay.clear();
				});
				context.waitTicks(FRAMES);
				int lines = context.computeOnClient(mc -> ZoneOutlines.linesLastFrame());
				check(lines == 12, "the zone's 12 edges are drawn, was " + lines);
				Path shot = context.takeScreenshot("compat-outline");
				int magenta = magentaPixels(shot);
				System.out.println("COMPAT outline: " + lines + " edges, " + magenta + " magenta pixels in " + shot.getFileName());
				check(magenta >= 50, "the outline is visible in the screenshot, found " + magenta + " magenta pixels");

				// No errors from Sound Sculptor.
				check(context.computeOnClient(mc -> SoundHook.errorCount()) == 0, "Sound Sculptor handled every sound without an error");
				List<String> errors = logErrors();
				check(errors.isEmpty(), "the log has errors from Sound Sculptor:\n" + String.join("\n", errors));
				System.out.println("COMPAT pass: screen, mute, cue and outline work, and the log has no errors from Sound Sculptor");
			} finally {
				context.runOnClient(mc -> {
					SoundSculptorSettings.setOutlineMode(modeBefore);
					ProfileManager.update(p -> Profile.empty(p.name()));
					ProfileManager.flushSaves();
					CueOverlay.clear();
				});
			}
		}
	}

	/** Plays a villager sound 3 blocks east of the player; true if it started. */
	private static boolean play(ClientGameTestContext context) {
		boolean started = context.computeOnClient(mc -> {
			Vec3 at = mc.player.position();
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(VILLAGER), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, at.x + 3, at.y + 1, at.z, false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound);
		});
		context.waitTicks(3);
		return started;
	}

	private static String version(String modId) {
		return FabricLoader.getInstance().getModContainer(modId).map(m -> modId + " " + m.getMetadata().getVersion().getFriendlyString())
				.orElse(modId + " absent");
	}

	/** Whether Iris has a shader pack on, through its public API (reflection: Iris is not on the compile classpath). */
	private static String shaderPackInUse() {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			return String.valueOf(api.getMethod("isShaderPackInUse").invoke(instance));
		} catch (ReflectiveOperationException | LinkageError e) {
			return "unknown (" + e + ")";
		}
	}

	/** Pixels close to the zone's magenta in the screenshot. */
	private static int magentaPixels(Path png) {
		try {
			BufferedImage image = ImageIO.read(png.toFile());
			int count = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					int r = (rgb >> 16) & 0xFF;
					int g = (rgb >> 8) & 0xFF;
					int b = rgb & 0xFF;
					if (r > 180 && b > 180 && g < 90) {
						count++;
					}
				}
			}
			return count;
		} catch (IOException e) {
			throw new AssertionError("could not read the screenshot " + png, e);
		}
	}

	/**
	 * ERROR entries in this run's log that come from Sound Sculptor: the ERROR line names it, or one of the lines after it
	 * (its exception and stack frames, up to the next log line) has a {@code soundsculptor.} frame. A mod logging an error
	 * that Sound Sculptor's code threw is caught this way too.
	 */
	private static List<String> logErrors() {
		Path log = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
		List<String> lines;
		try {
			lines = Files.readAllLines(log, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError("could not read the log " + log, e);
		}
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!line.contains("/ERROR]")) {
				continue;
			}
			String frame = null;
			// The entry's own lines run until the next log line, which starts with its "[hh:mm:ss]" time.
			for (int j = i + 1; j < lines.size() && !lines.get(j).startsWith("["); j++) {
				if (lines.get(j).contains("soundsculptor.")) {
					frame = lines.get(j).trim();
					break;
				}
			}
			if (line.contains("soundsculptor") || line.contains("Sound Sculptor")) {
				errors.add(line);
			} else if (frame != null) {
				errors.add(line + "\n    " + frame);
			}
		}
		return errors;
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
