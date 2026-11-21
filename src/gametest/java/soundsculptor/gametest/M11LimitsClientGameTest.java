package soundsculptor.gametest;

import soundsculptor.core.diag.DiagnosticsReport;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.sets.Limit;
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
import soundsculptor.mc.CueOverlay;
import soundsculptor.mc.Diagnostics;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.LimitControls;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SoundHook;
import soundsculptor.mc.SoundObserver;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.TraceScreen;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Automated version of the M11 milestone test (PRD 11.1): the FR-6 acceptance criteria and matrix cases P4 and P4b
 * (11.4), as far as they can be checked without listening, plus the new controls in the entry editor, the Sounds tab's
 * pop-up, the trace and Diagnostics.
 *
 * <p>Villager sounds are played from the test at 20 fixed spots around the player, like 20 villagers in a pen, rather
 * than waiting for 20 real villagers. The time they are decided at comes from {@link TestClock} (a test-only mixin), so
 * 30 seconds of sounds take one tick and a slow CI runner cannot stretch a window. What happened to each play is read
 * from the sound hook's observers, the recently played list, the trace and the Diagnostics numbers.
 * <ul>
 *   <li>20 villagers, ambient limited to 3 per 1 s, shared: over 30 s, 90 play and 510 are Limited, never more than 3
 *   start in any one second; the Sounds tab row says "Limited ×510"; Diagnostics shows the busiest window, 3 of 3.</li>
 *   <li>Per source: every villager is heard at its own 1 per second, so all 600 play.</li>
 *   <li>Minecart loops limited to 1 per 10 s: the first loop starts and is never cut off (also when a set is edited
 *   while it plays); one started 2 s later is silent. Two real minecarts summoned 2 s apart: the first rolls, the
 *   second is Limited.</li>
 *   <li>Everywhere 5 per second and a zone 1 per second: inside the zone 1 plays, outside 5.</li>
 *   <li>One set (2 per second) attached to two zones far apart: each zone counts on its own.</li>
 *   <li>P4: A mutes villagers, B limits them to 2 per second: 2 play muted, the rest Limited, and the trace names A for
 *   loudness and B for the limit. P4b: A at 40%, B 2 per second: at most 2 per second, each at 40%.</li>
 * </ul>
 */
public final class M11LimitsClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String VILLAGERS = "minecraft:entity.villager.*";
	private static final String MINECART = "minecraft:entity.minecart.riding";
	private static final String MINECARTS = "minecraft:entity.minecart.*";
	private static final String OVERWORLD = "minecraft:overworld";
	private static final PlaceRef EVERYWHERE = PlaceRef.EVERYWHERE;
	private static final int PEN = 20;

	/** What the sound hook did with each villager or minecart sound, in order, while recording. */
	private static final List<Play> PLAYS = new ArrayList<>();
	private static boolean recording;
	private static final SoundObserver OBSERVER = o -> {
		if (recording && (o.idString().startsWith("minecraft:entity.villager.") || o.idString().startsWith("minecraft:entity.minecart."))) {
			PLAYS.add(new Play(o.idString(), o.status(), TestClock.lastDecided, o.zoneName()));
		}
	};

	private record Play(String id, PlayStatus status, long at, String zone) {
		boolean started() {
			return status != PlayStatus.LIMITED;
		}
	}

	/** The fake time every sound is decided at from now on; it only moves forward. */
	private static long now;

	@Override
	public void runTest(ClientGameTestContext context) {
		double musicBefore = context.computeOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get());
		double neutralBefore = context.computeOnClient(mc -> mc.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).get());
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("tp @a 0.5 -60 0.5 180 0");
			context.waitTicks(3);
			String key = context.computeOnClient(mc -> ProfileManager.key());
			context.runOnClient(mc -> {
				// Background music would show in the Sounds list; it is not what this test looks at.
				mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
				ProfileManager.setBypass(false);
				ProfileManager.update(p -> Profile.empty(p.name()));
				now = Util.getMillis() + 1_000_000;
				TestClock.fixedNow = now;
				SoundHook.limiter().clear();
				RecentLog.get().sounds().clear();
				SoundHook.addObserver(OBSERVER);
				recording = true;
			});

			villagerPenShared(context);
			soundsTabAndTrace(context);
			villagerPenPerSource(context);
			minecarts(context, world);
			everywhereAndZone(context, world, key);
			oneSetTwoZones(context, world, key);
			p4(context, key);
			p4b(context, key);
			entryEditor(context);
		} finally {
			context.runOnClient(mc -> {
				recording = false;
				SoundHook.removeObserver(OBSERVER);
				TestClock.fixedNow = null;
				// Real time is behind the fake times the counters hold.
				SoundHook.limiter().clear();
				mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(musicBefore);
				mc.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(neutralBefore);
				ProfileManager.update(p -> Profile.empty(p.name()));
				ProfileManager.flushSaves();
				CueOverlay.clear();
			});
		}
	}

	// ---- FR-6 AC 1 and 2: the villager pen ----

	/** AC 1: 20 villagers, 3 per 1 s shared, 30 s: about 90 play, the rest Limited, never more than 3 in one second. */
	private static void villagerPenShared(ClientGameTestContext context) {
		setQuickLimit(context, VILLAGER, new Limit(3, 1000, false));
		List<Play> plays = pen(context, 30_000);
		List<Play> started = plays.stream().filter(Play::started).toList();
		check(plays.size() == 600, "20 villagers, each once a second for 30 s, was " + plays.size());
		check(started.size() == 90, "3 per second for 30 s play, was " + started.size());
		checkAtMost(started, 3, 1000, "AC 1");

		RecentRow row = recentRow(context, VILLAGER);
		check(row.count() == 90 && row.dropped() == 510 && row.status() == PlayStatus.LIMITED,
				"the list shows 90 played and 510 Limited, was " + row);
		DiagnosticsReport.Data data = context.computeOnClient(mc -> Diagnostics.data());
		check(data.limits().allowed() == 90 && data.limits().dropped() == 510, "Diagnostics counts 90 played and 510 dropped: " + data.limits());
		check(data.limits().busiest() == 3 && new Limit(3, 1000, false).equals(data.limits().busiestLimit()),
				"Diagnostics: at most 3 in one window, of 3 per 1 s: " + data.limits());

		// The readout on Settings › Diagnostics.
		openMain(context);
		context.runOnClient(mc -> {
			main(mc).selectTab(SoundSculptorScreen.TAB_SETTINGS);
			main(mc).setSettingsPage(SoundSculptorScreen.SettingsPage.DIAGNOSTICS);
		});
		context.waitTicks(2);
		// A long line wraps onto two labels at a small window; joined with a space it reads as one line again.
		String diag = context.computeOnClient(mc -> String.join(" ", labels(main(mc))).replaceAll("\\s+", " "));
		check(diag.contains("Repeat limits: 90 played, 510 dropped. Most in one window: 3 (limit 3 per 1 s)"), "the Diagnostics page shows it:\n" + diag);
		context.takeScreenshot("m11-diagnostics");
		closeScreen(context);
	}

	/** AC 2: Per source: each villager counts on its own, so every one is heard and the total rises. */
	private static void villagerPenPerSource(ClientGameTestContext context) {
		setQuickLimit(context, VILLAGER, new Limit(3, 1000, true));
		List<Play> plays = pen(context, 30_000);
		long started = plays.stream().filter(Play::started).count();
		check(started == 600, "with Per source each villager plays its 1 per second, was " + started);
	}

	/**
	 * Each of 20 villagers in its own block plays its ambient sound once a second, one every 50 ms in turn, for
	 * {@code millis} of fake time. Neutral sounds are at 0% meanwhile, so 600 sounds do not all reach the audio device;
	 * Sound Sculptor still decides every one.
	 */
	private static List<Play> pen(ClientGameTestContext context, long millis) {
		return context.computeOnClient(mc -> {
			mc.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(0.0);
			PLAYS.clear();
			SoundHook.limiter().clear();
			Vec3 at = mc.player.position();
			long start = now + 20_000;
			for (int step = 0; step * 50L < millis; step++) {
				TestClock.fixedNow = start + step * 50L;
				int i = step % PEN;
				play(mc, VILLAGER, at.x + (i % 5) - 2, at.y, at.z + 2 + i / 5, false);
			}
			now = start + millis;
			TestClock.fixedNow = now;
			mc.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(1.0);
			return List.copyOf(PLAYS);
		});
	}

	// ---- FR-6.8 on the Sounds tab, the pop-up and the trace (FR-1.5, FR-14) ----

	private static void soundsTabAndTrace(ClientGameTestContext context) {
		openMain(context);
		context.runOnClient(mc -> {
			main(mc).selectTab(SoundSculptorScreen.TAB_SOUNDS);
			main(mc).sounds().setRecentOnly(true);
		});
		context.waitTicks(3);
		List<String> texts = context.computeOnClient(mc -> main(mc).sounds().list.findRow(VILLAGER).columnTexts());
		check(texts.contains("⏸ Limited ×510") && texts.stream().anyMatch(t -> t.startsWith("×90 ")),
				"the row shows 90 plays and Limited with 510 dropped: " + texts);
		context.takeScreenshot("m11-sounds-limited");

		// The row's Level, cue and limit… pop-up shows the actual limit at Everywhere and changes it in Quick changes.
		String label = context.computeOnClient(mc -> main(mc).sounds().list.findRow(VILLAGER).buttons().get(SoundsPanel.BUTTON_LEVEL)
				.getMessage().getString());
		check(label.equals("Level, cue and limit…"), "the row's button names the limit, was " + label);
		context.runOnClient(mc -> main(mc).sounds().openLevel(VILLAGER));
		context.waitTicks(1);
		check(context.computeOnClient(mc -> {
			LimitControls limit = main(mc).sounds().levelLimit;
			return limit != null && limit.toggle.value() && limit.countField.getValue().equals("3") && limit.windowField.getValue().equals("1")
					&& !limit.perSourceToggle.value();
		}), "the pop-up shows Limit On, 3 per 1 s, shared");
		context.takeScreenshot("m11-sounds-popup");
		context.runOnClient(mc -> main(mc).sounds().levelLimit.countField.setValue("4"));
		check(new Limit(4, 1000, false).equals(quickLimit(context, VILLAGER)), "typing 4 changes Quick changes at once");
		context.runOnClient(mc -> main(mc).sounds().levelLimit.windowField.setValue("20"));
		check(context.computeOnClient(mc -> main(mc).sounds().levelLimit.windowField.error().getString()).equals("Type a number from 0.05 to 10"),
				"a window above 10 s shows an error");
		check(new Limit(4, 1000, false).equals(quickLimit(context, VILLAGER)), "and changes nothing");
		context.runOnClient(mc -> main(mc).sounds().levelLimit.windowField.revealError());
		context.waitTicks(1);
		context.takeScreenshot("m11-sounds-popup-error");
		context.runOnClient(mc -> {
			main(mc).sounds().levelLimit.windowField.setValue("1");
			main(mc).sounds().levelLimit.countField.setValue("3");
			main(mc).closeOverlay();
		});
		check(new Limit(3, 1000, false).equals(quickLimit(context, VILLAGER)), "back to 3 per 1 s");
		closeScreen(context);

		// The trace (FR-14.2 step 4): the limit and that the last play was Limited.
		String trace = context.computeOnClient(mc -> new TraceScreen(null, VILLAGER).text());
		check(trace.contains("  Repeat limit: at most 3 per 1 s, shared\n  Last play was Limited: dropped by the repeat limit\n"),
				"the trace shows the limit and the dropped play:\n" + trace);
		check(trace.contains(VILLAGER + ": At most 3 per 1 s\n"), "the entry line shows its limit:\n" + trace);
		openMain(context);
		context.runOnClient(mc -> mc.gui.setScreen(new TraceScreen(main(mc), VILLAGER)));
		context.waitForScreen(TraceScreen.class);
		context.runOnClient(mc -> {
			TraceScreen t = (TraceScreen) mc.gui.screen();
			SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), EVERYWHERE, VILLAGER);
			SculptList.Row row = t.row(new TraceText.EntryTarget(new EntryLocation(EVERYWHERE, SoundSet.QUICK_ID, entry.id()), entry));
			check(row != null, "the trace lists the Quick changes entry");
			// FR-14.4: the line's Level… panel holds its limit too (a sixth button does not fit a line at 854 × 480).
			SculptButton level = row.buttons().stream().filter(b -> b.getMessage().getString().equals("Level…")).findFirst().orElse(null);
			check(level != null, "the entry line has Level…");
			check(NarrationCompat.collect(level::updateNarration).contains("level and repeat limit"), "Level… is read with what it changes");
			level.onPress(null);
			check(t.hasOverlay() && t.limitControls != null && t.limitControls.toggle.value() && t.limitControls.countField.getValue().equals("3"),
					"Level… shows the line's limit: On, 3 per 1 s");
		});
		context.takeScreenshot("m11-trace-limit");
		context.runOnClient(mc -> ((TraceScreen) mc.gui.screen()).limitControls.perSourceToggle.onPress(null));
		check(new Limit(3, 1000, true).equals(quickLimit(context, VILLAGER)), "Per source from the trace applies at once");
		context.runOnClient(mc -> {
			TraceScreen t = (TraceScreen) mc.gui.screen();
			t.closeOverlay();
			check(t.isPredicted() && t.text().contains("Repeat limit: at most 3 per 1 s, per source"), "the trace shows the new limit:\n" + t.text());
		});
		closeScreen(context);
		setQuickLimit(context, VILLAGER, new Limit(3, 1000, false));
	}

	// ---- FR-6 AC 3: minecarts (FR-6.6) ----

	private static void minecarts(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> {
			ProfileManager.update(p -> Profile.empty(p.name()));
			SoundHook.limiter().clear();
		});
		setEverywhere(context, SetEntry.of("c", MINECARTS).withLimit(new Limit(1, 10_000, false)));
		SoundInstance[] loops = new SoundInstance[2];
		check(context.computeOnClient(mc -> {
			Vec3 at = mc.player.position();
			loops[0] = play(mc, MINECART, at.x + 3, at.y, at.z, true);
			return mc.getSoundManager().isActive(loops[0]);
		}), "the first minecart loop starts");
		context.runOnClient(mc -> advance(2_000));
		check(!context.computeOnClient(mc -> {
			Vec3 at = mc.player.position();
			loops[1] = play(mc, MINECART, at.x - 3, at.y, at.z, true);
			return mc.getSoundManager().isActive(loops[1]);
		}), "a second loop 2 s later is silent");
		context.waitTicks(20);
		check(context.computeOnClient(mc -> mc.getSoundManager().isActive(loops[0])), "the first keeps playing");
		// An edit while it plays re-decides playing loops (FR-8.8): the limit is never applied again to a playing sound.
		setEverywhere(context, SetEntry.of("c", MINECARTS).withLimit(new Limit(1, 10_000, false)).withVolume(80));
		context.waitTicks(3);
		check(context.computeOnClient(mc -> mc.getSoundManager().isActive(loops[0])), "an edit does not cut the playing loop off");
		context.runOnClient(mc -> mc.getSoundManager().stop(loops[0]));

		// Two real minecarts, summoned 2 s apart: the game starts each one's rolling sound when it appears.
		context.runOnClient(mc -> {
			advance(20_000);
			PLAYS.clear();
		});
		Vec3 at = context.computeOnClient(mc -> mc.player.position());
		world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:minecart %.1f %.1f %.1f", at.x + 3, at.y, at.z));
		context.waitFor(mc -> PLAYS.stream().anyMatch(p -> p.id().equals(MINECART)), 200);
		context.runOnClient(mc -> advance(2_000));
		world.getServer().runCommand(String.format(Locale.ROOT, "summon minecraft:minecart %.1f %.1f %.1f", at.x - 3, at.y, at.z + 2));
		context.waitFor(mc -> PLAYS.stream().filter(p -> p.id().equals(MINECART)).count() >= 2, 200);
		List<PlayStatus> carts = context.computeOnClient(mc -> PLAYS.stream().filter(p -> p.id().equals(MINECART)).map(Play::status).toList());
		check(carts.size() == 2 && carts.get(0) != PlayStatus.LIMITED && carts.get(1) == PlayStatus.LIMITED,
				"the first minecart rolls, the second is Limited: " + carts);
		world.getServer().runCommand("kill @e[type=minecraft:minecart]");
		context.waitTicks(2);
	}

	// ---- FR-6 AC 4 and 5: places ----

	/** AC 4 (FR-6.7): Everywhere 5 per second, a zone 1 per second: inside the zone at most 1 plays, outside 5. */
	private static void everywhereAndZone(ClientGameTestContext context, TestSingleplayerContext world, String key) {
		Vec3 home = context.computeOnClient(mc -> mc.player.position());
		Zone zone = new Zone("m11-zone", "Pen", true, key, OVERWORLD, new Point(home.x, home.y, home.z), new Shape.Box(10, 10, 10),
				PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("z", VILLAGERS).withLimit(new Limit(1, 1000, false)))));
		context.runOnClient(mc -> {
			ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
					.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("e", VILLAGERS).withLimit(new Limit(5, 1000, false)))))
					.withZones(List.of(zone)), ws.library()));
			SoundHook.limiter().clear();
			advance(5_000);
		});
		List<Play> inside = burst(context, 10);
		check(inside.stream().filter(Play::started).count() == 1, "inside the zone 1 plays: " + inside);
		check(inside.stream().filter(p -> !p.started()).allMatch(p -> "Pen".equals(p.zone())), "the drops name the zone");
		teleport(context, world, home.x + 30, home.y, home.z);
		List<Play> outside = burst(context, 10);
		check(outside.stream().filter(Play::started).count() == 5, "outside it Everywhere's 5 play: " + outside);
		teleport(context, world, home.x, home.y, home.z);
	}

	/** AC 5 (FR-6.4): one set limiting villagers to 2 per second, attached to two zones far apart, counts in each. */
	private static void oneSetTwoZones(ClientGameTestContext context, TestSingleplayerContext world, String key) {
		Vec3 home = context.computeOnClient(mc -> mc.player.position());
		SoundSet pens = new SoundSet("m11-pens", "Villager pens", List.of(SetEntry.of("e1", VILLAGERS).withLimit(new Limit(2, 1000, false))));
		Zone a = new Zone("m11-a", "Pen A", true, key, OVERWORLD, new Point(home.x, home.y, home.z), new Shape.Box(10, 10, 10),
				PlaceSets.EMPTY.attach(pens.id()));
		Zone b = new Zone("m11-b", "Pen B", true, key, OVERWORLD, new Point(home.x + 200, home.y, home.z), new Shape.Box(10, 10, 10),
				PlaceSets.EMPTY.attach(pens.id()));
		context.runOnClient(mc -> {
			ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()).withZones(List.of(a, b)), ws.library().put(pens)));
			SoundHook.limiter().clear();
			advance(5_000);
		});
		check(burst(context, 5).stream().filter(Play::started).count() == 2, "pen A plays 2");
		teleport(context, world, home.x + 200, home.y, home.z);
		check(burst(context, 5).stream().filter(Play::started).count() == 2, "pen B plays 2 of its own, in the same second");
		teleport(context, world, home.x, home.y, home.z);
		check(burst(context, 5).stream().noneMatch(Play::started), "back in pen A, its 2 for this second are used up");
		context.runOnClient(mc -> advance(1_000));
		check(burst(context, 5).stream().filter(Play::started).count() == 2, "a second later pen A plays 2 again");
	}

	// ---- Matrix P4 and P4b (11.4) ----

	/** P4: A mutes villagers, B limits them to 2 per second, the player in both: muted, with B's limit. */
	private static void p4(ClientGameTestContext context, String key) {
		Vec3 home = context.computeOnClient(mc -> mc.player.position());
		Zone a = new Zone("p4-a", "A", true, key, OVERWORLD, new Point(home.x, home.y, home.z), new Shape.Box(8, 8, 8),
				PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("m", VILLAGERS).withMute(true).withCue(true))));
		Zone b = new Zone("p4-b", "B", true, key, OVERWORLD, new Point(home.x, home.y, home.z), new Shape.Box(12, 12, 12),
				PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("l", VILLAGERS).withLimit(new Limit(2, 1000, false)))));
		context.runOnClient(mc -> {
			ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()).withZones(List.of(a, b)), ws.library()));
			SoundHook.limiter().clear();
			advance(5_000);
			CueOverlay.clear();
		});
		List<Play> plays = burst(context, 5);
		check(plays.stream().map(Play::status).toList().equals(List.of(PlayStatus.MUTED, PlayStatus.MUTED, PlayStatus.LIMITED,
				PlayStatus.LIMITED, PlayStatus.LIMITED)), "P4: 2 muted, then Limited: " + plays);
		check("A".equals(plays.get(0).zone()) && "B".equals(plays.get(4).zone()), "P4: A decides loudness, B the drops: " + plays);
		String trace = context.computeOnClient(mc -> new TraceScreen(null, VILLAGER).text());
		check(trace.contains("  loudness: A (") && trace.contains("  limit: B (") && trace.contains("Repeat limit: at most 2 per 1 s, shared"),
				"P4: the trace names A for loudness and B for the limit:\n" + trace);
		context.runOnClient(mc -> CueOverlay.clear());
	}

	/** P4b: A sets villagers to 40%, B limits them to 2 per second: at most 2 per second, each at 40%. */
	private static void p4b(ClientGameTestContext context, String key) {
		Vec3 home = context.computeOnClient(mc -> mc.player.position());
		Zone a = new Zone("p4b-a", "A", true, key, OVERWORLD, new Point(home.x, home.y, home.z), new Shape.Box(8, 8, 8),
				PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("v", VILLAGERS).withVolume(40))));
		Zone b = new Zone("p4b-b", "B", true, key, OVERWORLD, new Point(home.x, home.y, home.z), new Shape.Box(12, 12, 12),
				PlaceSets.EMPTY.withQuickEntries(List.of(SetEntry.of("l", VILLAGERS).withLimit(new Limit(2, 1000, false)))));
		context.runOnClient(mc -> {
			ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()).withZones(List.of(a, b)), ws.library()));
			SoundHook.limiter().clear();
			advance(5_000);
		});
		// 10 villagers, each once in 3 seconds, one every 100 ms; the started ones are heard, at 40%.
		List<Float> factors = new ArrayList<>();
		List<Play> plays = context.computeOnClient(mc -> {
			PLAYS.clear();
			Vec3 at = mc.player.position();
			for (int step = 0; step < 30; step++) {
				TestClock.fixedNow = now + step * 100L;
				int i = step % 10;
				SoundInstance sound = play(mc, VILLAGER, at.x + (i % 5) - 2, at.y, at.z + 2 + i / 5, false);
				if (mc.getSoundManager().isActive(sound)) {
					factors.add(SoundHook.lastStartFactor());
				}
			}
			now += 3_000;
			TestClock.fixedNow = now;
			return List.copyOf(PLAYS);
		});
		List<Play> started = plays.stream().filter(Play::started).toList();
		check(started.size() == 6, "P4b: 2 per second for 3 s, was " + started.size());
		checkAtMost(started, 2, 1000, "P4b");
		check(factors.size() == 6 && factors.stream().allMatch(f -> Math.abs(f - 0.4F) < 1e-6), "P4b: each at 40%: " + factors);
	}

	// ---- The entry editor (FR-4.3, FR-6.1) ----

	private static void entryEditor(ClientGameTestContext context) {
		context.runOnClient(mc -> ProfileManager.update(p -> Profile.empty(p.name())));
		openMain(context);
		context.runOnClient(mc -> mc.gui.setScreen(EntryEditorScreen.forSound(main(mc), EVERYWHERE, VILLAGER)));
		context.waitForScreen(EntryEditorScreen.class);
		check(context.computeOnClient(mc -> {
			LimitControls l = editor(mc).limitControls;
			return !l.toggle.value() && l.countField.getValue().isEmpty() && l.windowField.getValue().equals("1") && !l.windowField.active
					&& !l.perSourceToggle.value() && !l.perSourceToggle.active;
		}), "the limit is off by default, the window 1 s, shared");
		check(context.computeOnClient(mc -> NarrationCompat.collect(editor(mc).limitControls.toggle::updateNarration)).contains("Limit: Off"),
				"the toggle is read with its state");
		context.takeScreenshot("m11-entry-editor-off");

		// Keyboard: Enter on the toggle turns it on; Tab then goes count, window, Per source, reset (reading order).
		context.runOnClient(mc -> editor(mc).setFocused(editor(mc).limitControls.toggle));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(1);
		check(context.computeOnClient(mc -> {
			LimitControls l = editor(mc).limitControls;
			return l.toggle.value() && l.countField.getValue().equals("3") && l.windowField.active && l.perSourceToggle.active;
		}), "on: 3 per 1 s");
		List<Object> order = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(1);
			order.add(context.computeOnClient(mc -> mc.gui.screen().getFocused()));
		}
		check(context.computeOnClient(mc -> {
			EntryEditorScreen e = editor(mc);
			return order.equals(List.of(e.limitControls.countField, e.limitControls.windowField, e.limitControls.perSourceToggle, e.limitReset));
		}), "Tab goes count, window, Per source, reset: " + order);
		check(context.computeOnClient(mc -> NarrationCompat.collect(editor(mc).limitControls.windowField::updateNarration)).contains("Window in seconds"),
				"the window field is read with its unit");

		// Validation like the other fields (FR-12.7): an error under the field, Save off.
		context.runOnClient(mc -> {
			editor(mc).limitControls.windowField.setValue("0.01");
			editor(mc).limitControls.windowField.revealError();
		});
		context.waitTicks(1);
		check(context.computeOnClient(mc -> editor(mc).limitControls.windowField.error().getString()).equals("Type a number from 0.05 to 10"),
				"a window under 50 ms shows an error");
		check(!context.computeOnClient(mc -> editor(mc).saveButton.active), "and Save is off");
		context.takeScreenshot("m11-entry-editor-error");
		context.runOnClient(mc -> {
			editor(mc).limitControls.windowField.setValue("0.5");
			editor(mc).limitControls.countField.setValue("100");
			editor(mc).limitControls.perSourceToggle.onPress(null);
		});
		context.waitTicks(1);
		context.takeScreenshot("m11-entry-editor");
		check(context.computeOnClient(mc -> editor(mc).saveButton.active), "valid again: Save is on");
		context.runOnClient(mc -> editor(mc).save());
		context.waitTicks(1);
		check(new Limit(100, 500, true).equals(quickLimit(context, VILLAGER)), "Save writes 100 per 0.5 s, per source");
		closeScreen(context);
	}

	// ---- Helpers ----

	/** {@code count} villager sounds at once around the player, at the current fake time. */
	private static List<Play> burst(ClientGameTestContext context, int count) {
		return context.computeOnClient(mc -> {
			PLAYS.clear();
			Vec3 at = mc.player.position();
			for (int i = 0; i < count; i++) {
				play(mc, VILLAGER, at.x + (i % 5) - 2, at.y, at.z + 2 + i / 5, false);
			}
			return List.copyOf(PLAYS);
		});
	}

	/** At most {@code n} of {@code plays} started within any {@code windowMs}, by the times they were decided at. */
	private static void checkAtMost(List<Play> plays, int n, long windowMs, String what) {
		for (int i = n; i < plays.size(); i++) {
			check(plays.get(i).at() - plays.get(i - n).at() >= windowMs,
					what + ": " + (n + 1) + " started within " + windowMs + " ms, at " + plays.get(i - n).at() + " and " + plays.get(i).at());
		}
	}

	private static SoundInstance play(Minecraft mc, String id, double x, double y, double z, boolean looping) {
		SoundInstance sound = new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(), looping, 0,
				SoundInstance.Attenuation.LINEAR, x, y, z, false);
		mc.getSoundManager().play(sound);
		return sound;
	}

	/** Moves the fake clock forward. Render thread. */
	private static void advance(long millis) {
		now += millis;
		TestClock.fixedNow = now;
	}

	/** The sound's single-sound entry in Everywhere's Quick changes gets {@code limit}; nothing else changes. */
	private static void setQuickLimit(ClientGameTestContext context, String pattern, Limit limit) {
		context.runOnClient(mc -> ProfileManager.change(ws -> QuickActions.apply(ws, EVERYWHERE, pattern,
				new soundsculptor.core.trace.EntryEdit.SetLimit(limit.count(), limit.windowMs(), limit.perSource())).after()));
	}

	/** Everywhere's Quick changes hold just {@code entries}; no zones. */
	private static void setEverywhere(ClientGameTestContext context, SetEntry... entries) {
		context.runOnClient(mc -> ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name())
				.withEverywhere(PlaceSets.EMPTY.withQuickEntries(List.of(entries))), ws.library())));
	}

	private static Limit quickLimit(ClientGameTestContext context, String soundId) {
		return context.computeOnClient(mc -> {
			SetEntry entry = QuickActions.quickEntry(ProfileManager.workspace(), EVERYWHERE, soundId);
			return entry == null ? null : entry.limit();
		});
	}

	private static RecentRow recentRow(ClientGameTestContext context, String id) {
		RecentRow row = context.computeOnClient(mc -> RecentLog.get().sounds().snapshot(Util.getMillis(), SortOrder.MOST_RECENT, s -> null)
				.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null));
		check(row != null, "the recently played list should have " + id);
		return row;
	}

	private static void teleport(ClientGameTestContext context, TestSingleplayerContext world, double x, double y, double z) {
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f", x, y, z));
		context.waitFor(mc -> mc.player != null && mc.player.position().distanceToSqr(x, y, z) < 0.25, 200);
		context.waitTicks(2);
	}

	private static List<String> labels(SculptScreen screen) {
		List<String> texts = new ArrayList<>();
		for (SculptScreen.Body.Label label : screen.body().labels()) {
			texts.add(label.text().get().getString());
		}
		return texts;
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
