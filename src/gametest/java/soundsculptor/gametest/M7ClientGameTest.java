package soundsculptor.gametest;

import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.profile.WorldIds;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.PlayingChannels;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.WorldSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Automated version of the M7 engine tests (PRD 11.1): a profile per world (FR-11.1), box zones with hard
 * edges that follow where the player stands (FR-8 AC), precedence cases P1 to P5, P17 and P18 (FR-9, section 11.4),
 * playing loops following the player and edits (FR-8.8), Bypass (FR-13.1), "Zone: name" in the recently played list and
 * zones in the sound trace.
 */
public final class M7ClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String TRADER = "minecraft:entity.wandering_trader.ambient";
	private static final String LOOP = "minecraft:block.beacon.ambient";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final int SETTLE = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();

			// FR-11.1: a singleplayer world gets world:<id>, kept in soundsculptor_id.txt in its save folder.
			String key = context.computeOnClient(mc -> ProfileManager.key());
			check(key != null && key.startsWith("world:"), "a singleplayer world should have a world:<id> key, was " + key);
			Path root = context.computeOnClient(mc -> mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
			check(Files.exists(root.resolve(WorldIds.FILE_NAME)), "soundsculptor_id.txt should be written into the save folder");
			check(WorldSession.singleplayerKey(root).key().equals(key), "joining again should read the same ID");
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
				RecentLog.get().sounds().clear();
			});
			Vec3 origin = context.computeOnClient(mc -> mc.getSoundManager().getListenerTransform().position());
			server = world;
			feetY = context.computeOnClient(mc -> mc.player.position().y);

			// FR-8 AC 1: a 20-block box muting *villager*: standing in it a villager is silent; 15 blocks away it is not.
			Point pen = new Point(origin.x + 6, origin.y, origin.z);
			setZones(context, List.of(), zone("pen", "Pen", key, OVERWORLD, pen, new Shape.Box(20, 20, 20), SetEntry.of("z1", "*villager*").withMute(true)));
			check(playAt(context, VILLAGER, pen.x() + 2, pen.z()) == 0.0F, "a villager while you stand in the zone should be muted");
			check(lastStatus(context, VILLAGER) == PlayStatus.MUTED, "the row should say Muted");
			check("Pen".equals(lastZone(context, VILLAGER)), "the row should say which zone decided, was " + lastZone(context, VILLAGER));
			// Zones are part of the trace of that play (FR-14.2).
			String trace = context.computeOnClient(mc -> new TraceScreen(null, VILLAGER).text());
			check(trace.contains("You were in: Pen \u2192 Everywhere\n  Pen (Box 20 × 20 × 20)\n") && trace.contains("Deciding zones\n"),
					"the trace should list the zone:\n" + trace);
			check(playAt(context, VILLAGER, pen.x() - 15, pen.z()) == 1.0F, "standing 15 blocks from the anchor, the villager plays");
			check(playAt(context, TRADER, pen.x(), pen.z()) == 1.0F, "the trader does not match *villager*");
			// P17 and P18: only where you stand counts, not where the sound is.
			standAt(context, pen.x() + 8, pen.z());
			check(play(context, VILLAGER, pen.x() + 20, origin.y, pen.z()) == 0.0F, "P17: a villager outside the zone you stand in is muted");
			standAt(context, pen.x() - 15, pen.z());
			check(play(context, VILLAGER, pen.x(), origin.y, pen.z()) == 1.0F, "P18: a villager in a zone you are not in plays");

			// FR-8 AC 2 and 3: a zone made for another dimension or another world has no effect here.
			setZones(context, List.of(), zone("nether", "Nether", key, "minecraft:the_nether", pen, new Shape.Box(20, 20, 20), SetEntry.of("z1", "*villager*").withMute(true)));
			check(playAt(context, VILLAGER, pen.x(), pen.z()) == 1.0F, "a Nether zone must not apply in the Overworld");
			setZones(context, List.of(), zone("other", "Other", "play.example.net", OVERWORLD, pen, new Shape.Box(20, 20, 20), SetEntry.of("z1", "*villager*").withMute(true)));
			check(playAt(context, VILLAGER, pen.x(), pen.z()) == 1.0F, "another server's zone must not apply here");

			// Box zones: half the size in each direction from the anchor (FR-8.3).
			setZones(context, List.of(), zone("box", "Box", key, OVERWORLD, pen, new Shape.Box(4, 4, 20), SetEntry.of("z1", "*villager*").withVolume(30)));
			check(playAt(context, VILLAGER, pen.x(), pen.z() + 9) == 0.3F, "standing inside the box's long side the zone applies");
			check(playAt(context, VILLAGER, pen.x() + 3, pen.z()) == 1.0F, "outside the box's short side it does not");

			// P1: Everywhere mutes villagers, zone A sets them to 50%: 50% while you stand in A; muted (not started) outside.
			setZones(context, List.of(SetEntry.of("g1", "*villager*").withMute(true)),
					zone("a", "A", key, OVERWORLD, pen, new Shape.Box(20, 20, 20), SetEntry.of("z1", "*villager*").withVolume(50)));
			check(playAt(context, VILLAGER, pen.x(), pen.z()) == 0.5F, "P1 in A: 50%");
			check(playAt(context, VILLAGER, pen.x() + 30, pen.z()) == 0.0F, "P1 outside A: muted, not started");
			// P2: Everywhere mutes, no zone.
			setZones(context, List.of(SetEntry.of("g1", "*villager*").withMute(true)));
			check(playAt(context, VILLAGER, pen.x(), pen.z()) == 0.0F, "P2: muted");

			// P3: standing in A (20 blocks across) inside B (40 across), the smaller A wins, whatever the list order.
			setZones(context, List.of(),
					zone("b", "B", key, OVERWORLD, new Point(pen.x() - 9, pen.y(), pen.z()), new Shape.Box(40, 40, 40), SetEntry.of("z1", "*villager*").withVolume(80)),
					zone("a", "A", key, OVERWORLD, new Point(pen.x() + 3, pen.y(), pen.z()), new Shape.Box(20, 20, 20), SetEntry.of("z1", "*villager*").withVolume(20)));
			check(playAt(context, VILLAGER, pen.x(), pen.z()) == 0.2F, "P3: the smaller zone A wins");
			check("A".equals(lastZone(context, VILLAGER)), "P3: the row should name zone A");
			// P5: two zones of the same size: the one higher in the list wins.
			setZones(context, List.of(),
					zone("b", "B", key, OVERWORLD, new Point(pen.x() - 5, pen.y(), pen.z()), new Shape.Box(24, 24, 24), SetEntry.of("z1", "*villager*").withVolume(80)),
					zone("a", "A", key, OVERWORLD, new Point(pen.x() + 5, pen.y(), pen.z()), new Shape.Box(24, 24, 24), SetEntry.of("z1", "*villager*").withVolume(20)));
			check(playAt(context, VILLAGER, pen.x(), pen.z()) == 0.8F, "P5: the same size: B, higher in the list, wins");

			// FR-8.8: a playing loop follows you. With a zone muting it, it goes silent when you walk in, without
			// stopping, and comes back when you walk out.
			setZones(context, List.of(), zone("quiet", "Quiet", key, OVERWORLD, pen, new Shape.Box(10, 10, 10), SetEntry.of("z1", "*beacon*").withMute(true)));
			standAt(context, pen.x() - 12, pen.z());
			SoundInstance loop = startLoop(context, pen);
			float before = context.computeOnClient(mc -> PlayingChannels.get().gain(loop));
			check(before > 0.01F, "outside the zone the loop should be tracked at its vanilla gain, was " + before);
			standAt(context, pen.x(), pen.z());
			check(context.computeOnClient(mc -> PlayingChannels.get().gain(loop)) == 0.0F, "walking into the zone should silence it");
			check(context.computeOnClient(mc -> mc.getSoundManager().isActive(loop)), "muting by a zone must not stop the sound");
			standAt(context, pen.x() - 12, pen.z());
			check(Math.abs(context.computeOnClient(mc -> PlayingChannels.get().gain(loop)) - before) < 1e-4, "walking out should restore it");
			// An edit applies to a playing loop at once (P12): mute it on Everywhere while you stand outside the zone.
			setZones(context, List.of(SetEntry.of("g1", "*beacon*").withMute(true)),
					zone("quiet", "Quiet", key, OVERWORLD, pen, new Shape.Box(10, 10, 10), SetEntry.of("z1", "*beacon*").withMute(true)));
			context.waitTicks(SETTLE);
			check(context.computeOnClient(mc -> PlayingChannels.get().gain(loop)) == 0.0F, "muting it on Everywhere should silence it at once");
			context.runOnClient(mc -> mc.getSoundManager().stop(loop));
			// Vanilla releases a stopped channel some ticks later; tracking ends together with vanilla's.
			for (int i = 0; i < 60 && context.computeOnClient(mc -> mc.getSoundManager().isActive(loop)); i++) {
				context.waitTicks(1);
			}
			check(!context.computeOnClient(mc -> PlayingChannels.get().isTracked(loop)), "a stopped sound should no longer be tracked");

			// Bypass returns playing sounds to vanilla too.
			setZones(context, List.of(), zone("quiet", "Quiet", key, OVERWORLD, pen, new Shape.Box(10, 10, 10), SetEntry.of("z1", "*beacon*").withVolume(10)));
			standAt(context, pen.x(), pen.z());
			SoundInstance loop2 = startLoop(context, pen);
			float quiet = context.computeOnClient(mc -> PlayingChannels.get().gain(loop2));
			context.runOnClient(mc -> ProfileManager.setBypass(true));
			context.waitTicks(SETTLE);
			float bypassed = context.computeOnClient(mc -> PlayingChannels.get().gain(loop2));
			check(bypassed > quiet * 5, "Bypass should bring the playing sound back to vanilla: " + quiet + " -> " + bypassed);
			context.runOnClient(mc -> ProfileManager.setBypass(false));
			context.waitTicks(SETTLE);
			check(Math.abs(context.computeOnClient(mc -> PlayingChannels.get().gain(loop2)) - quiet) < 1e-4,
					"turning Bypass off should put the loop back at 10% at once");
			context.runOnClient(mc -> mc.getSoundManager().stop(loop2));
			standAt(context, origin.x, origin.z);

			// The profile is saved under this world's key (FR-11.8).
			context.runOnClient(mc -> ProfileManager.flushSaves());
			Path file = context.computeOnClient(mc -> ProfileManager.store().fileFor(key));
			check(Files.exists(file), "the profile should be saved as " + file);

			// Clean up for later tests.
			context.runOnClient(mc -> {
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
			});
		}
		check(context.computeOnClient(mc -> !ProfileManager.isLoaded()), "leaving the world should unload its profile");
	}

	private static Zone zone(String id, String name, String key, String dimension, Point anchor, Shape shape, SetEntry rule) {
		return new Zone(id, name, true, key, dimension, anchor, shape, PlaceSets.EMPTY.withQuickEntries(List.of(rule)));
	}

	private static void setZones(ClientGameTestContext context, List<SetEntry> globalRules, Zone... zones) {
		context.runOnClient(mc -> ProfileManager.update(p -> p.withEverywhere(PlaceSets.EMPTY.withQuickEntries(globalRules)).withZones(List.of(zones))));
	}

	/** The world, for moving the player. */
	private static TestSingleplayerContext server;
	/** The height of the player's feet on the test world's floor. */
	private static double feetY;

	/** Moves the player's feet to {@code (x, floor, z)} (FR-8.4: zones follow where you stand). */
	private static void standAt(ClientGameTestContext context, double x, double z) {
		server.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f", x, feetY, z));
		context.waitTicks(SETTLE);
	}

	/** Stands at {@code (x, z)} and plays the sound right there; returns the start factor (0 when it did not start). */
	private static float playAt(ClientGameTestContext context, String id, double x, double z) {
		standAt(context, x, z);
		return context.computeOnClient(mc -> {
			Vec3 at = mc.player.position();
			return startFactor(mc, id, at.x, at.y + 1, at.z);
		}) + settle(context);
	}

	/** Plays a villager-style sound at a position, wherever the player is; returns the start factor. */
	private static float play(ClientGameTestContext context, String id, double x, double y, double z) {
		return context.computeOnClient(mc -> startFactor(mc, id, x, y, z)) + settle(context);
	}

	private static float startFactor(net.minecraft.client.Minecraft mc, String id, double x, double y, double z) {
		SoundInstance sound = new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
				false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false);
		mc.getSoundManager().play(sound);
		return mc.getSoundManager().isActive(sound) ? SoundHook.lastStartFactor() : 0.0F;
	}

	/** Waits for the sound to settle; adds nothing to the factor. */
	private static float settle(ClientGameTestContext context) {
		context.waitTicks(SETTLE);
		return 0F;
	}

	/** Starts the looping beacon sound at {@code at} and lets it settle. */
	private static SoundInstance startLoop(ClientGameTestContext context, Point at) {
		SoundInstance loop = context.computeOnClient(mc -> {
			SoundInstance instance = new SimpleSoundInstance(Identifier.parse(LOOP), SoundSource.BLOCKS, 1.0F, 1.0F, RandomSource.create(),
					true, 0, SoundInstance.Attenuation.LINEAR, at.x(), at.y(), at.z(), false);
			mc.getSoundManager().play(instance);
			return instance;
		});
		context.waitTicks(SETTLE);
		return loop;
	}

	private static RecentRow row(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> RecentLog.get().sounds().snapshot(Util.getMillis(), SortOrder.MOST_RECENT, s -> null)
				.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null));
	}

	private static PlayStatus lastStatus(ClientGameTestContext context, String id) {
		RecentRow row = row(context, id);
		return row == null ? null : row.status();
	}

	private static String lastZone(ClientGameTestContext context, String id) {
		RecentRow row = row(context, id);
		return row == null ? null : row.zoneName();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
