package soundsculptor.gametest;

import soundsculptor.core.boost.BoostSupport;
import soundsculptor.core.diag.DiagnosticsReport;
import soundsculptor.core.limit.RepeatLimiter;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.sets.Limit;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryWinReason;
import soundsculptor.core.trace.EntryWinner;
import soundsculptor.core.trace.SoundTrace;
import soundsculptor.core.trace.TraceNotice;
import soundsculptor.core.trace.TraceProperty;
import soundsculptor.core.trace.ZoneLine;
import soundsculptor.core.trace.ZoneWinReason;
import soundsculptor.core.trace.ZoneWinner;
import soundsculptor.core.zones.Point;
import soundsculptor.core.zones.Shape;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.CueOverlay;
import soundsculptor.mc.Diagnostics;
import soundsculptor.mc.PlayingChannels;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SoundObserver;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.WorldSession;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The full zone precedence matrix (PRD 11.4) in one run, for M16 (PRD 11.1). Every live case is built in a test world
 * and checked the same way: the level the sound starts at (or that it did not start), its status (Muted, Limited) and
 * the zone named for it, the repeat limit, and the trace's deciding place, set and entry for each property (FR-14). Each
 * case prints one {@code MATRIX Pn pass} or {@code MATRIX Pn fail: why} line, then a summary; any failure fails the test.
 *
 * <p>Sounds are started by the test with sound instances it controls, at fixed spots around the player, as the game
 * would play them (entity-bound eating, a vanilla button click, music and jukebox instances from vanilla's own
 * factories). So no case waits for a random mob sound or for the background music. The matrix uses villagers instead
 * of a cow or villager in a boat: where the source is never matters except in P17 and P18, which place it on purpose.
 *
 * <p>Some cases have deeper tests elsewhere. This class checks every case compactly; the other tests add the UI paths
 * and the long runs:
 * <table>
 *   <caption>Where each case is also tested</caption>
 *   <tr><th>Case</th><th>Here</th><th>Also</th></tr>
 *   <tr><td>P1, P2, P3, P5</td><td>levels, zone named, trace</td><td>{@code M7ClientGameTest} (Quick changes form)</td></tr>
 *   <tr><td>P4, P4b</td><td>5 plays at once; 30 plays over 3 s, and the Diagnostics readout</td><td>{@code M11LimitsClientGameTest}</td></tr>
 *   <tr><td>P5b</td><td>list order swapped</td><td>-</td></tr>
 *   <tr><td>P8</td><td>150% with A's cue, no cue shown</td><td>{@code M10BoostClientGameTest}</td></tr>
 *   <tr><td>P10</td><td>the player really in the Nether</td><td>{@code M7ClientGameTest} (a Nether zone seen from the Overworld)</td></tr>
 *   <tr><td>P11</td><td>music, a vanilla button click, eating</td><td>{@code M5ClientGameTest} (UI click)</td></tr>
 *   <tr><td>P12</td><td>the tick the feet enter and leave A</td><td>{@code M7ClientGameTest}, {@code M11LimitsClientGameTest} (loops)</td></tr>
 *   <tr><td>P13</td><td>sets swapped in data</td><td>{@code PrecedenceSetsClientGameTest}, {@code PlaceSetsClientGameTest} (Move up in the UI)</td></tr>
 *   <tr><td>P14, P17, P18</td><td>levels, trace</td><td>{@code PrecedenceSetsClientGameTest} (a cow in a boat)</td></tr>
 *   <tr><td>P15</td><td>5 plays at once</td><td>-</td></tr>
 *   <tr><td>P16</td><td>set edited in data, both zones</td><td>{@code PrecedenceSetsClientGameTest} (edited from B's set editor)</td></tr>
 *   <tr><td>P19, P20</td><td>a record and a music track</td><td>-</td></tr>
 * </table>
 */
public final class PrecedenceMatrixClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String VILLAGER_YES = "minecraft:entity.villager.yes";
	private static final String VILLAGERS = "minecraft:entity.villager.*";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String COWS = "minecraft:entity.cow.*";
	private static final String MINECART = "minecraft:entity.minecart.riding";
	private static final String MUSIC = "minecraft:music.game";
	private static final String RECORD = "minecraft:music_disc.cat";
	private static final String RECORD_NEXT = "minecraft:music_disc.blocks";
	private static final String CLICK = "minecraft:ui.button.click";
	private static final String EAT = "minecraft:entity.generic.eat";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	/** Where the player stands unless a case moves them: the floor of the flat test world. */
	private static final double HOME_X = 0.5;
	private static final double HOME_Y = -60;
	private static final double HOME_Z = 0.5;
	private static final int SETTLE = 3;
	/** The start factor of a muted loop: it starts, but silently (FR-8.8). */
	private static final float SILENT = 1.0E-5F;
	private static final Limit TWO_PER_SECOND = new Limit(2, 1000, false);

	/** What the sound hook last did with each sound ID: status and the zone named for it. */
	private static final Map<String, Seen> SEEN = new HashMap<>();
	private static final SoundObserver OBSERVER = o -> SEEN.put(o.idString(), new Seen(o.status(), o.zoneName()));

	/** Runs at the end of each client tick, after Sound Sculptor's own tick work (registered later); {@code null} when idle. */
	private static Consumer<Minecraft> probe;
	private static boolean probeRegistered;

	private static TestSingleplayerContext world;
	private static String key;
	private static final List<String> FAILED = new ArrayList<>();

	private record Seen(PlayStatus status, String zone) {
	}

	/** One sound start: whether it started, its start factor (0 if not started), what the hook said, and its trace. */
	private record Heard(String id, SoundInstance instance, boolean started, float factor, PlayStatus status, String zone,
			SoundTrace trace) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		FAILED.clear();
		if (!probeRegistered) {
			ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				Consumer<Minecraft> p = probe;
				if (p != null) {
					p.accept(mc);
				}
			});
			probeRegistered = true;
		}
		double musicBefore = context.computeOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get());
		try (TestSingleplayerContext w = TestWorlds.create(context)) {
			world = w;
			w.getConnection().waitForChunksRender();
			key = context.computeOnClient(mc -> ProfileManager.key());
			standAt(context, 0, 0);
			Snapshot before = context.computeOnClient(mc -> new Snapshot(ProfileManager.profile(), ProfileManager.library()));
			context.runOnClient(mc -> {
				ProfileManager.setBypass(false);
				SoundHook.addObserver(OBSERVER);
			});
			try {
				run(context, "P1", () -> p1(context));
				run(context, "P2", () -> p2(context));
				run(context, "P3", () -> p3(context));
				run(context, "P4", () -> p4(context));
				run(context, "P4b", () -> p4b(context));
				run(context, "P5", () -> p5(context, false));
				run(context, "P5b", () -> p5(context, true));
				run(context, "P8", () -> p8(context));
				run(context, "P10", () -> p10(context));
				run(context, "P11", () -> p11(context));
				run(context, "P12", () -> p12(context));
				run(context, "P13", () -> p13(context));
				run(context, "P14", () -> p14(context));
				run(context, "P15", () -> p15(context));
				run(context, "P16", () -> p16(context));
				run(context, "P17", () -> p17(context));
				run(context, "P18", () -> p18(context));
				run(context, "P19", () -> p19(context));
				run(context, "P20", () -> p20(context));
			} finally {
				context.runOnClient(mc -> {
					SoundHook.removeObserver(OBSERVER);
					mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(musicBefore);
					ProfileManager.change(ws -> new Workspace(before.profile(), before.library()));
					ProfileManager.flushSaves();
				});
			}
		} finally {
			world = null;
		}
		System.out.println("MATRIX summary: " + (19 - FAILED.size()) + " of 19 cases pass" + (FAILED.isEmpty() ? "" : "; failed: " + FAILED));
		check(FAILED.isEmpty(), "zone precedence matrix cases failed: " + FAILED);
	}

	/** Runs one case, prints its MATRIX line, and puts everything back for the next case whatever happened. */
	private static void run(ClientGameTestContext context, String name, Runnable body) {
		try {
			body.run();
			System.out.println("MATRIX " + name + " pass");
		} catch (RuntimeException | AssertionError e) {
			FAILED.add(name);
			System.out.println("MATRIX " + name + " fail: " + e.getMessage());
			e.printStackTrace(System.out);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				probe = null;
				ProfileManager.setBypass(false);
				TestClock.fixedNow = null;
				SoundHook.limiter().clear();
				mc.getSoundManager().stop();
				CueOverlay.clear();
			});
			standAt(context, 0, 0);
		}
	}

	// ---- The cases, in the order of the 11.4 table ----

	/** P1: Everywhere mutes villagers; A sets them to 50%: 50% while you stand in A, muted when you step out. */
	private static void p1(ClientGameTestContext context) {
		SoundSet everywhere = set("p1-e", "Quiet village", SetEntry.of("m", VILLAGERS).withMute(true));
		SoundSet half = set("p1-a", "Half villagers", SetEntry.of("h", VILLAGERS).withVolume(50));
		setUp(context, attach(everywhere), List.of(everywhere, half), zone("p1-a", "A", 0, 0, box(8), attach(half)));
		Heard in = villager(context, VILLAGER, 3, 0);
		expect(in, 0.5F, PlayStatus.PLAYING, "A", null);
		expectDecided(in, TraceProperty.LOUDNESS, "A", "p1-a", "h");
		standAt(context, 20, 0);
		Heard out = villager(context, VILLAGER, 3, 0);
		expect(out, 0, PlayStatus.MUTED, null, null);
		expectDecided(out, TraceProperty.LOUDNESS, "Everywhere", "p1-e", "m");
	}

	/** P2: Everywhere mutes villagers, no zones: muted. */
	private static void p2(ClientGameTestContext context) {
		SoundSet everywhere = set("p2-e", "Quiet village", SetEntry.of("m", VILLAGERS).withMute(true));
		setUp(context, attach(everywhere), List.of(everywhere));
		Heard h = villager(context, VILLAGER, 3, 0);
		expect(h, 0, PlayStatus.MUTED, null, null);
		expectDecided(h, TraceProperty.LOUDNESS, "Everywhere", "p2-e", "m");
	}

	/** P3: A (20%, 10 x 5 x 10) inside B (80%, 40 x 10 x 40), B higher in the list: 20% in A, then 80% in B outside A. */
	private static void p3(ClientGameTestContext context) {
		SoundSet low = set("p3-a", "Room", SetEntry.of("v", VILLAGERS).withVolume(20));
		SoundSet high = set("p3-b", "Base", SetEntry.of("v", VILLAGERS).withVolume(80));
		setUp(context, PlaceSets.EMPTY, List.of(low, high),
				zone("p3-b", "B", 10, 0, new Shape.Box(40, 10, 40), attach(high)),
				zone("p3-a", "A", 0, 0, new Shape.Box(10, 5, 10), attach(low)));
		Heard inA = villager(context, VILLAGER, 3, 0);
		expect(inA, 0.2F, PlayStatus.PLAYING, "A", null);
		expectDecided(inA, TraceProperty.LOUDNESS, "A", "p3-a", "v");
		expectZoneReason(inA, TraceProperty.LOUDNESS, ZoneWinReason.SMALLEST);
		standAt(context, 15, 0);
		Heard inB = villager(context, VILLAGER, 3, 0);
		expect(inB, 0.8F, PlayStatus.PLAYING, "B", null);
		expectDecided(inB, TraceProperty.LOUDNESS, "B", "p3-b", "v");
		expectZoneReason(inB, TraceProperty.LOUDNESS, ZoneWinReason.ONLY_ZONE);
	}

	/** P4: A mutes villagers, B limits them to 2/s, you stand in both: muted (A) and limited (B). */
	private static void p4(ClientGameTestContext context) {
		SoundSet mute = set("p4-a", "Mute villagers", SetEntry.of("m", VILLAGERS).withMute(true));
		SoundSet limit = set("p4-b", "Villagers 2/s", SetEntry.of("l", VILLAGERS).withLimit(TWO_PER_SECOND));
		setUp(context, PlaceSets.EMPTY, List.of(mute, limit),
				zone("p4-a", "A", 0, 0, box(8), attach(mute)),
				zone("p4-b", "B", 0, 0, box(12), attach(limit)));
		List<Seen> burst = burst(context, VILLAGER, 5);
		check(burst.stream().map(Seen::status).toList().equals(List.of(PlayStatus.MUTED, PlayStatus.MUTED, PlayStatus.LIMITED,
				PlayStatus.LIMITED, PlayStatus.LIMITED)), "5 at once: 2 muted, then 3 Limited, was " + burst);
		check("A".equals(burst.get(0).zone()) && "B".equals(burst.get(4).zone()), "A names the muted plays, B the Limited ones: " + burst);
		SoundTrace t = trace(context, VILLAGER);
		check(TWO_PER_SECOND.equals(t.limit()) && t.volumePercent() == 0, "the trace says muted with 2 per 1 s, was " + t.volumePercent() + "%, " + t.limit());
		expectDecided(VILLAGER, t, TraceProperty.LOUDNESS, "A", "p4-a", "m");
		expectDecided(VILLAGER, t, TraceProperty.LIMIT, "B", "p4-b", "l");
	}

	/** P4b: A sets villagers to 40%, B limits them to 2/s; 10 villagers: at most 2 start in any second, each at 40%. */
	private static void p4b(ClientGameTestContext context) {
		SoundSet level = set("p4b-a", "Villagers 40%", SetEntry.of("v", VILLAGERS).withVolume(40));
		SoundSet limit = set("p4b-b", "Villagers 2/s", SetEntry.of("l", VILLAGERS).withLimit(TWO_PER_SECOND));
		setUp(context, PlaceSets.EMPTY, List.of(level, limit),
				zone("p4b-a", "A", 0, 0, box(8), attach(level)),
				zone("p4b-b", "B", 0, 0, box(12), attach(limit)));
		// 10 villagers in a pen, each once in 3 seconds, one every 100 ms. The fake clock (TestClock) makes the 3 seconds
		// take one tick, so a slow machine cannot stretch the window.
		List<Long> startedAt = new ArrayList<>();
		List<Float> factors = new ArrayList<>();
		context.runOnClient(mc -> {
			long base = Util.getMillis() + 1_000_000;
			Vec3 at = mc.player.position();
			for (int step = 0; step < 30; step++) {
				TestClock.fixedNow = base + step * 100L;
				int i = step % 10;
				SoundInstance sound = instance(VILLAGER, at.x + (i % 5) - 2, at.y, at.z + 2 + i / 5);
				mc.getSoundManager().play(sound);
				if (mc.getSoundManager().isActive(sound)) {
					startedAt.add(TestClock.fixedNow);
					factors.add(SoundHook.lastStartFactor());
				}
			}
		});
		check(startedAt.size() == 6, "2 per second for 3 s: 6 start, was " + startedAt.size());
		for (int i = 2; i < startedAt.size(); i++) {
			check(startedAt.get(i) - startedAt.get(i - 2) >= 1000, "3 started within 1 s: " + startedAt);
		}
		check(factors.stream().allMatch(f -> Math.abs(f - 0.4F) < 1e-6), "each at 40%: " + factors);
		// Diagnostics agrees: 6 played, 24 dropped, and at most 2 in any one window of B's limit (M11's readout).
		DiagnosticsReport.Data data = context.computeOnClient(mc -> Diagnostics.data());
		check(data.limits().allowed() == 6 && data.limits().dropped() == 24
				&& data.limits().fullest().equals(List.of(new RepeatLimiter.Fullest(2, TWO_PER_SECOND))),
				"Diagnostics: 6 played, 24 dropped, most in one window 2 of 2 per 1 s, was " + data.limits());
		String report = context.computeOnClient(mc -> Diagnostics.report());
		check(report.contains("Most in one window: 2 (limit 2 per 1 s)"), "the Diagnostics report shows at most 2 per second:\n" + report);
		SoundTrace t = trace(context, VILLAGER);
		check(TWO_PER_SECOND.equals(t.limit()) && Math.abs(t.volumePercent() - 40) < 0.01, "the trace says 40% with 2 per 1 s, was "
				+ t.volumePercent() + "%, " + t.limit());
		expectDecided(VILLAGER, t, TraceProperty.LOUDNESS, "A", "p4b-a", "v");
		expectDecided(VILLAGER, t, TraceProperty.LIMIT, "B", "p4b-b", "l");
	}

	/**
	 * P5: A (20%) and B (80%), the same size, overlapping, A higher in the zone list, you in both: 20%. P5b: the same with
	 * the list order swapped: 80%.
	 */
	private static void p5(ClientGameTestContext context, boolean swapped) {
		SoundSet low = set("p5-a", "A's villagers", SetEntry.of("v", VILLAGERS).withVolume(20));
		SoundSet high = set("p5-b", "B's villagers", SetEntry.of("v", VILLAGERS).withVolume(80));
		Zone a = zone("p5-a", "A", -2, 0, box(10), attach(low));
		Zone b = zone("p5-b", "B", 2, 0, box(10), attach(high));
		if (swapped) {
			setUp(context, PlaceSets.EMPTY, List.of(low, high), b, a);
		} else {
			setUp(context, PlaceSets.EMPTY, List.of(low, high), a, b);
		}
		Heard h = villager(context, VILLAGER, 3, 0);
		String winner = swapped ? "B" : "A";
		expect(h, swapped ? 0.8F : 0.2F, PlayStatus.PLAYING, winner, null);
		expectDecided(h, TraceProperty.LOUDNESS, winner, swapped ? "p5-b" : "p5-a", "v");
		expectZoneReason(h, TraceProperty.LOUDNESS, ZoneWinReason.SAME_SIZE_LIST_ORDER);
	}

	/** P8: Everywhere villagers 150%; A sets only Cue on: 150%, with A's cue setting, and no cue (not muted). */
	private static void p8(ClientGameTestContext context) {
		SoundSet loud = set("p8-e", "Loud villagers", SetEntry.of("v", VILLAGERS).withVolume(150));
		SoundSet cue = set("p8-a", "Villager cues", SetEntry.of("c", VILLAGERS).withCue(true));
		setUp(context, attach(loud), List.of(loud, cue), zone("p8-a", "A", 0, 0, box(10), attach(cue)));
		boolean boost = context.computeOnClient(mc -> BoostSupport.deviceAllows());
		Heard h = villager(context, VILLAGER, 3, 0);
		// On an audio device that cannot boost, 150% plays at 100% (M10); CI and Windows both boost.
		expect(h, boost ? 1.5F : 1.0F, boost ? PlayStatus.BOOSTED : PlayStatus.PLAYING, null, null);
		check(context.computeOnClient(mc -> CueOverlay.shown()).isEmpty(), "no cue: the sound is not muted");
		check(h.trace().cueOn(), "the trace has A's cue on");
		check(!boost || h.trace().notices().contains(TraceNotice.BOOSTED), "the trace says boost applied: " + h.trace().notices());
		expectDecided(h, TraceProperty.LOUDNESS, "Everywhere", "p8-e", "v");
		expectDecided(h, TraceProperty.CUE, "A", "p8-a", "c");
		if (!boost) {
			System.out.println("MATRIX P8 note: this audio device cannot boost, so 150% played at 100%");
		}
	}

	/** P10: a zone in the Overworld; the player at the same coordinates in the Nether: no effect there. */
	private static void p10(ClientGameTestContext context) {
		SoundSet mute = set("p10-a", "Mute villagers", SetEntry.of("m", VILLAGERS).withMute(true));
		// 256 blocks tall, so the Overworld floor (y -60) and a spot in the Nether (y 64) have the same x and z inside it.
		Zone a = new Zone("p10-a", "A", true, key, OVERWORLD, new Point(HOME_X, 0, HOME_Z), new Shape.Box(8, 256, 8), attach(mute));
		setUp(context, PlaceSets.EMPTY, List.of(mute), a);
		Heard overworld = villager(context, VILLAGER, 2, 0);
		expect(overworld, 0, PlayStatus.MUTED, "A", null);
		GameType mode = context.computeOnClient(mc -> mc.gameMode.getPlayerMode());
		try {
			// Spectator: no falling into lava or out of the world while the case runs.
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.1f 64 %.1f", NETHER, HOME_X, HOME_Z));
			context.waitFor(mc -> mc.level != null && mc.player != null && NETHER.equals(WorldSession.dimension(mc))
					&& mc.player.blockPosition().equals(BlockPos.containing(HOME_X, 64, HOME_Z)), 600);
			context.waitTicks(SETTLE);
			check(a.containsBlock((int) Math.floor(HOME_X), 64, (int) Math.floor(HOME_Z)), "the Nether spot has the zone's coordinates");
			Heard nether = villager(context, VILLAGER, 2, 0);
			expect(nether, 1.0F, PlayStatus.PLAYING, null, null);
			check(nether.trace().zones().isEmpty() && nether.trace().zoneWinners().isEmpty(), "the trace lists no zone in the Nether");
			expectDecided(nether, TraceProperty.LOUDNESS, null, null, null);
		} finally {
			world.getServer().runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.1f %.1f %.1f", OVERWORLD, HOME_X, HOME_Y, HOME_Z));
			context.waitFor(mc -> mc.level != null && OVERWORLD.equals(WorldSession.dimension(mc)), 600);
			world.getServer().runCommand("gamemode " + mode.getName() + " @a");
			world.getConnection().waitForChunksRender();
		}
	}

	/** P11: a zone mutes {@code *}; you stand in it; a music track starts, you click a button, you eat: all silent. */
	private static void p11(ClientGameTestContext context) {
		SoundSet all = set("p11-a", "Silence", SetEntry.of("all", "*").withMute(true));
		setUp(context, PlaceSets.EMPTY, List.of(all), zone("p11-a", "A", 0, 0, box(8), attach(all)));
		Heard music = heard(context, mc -> SimpleSoundInstance.forMusic(event(MUSIC)));
		expect(music, 0, PlayStatus.MUTED, "A", null);
		expectDecided(music, TraceProperty.LOUDNESS, "A", "p11-a", "all");

		// A real vanilla button: Enter on Back to Game on the pause menu plays the click, as a mouse click does.
		context.runOnClient(mc -> SEEN.remove(CLICK));
		context.setScreen(() -> new PauseScreen(true));
		context.waitForScreen(PauseScreen.class);
		context.runOnClient(mc -> {
			String label = Component.translatable("menu.returnToGame").getString();
			AbstractButton back = mc.gui.screen().children().stream().filter(c -> c instanceof AbstractButton b && b.getMessage().getString().equals(label))
					.map(c -> (AbstractButton) c).findFirst().orElseThrow(() -> new AssertionError("the pause menu has Back to Game"));
			mc.gui.screen().setFocused(back);
		});
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitFor(mc -> mc.gui.screen() == null, 100);
		Seen click = context.computeOnClient(mc -> SEEN.get(CLICK));
		check(click != null && click.status() == PlayStatus.MUTED && "A".equals(click.zone()), "the button click is muted by A, was " + click);
		expectDecided(CLICK, trace(context, CLICK), TraceProperty.LOUDNESS, "A", "p11-a", "all");

		Heard eat = heard(context, mc -> new EntityBoundSoundInstance(SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F, mc.player, 0L));
		expect(eat, 0, PlayStatus.MUTED, "A", null);
		expectDecided(eat, TraceProperty.LOUDNESS, "A", "p11-a", "all");
	}

	/**
	 * P12: a minecart rolls (a loop) while you stand outside A, which mutes minecarts. Walk into A: silent within one tick
	 * of entering; walk out: audible again within one tick. Checked on the very tick the feet block changes, at the end of
	 * the client tick, after Sound Sculptor's own tick work.
	 */
	private static void p12(ClientGameTestContext context) {
		SoundSet carts = set("p12-a", "No minecarts", SetEntry.of("m", "*minecart*").withMute(true));
		Zone a = zone("p12-a", "A", 12, 0, box(8), attach(carts));
		setUp(context, PlaceSets.EMPTY, List.of(carts), a);
		Heard rolling = heard(context, mc -> loop(MINECART, mc.player.getX() + 3, mc.player.getY(), mc.player.getZ() + 3));
		expect(rolling, 1.0F, PlayStatus.PLAYING, null, null);
		float before = context.computeOnClient(mc -> PlayingChannels.get().gain(rolling.instance()));
		check(before > 0.01F, "outside A the loop plays at its vanilla gain, was " + before);

		List<Tick> ticks = new ArrayList<>();
		context.runOnClient(mc -> probe = m -> {
			BlockPos feet = m.player.blockPosition();
			ticks.add(new Tick(a.containsBlock(feet.getX(), feet.getY(), feet.getZ()), PlayingChannels.get().gain(rolling.instance()),
					m.getSoundManager().isActive(rolling.instance())));
		});
		standAt(context, 12, 0);
		List<Tick> walkingIn = context.computeOnClient(mc -> List.copyOf(ticks));
		Tick entered = walkingIn.stream().filter(Tick::inA).findFirst().orElse(null);
		check(entered != null, "a tick with the feet in A was recorded: " + walkingIn);
		check(entered.gain() == 0.0F && entered.active(), "silent, and still playing, on the tick the feet entered A: " + entered);

		// A second minecart that starts while you are in A starts silently, so it can come back (FR-8.8).
		Heard second = heard(context, mc -> loop(MINECART, mc.player.getX() + 3, mc.player.getY(), mc.player.getZ() + 3));
		expect(second, 0, PlayStatus.MUTED, "A", null);
		check(second.started(), "a muted loop still starts, silently");
		expectDecided(second, TraceProperty.LOUDNESS, "A", "p12-a", "m");

		int inside = context.computeOnClient(mc -> ticks.size());
		standAt(context, 0, 0);
		List<Tick> walkingOut = context.computeOnClient(mc -> List.copyOf(ticks.subList(inside, ticks.size())));
		Tick left = walkingOut.stream().filter(t -> !t.inA()).findFirst().orElse(null);
		check(left != null, "a tick with the feet out of A was recorded");
		check(Math.abs(left.gain() - before) < 1e-4 && left.active(), "audible again on the tick the feet left A: " + left + ", before " + before);
		context.runOnClient(mc -> probe = null);
	}

	private record Tick(boolean inA, float gain, boolean active) {
	}

	/** P13: Everywhere has S1 (villagers 20%) above S2 (villagers 80%): 20%; swap them: 80%. */
	private static void p13(ClientGameTestContext context) {
		SoundSet s1 = set("p13-s1", "S1", SetEntry.of("v", VILLAGERS).withVolume(20));
		SoundSet s2 = set("p13-s2", "S2", SetEntry.of("v", VILLAGERS).withVolume(80));
		setUp(context, attach(s1, s2), List.of(s1, s2));
		Heard first = villager(context, VILLAGER, 3, 0);
		expect(first, 0.2F, PlayStatus.PLAYING, null, null);
		expectDecided(first, TraceProperty.LOUDNESS, "Everywhere", "p13-s1", "v");
		expectEntryReason(first, TraceProperty.LOUDNESS, EntryWinReason.HIGHER_SET);
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile().withEverywhere(ws.profile().everywhere().move(s2.id(), -1)),
				ws.library())));
		Heard swapped = villager(context, VILLAGER, 3, 0);
		expect(swapped, 0.8F, PlayStatus.PLAYING, null, null);
		expectDecided(swapped, TraceProperty.LOUDNESS, "Everywhere", "p13-s2", "v");
	}

	/** P14: one set: group villagers muted, single sound villager ambient 60%: ambient 60%, other villager sounds muted. */
	private static void p14(ClientGameTestContext context) {
		SoundSet villagers = set("p14", "Villagers", SetEntry.of("g", VILLAGERS).withMute(true), SetEntry.of("a", VILLAGER).withVolume(60));
		setUp(context, attach(villagers), List.of(villagers));
		Heard ambient = villager(context, VILLAGER, 3, 0);
		expect(ambient, 0.6F, PlayStatus.PLAYING, null, null);
		expectDecided(ambient, TraceProperty.LOUDNESS, "Everywhere", "p14", "a");
		expectEntryReason(ambient, TraceProperty.LOUDNESS, EntryWinReason.EXACT_ID);
		Heard yes = villager(context, VILLAGER_YES, 3, 0);
		expect(yes, 0, PlayStatus.MUTED, null, null);
		expectDecided(yes, TraceProperty.LOUDNESS, "Everywhere", "p14", "g");
	}

	/** P15: Everywhere has S1 (mutes villagers) above S2 (limits villagers to 2/s): muted (S1), limit from S2. */
	private static void p15(ClientGameTestContext context) {
		SoundSet s1 = set("p15-s1", "S1", SetEntry.of("m", VILLAGERS).withMute(true));
		SoundSet s2 = set("p15-s2", "S2", SetEntry.of("l", VILLAGERS).withLimit(TWO_PER_SECOND));
		setUp(context, attach(s1, s2), List.of(s1, s2));
		List<Seen> burst = burst(context, VILLAGER, 5);
		check(burst.stream().map(Seen::status).toList().equals(List.of(PlayStatus.MUTED, PlayStatus.MUTED, PlayStatus.LIMITED,
				PlayStatus.LIMITED, PlayStatus.LIMITED)), "5 at once: 2 muted, then 3 Limited, was " + burst);
		SoundTrace t = trace(context, VILLAGER);
		check(TWO_PER_SECOND.equals(t.limit()) && t.volumePercent() == 0, "the trace says muted with 2 per 1 s, was " + t.volumePercent() + "%, " + t.limit());
		expectDecided(VILLAGER, t, TraceProperty.LOUDNESS, "Everywhere", "p15-s1", "m");
		expectDecided(VILLAGER, t, TraceProperty.LIMIT, "Everywhere", "p15-s2", "l");
	}

	/** P16: A and B both attach "Farm animals" (cows 30%); edit it to 60%: 60% in A, and in B. */
	private static void p16(ClientGameTestContext context) {
		SoundSet farm = set("p16-farm", "Farm animals", SetEntry.of("c", COWS).withVolume(30));
		setUp(context, PlaceSets.EMPTY, List.of(farm),
				zone("p16-a", "A", 0, 0, box(8), attach(farm)),
				zone("p16-b", "B", 40, 0, box(8), attach(farm)));
		Heard before = villager(context, COW, 3, 0);
		expect(before, 0.3F, PlayStatus.PLAYING, "A", null);
		expectDecided(before, TraceProperty.LOUDNESS, "A", "p16-farm", "c");
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(ws.profile(),
				ws.library().put(farm.putEntry(SetEntry.of("c", COWS).withVolume(60))))));
		Heard inA = villager(context, COW, 3, 0);
		expect(inA, 0.6F, PlayStatus.PLAYING, "A", null);
		expectDecided(inA, TraceProperty.LOUDNESS, "A", "p16-farm", "c");
		standAt(context, 40, 0);
		Heard inB = villager(context, COW, 3, 0);
		expect(inB, 0.6F, PlayStatus.PLAYING, "B", null);
		expectDecided(inB, TraceProperty.LOUDNESS, "B", "p16-farm", "c");
	}

	/** P17: A mutes villagers; you stand in A; the villager is 10 blocks outside A: muted. */
	private static void p17(ClientGameTestContext context) {
		SoundSet mute = set("p17-a", "Mute villagers", SetEntry.of("m", VILLAGERS).withMute(true));
		Zone a = zone("p17-a", "A", 0, 0, box(8), attach(mute));
		setUp(context, PlaceSets.EMPTY, List.of(mute), a);
		// A covers 4 blocks either side of the player; the villager is at 14, 10 blocks beyond its edge.
		check(!a.containsBlock((int) Math.floor(HOME_X + 14), (int) HOME_Y, (int) Math.floor(HOME_Z)), "the villager is outside A");
		Heard h = villager(context, VILLAGER, 14, 0);
		expect(h, 0, PlayStatus.MUTED, "A", null);
		expectDecided(h, TraceProperty.LOUDNESS, "A", "p17-a", "m");
	}

	/**
	 * P18: A mutes villagers; you stand outside A; the villager is inside A: it plays at Everywhere's value. Everywhere sets
	 * villagers to 70% here, so the result is told apart from "nothing decided".
	 */
	private static void p18(ClientGameTestContext context) {
		SoundSet mute = set("p18-a", "Mute villagers", SetEntry.of("m", VILLAGERS).withMute(true));
		SoundSet everywhere = set("p18-e", "Villagers 70%", SetEntry.of("v", VILLAGERS).withVolume(70));
		Zone a = zone("p18-a", "A", 14, 0, box(8), attach(mute));
		setUp(context, attach(everywhere), List.of(mute, everywhere), a);
		check(a.containsBlock((int) Math.floor(HOME_X + 14), (int) HOME_Y, (int) Math.floor(HOME_Z)), "the villager is inside A");
		Heard h = villager(context, VILLAGER, 14, 0);
		expect(h, 0.7F, PlayStatus.PLAYING, null, null);
		check(h.trace().zones().isEmpty(), "the trace lists no zone: you are not in A");
		expectDecided(h, TraceProperty.LOUDNESS, "Everywhere", "p18-e", "v");
	}

	/**
	 * P19: A sets records to 30%; you stand in A while a jukebox record plays; Bypass on: vanilla volume within one tick;
	 * Bypass off: it stays at vanilla volume (a record is not a loop); the next record started in A plays at 30%.
	 */
	private static void p19(ClientGameTestContext context) {
		SoundSet records = set("p19-a", "Quiet records", SetEntry.of("r", "minecraft:music_disc.*").withVolume(30));
		setUp(context, PlaceSets.EMPTY, List.of(records), zone("p19-a", "A", 0, 0, box(8), attach(records)));
		Heard record = heard(context, mc -> SimpleSoundInstance.forJukeboxSong(event(RECORD), mc.player.position().add(3, 0, 0)));
		expect(record, 0.3F, PlayStatus.PLAYING, "A", null);
		expectDecided(record, TraceProperty.LOUDNESS, "A", "p19-a", "r");
		float quiet = context.computeOnClient(mc -> PlayingChannels.get().gain(record.instance()));
		float vanilla = quiet / 0.3F;
		check(quiet > 0.01F, "the record is tracked at 30% of its vanilla gain, was " + quiet);

		context.runOnClient(mc -> ProfileManager.setBypass(true));
		context.waitTick();
		float bypassed = context.computeOnClient(mc -> PlayingChannels.get().gain(record.instance()));
		check(Math.abs(bypassed - vanilla) < 1e-3 && context.computeOnClient(mc -> mc.getSoundManager().isActive(record.instance())),
				"Bypass on: vanilla volume within one tick, was " + bypassed + ", vanilla " + vanilla);
		check(trace(context, RECORD).notices().contains(TraceNotice.BYPASS), "the trace leads with Bypass");

		// Bypass off: the record plays on at vanilla volume, sampled every 10 ticks for 2 s (as P20 does).
		context.runOnClient(mc -> ProfileManager.setBypass(false));
		for (int i = 0; i < 4; i++) {
			context.waitTicks(10);
			float after = context.computeOnClient(mc -> PlayingChannels.get().gain(record.instance()));
			check(Math.abs(after - vanilla) < 1e-3 && context.computeOnClient(mc -> mc.getSoundManager().isActive(record.instance())),
					"Bypass off: the playing record stays at vanilla volume, was " + after + " after " + (i + 1) * 10 + " ticks");
		}

		Heard next = heard(context, mc -> SimpleSoundInstance.forJukeboxSong(event(RECORD_NEXT), mc.player.position().add(-3, 0, 0)));
		expect(next, 0.3F, PlayStatus.PLAYING, "A", null);
		expectDecided(next, TraceProperty.LOUDNESS, "A", "p19-a", "r");
	}

	/**
	 * P20: A mutes {@code minecraft:music.*}; a music track is playing; walk into A: the track plays on as it started; the
	 * next track that starts while you are in A is silent. "To its end" is checked for 2 seconds after entering: a track
	 * is never decided again after it starts (FR-8.8), so nothing later can change it.
	 */
	private static void p20(ClientGameTestContext context) {
		SoundSet music = set("p20-a", "No music", SetEntry.of("m", "minecraft:music.*").withMute(true));
		setUp(context, PlaceSets.EMPTY, List.of(music), zone("p20-a", "A", 12, 0, box(8), attach(music)));
		context.runOnClient(mc -> {
			// The track must be able to start, and the background music must not be in the way.
			mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(1.0);
			mc.getMusicManager().stopPlaying();
		});
		Heard track = heard(context, mc -> SimpleSoundInstance.forMusic(event(MUSIC)));
		expect(track, 1.0F, PlayStatus.PLAYING, null, null);
		float before = context.computeOnClient(mc -> PlayingChannels.get().gain(track.instance()));
		standAt(context, 12, 0);
		for (int i = 0; i < 4; i++) {
			context.waitTicks(10);
			float now = context.computeOnClient(mc -> PlayingChannels.get().gain(track.instance()));
			check(context.computeOnClient(mc -> mc.getSoundManager().isActive(track.instance())) && Math.abs(now - before) < 1e-4,
					"in A the playing track plays on as it started: gain " + now + ", was " + before);
		}
		Heard next = heard(context, mc -> SimpleSoundInstance.forMusic(event(MUSIC)));
		expect(next, 0, PlayStatus.MUTED, "A", null);
		expectDecided(next, TraceProperty.LOUDNESS, "A", "p20-a", "m");
	}

	// ---- Checks ----

	/**
	 * The play started at {@code factor} (0: muted; a muted loop starts silently), with {@code status}, naming
	 * {@code zone}, and the trace agrees on the level and the limit.
	 */
	private static void expect(Heard h, float factor, PlayStatus status, String zone, Limit limit) {
		String what = h.id() + ": ";
		check(h.status() == status, what + "status " + status + ", was " + h.status());
		if (factor == 0) {
			check(!h.started() || h.factor() <= SILENT, what + "muted, but it started at " + h.factor());
		} else {
			check(h.started() && Math.abs(h.factor() - factor) < 1e-4, what + "starts at " + factor + ", was " + (h.started() ? h.factor() : "not started"));
		}
		check(java.util.Objects.equals(zone, h.zone()), what + "the zone named should be " + zone + ", was " + h.zone());
		check(Math.abs(h.trace().volumePercent() - factor * 100) < 0.01, what + "the trace says " + h.trace().volumePercent() + "%, expected " + factor * 100);
		check(java.util.Objects.equals(limit, h.trace().limit()), what + "the trace's limit should be " + limit + ", was " + h.trace().limit());
	}

	private static void expectDecided(Heard h, TraceProperty property, String place, String setId, String entryId) {
		expectDecided(h.id(), h.trace(), property, place, setId, entryId);
	}

	/** The trace names {@code place} (a zone's name, "Everywhere", or {@code null} for none), set and entry for {@code property}. */
	private static void expectDecided(String id, SoundTrace t, TraceProperty property, String place, String setId, String entryId) {
		String decider = decider(t, property);
		EntryWinner winner = winner(t, property);
		String found = decider == null ? "nothing" : decider + " / " + (winner == null ? "?" : winner.setId() + " / " + winner.entry().id());
		String wanted = place == null ? "nothing" : place + " / " + setId + " / " + entryId;
		check(found.equals(wanted), id + ": the trace's " + property + " should be decided by " + wanted + ", was " + found);
	}

	private static void expectZoneReason(Heard h, TraceProperty property, ZoneWinReason reason) {
		ZoneWinner z = h.trace().zoneWinners().get(property);
		check(z != null && z.reason() == reason, h.id() + ": the zone should win " + property + " by " + reason + ", was " + z);
	}

	private static void expectEntryReason(Heard h, TraceProperty property, EntryWinReason reason) {
		EntryWinner w = winner(h.trace(), property);
		check(w != null && w.reason() == reason, h.id() + ": the entry should win " + property + " by " + reason + ", was " + w);
	}

	/** The place that decided {@code property}: a zone's name, "Everywhere", or {@code null} when no place sets it. */
	private static String decider(SoundTrace t, TraceProperty property) {
		ZoneWinner zone = t.zoneWinners().get(property);
		if (zone != null) {
			return zone.zone().name();
		}
		return t.everywhere().winners().containsKey(property) ? "Everywhere" : null;
	}

	private static EntryWinner winner(SoundTrace t, TraceProperty property) {
		ZoneWinner zone = t.zoneWinners().get(property);
		if (zone == null) {
			return t.everywhere().winners().get(property);
		}
		for (ZoneLine line : t.zones()) {
			if (line.zone().id().equals(zone.zone().id())) {
				return line.sets().winners().get(property);
			}
		}
		return null;
	}

	// ---- Building the cases ----

	private static SoundSet set(String id, String name, SetEntry... entries) {
		return new SoundSet(id, name, List.of(entries));
	}

	private static PlaceSets attach(SoundSet... sets) {
		PlaceSets place = PlaceSets.EMPTY;
		for (SoundSet s : sets) {
			place = place.attach(s.id());
		}
		return place;
	}

	private static Shape.Box box(int side) {
		return new Shape.Box(side, side, side);
	}

	/** A zone in this world's Overworld, centered {@code (dx, dz)} from where the player stands by default. */
	private static Zone zone(String id, String name, double dx, double dz, Shape shape, PlaceSets sets) {
		return new Zone(id, name, true, key, OVERWORLD, new Point(HOME_X + dx, HOME_Y, HOME_Z + dz), shape, sets);
	}

	/** A fresh profile: Everywhere and the zones as given, the sets put in the library, boost allowed, no counters. */
	private static void setUp(ClientGameTestContext context, PlaceSets everywhere, List<SoundSet> sets, Zone... zones) {
		context.runOnClient(mc -> {
			ProfileManager.change(ws -> {
				SetLibrary library = ws.library();
				for (SoundSet s : sets) {
					library = library.put(s);
				}
				return new Workspace(Profile.empty(ws.profile().name()).withBoostConfirmed(true).withEverywhere(everywhere)
						.withZones(List.of(zones)), library);
			});
			SoundHook.limiter().clear();
			CueOverlay.clear();
		});
		context.waitTicks(1);
	}

	/** Moves the player's feet to {@code (dx, dz)} from home on the floor (FR-8.4) and waits until the client is there. */
	private static void standAt(ClientGameTestContext context, double dx, double dz) {
		double x = HOME_X + dx;
		double z = HOME_Z + dz;
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f", x, HOME_Y, z));
		BlockPos target = BlockPos.containing(x, HOME_Y, z);
		context.waitFor(mc -> mc.player != null && mc.player.blockPosition().equals(target), 200);
		context.waitTicks(SETTLE);
	}

	// ---- Playing sounds ----

	private static SoundEvent event(String id) {
		return SoundEvent.createVariableRangeEvent(Identifier.parse(id));
	}

	private static SoundInstance instance(String id, double x, double y, double z) {
		return new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(), false, 0,
				SoundInstance.Attenuation.LINEAR, x, y, z, false);
	}

	private static SoundInstance loop(String id, double x, double y, double z) {
		return new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(), true, 0,
				SoundInstance.Attenuation.LINEAR, x, y, z, false);
	}

	/** A villager-style sound {@code (dx, dz)} from the player. */
	private static Heard villager(ClientGameTestContext context, String id, double dx, double dz) {
		return heard(context, mc -> instance(id, mc.player.getX() + dx, mc.player.getY() + 1, mc.player.getZ() + dz));
	}

	/** Starts the sound, then reads what happened: started or not, its start factor, the hook's status, and its trace. */
	private static Heard heard(ClientGameTestContext context, Function<Minecraft, SoundInstance> make) {
		Heard h = context.computeOnClient(mc -> {
			SoundInstance sound = make.apply(mc);
			String id = sound.getIdentifier().toString();
			SEEN.remove(id);
			mc.getSoundManager().play(sound);
			boolean started = mc.getSoundManager().isActive(sound);
			float factor = started ? SoundHook.lastStartFactor() : 0.0F;
			Seen seen = SEEN.get(id);
			check(seen != null, id + " should have been decided by the sound hook");
			return new Heard(id, sound, started, factor, seen.status(), seen.zone(), new TraceScreen(null, id).trace());
		});
		context.waitTicks(SETTLE);
		return h;
	}

	/** {@code count} villager sounds at once around the player; what the hook did with each, in order. */
	private static List<Seen> burst(ClientGameTestContext context, String id, int count) {
		List<Seen> seen = context.computeOnClient(mc -> {
			List<Seen> out = new ArrayList<>();
			Vec3 at = mc.player.position();
			for (int i = 0; i < count; i++) {
				SEEN.remove(id);
				mc.getSoundManager().play(instance(id, at.x + (i % 5) - 2, at.y, at.z + 2 + i / 5));
				out.add(SEEN.get(id));
			}
			return out;
		});
		context.waitTicks(SETTLE);
		return seen;
	}

	/** The trace of the sound's last play, as the "Why does it sound like this?" panel works it out. */
	private static SoundTrace trace(ClientGameTestContext context, String id) {
		return context.computeOnClient(mc -> new TraceScreen(null, id).trace());
	}

	private record Snapshot(Profile profile, SetLibrary library) {
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
