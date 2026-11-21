package soundsculptor.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import soundsculptor.SoundSculptorClient;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.sets.PlaceSets;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SoundSet;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.zones.Zone;
import soundsculptor.mc.AttachSetScreen;
import soundsculptor.mc.EntryEditorScreen;
import soundsculptor.mc.PlaceSetsScreen;
import soundsculptor.mc.ProfileManager;
import soundsculptor.mc.RecentLog;
import soundsculptor.mc.SetEditorScreen;
import soundsculptor.mc.SetNameScreen;
import soundsculptor.mc.SoundPickerScreen;
import soundsculptor.mc.SoundSculptorScreen;
import soundsculptor.mc.SoundsPanel;
import soundsculptor.mc.ZoneEditorScreen;
import soundsculptor.mc.ui.SculptColorPicker;
import soundsculptor.mc.ui.SculptDialog;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;

/**
 * PRD 6.4 A-2 and 11.5: the seven core tasks, each done with the keyboard only, through the game's real key input.
 * Every control is reached with Tab (and the arrow keys inside a list), and pressed with Enter or Space; text is
 * typed. Only the fixture (a sound heard, another server's profile) is set up in code.
 * <ol>
 *   <li>Mute a recently played sound.</li>
 *   <li>Create a Sound Set and add sounds to it from the picker.</li>
 *   <li>Edit an entry's level.</li>
 *   <li>Create a box zone at your position and attach a set to it.</li>
 *   <li>Change a zone's color.</li>
 *   <li>Copy settings from another server.</li>
 *   <li>Turn on Bypass.</li>
 * </ol>
 */
public final class CoreTasksClientGameTest implements FabricClientGameTest {
	private static final String VILLAGER = "minecraft:entity.villager.ambient";
	private static final String COW = "minecraft:entity.cow.ambient";
	private static final String SET_NAME = "Keyboard farm";
	private static final String OTHER = "a2-other.example.net";
	/** Tab presses allowed to reach one control; every screen has fewer controls than this. */
	private static final int MAX_STEPS = 60;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = TestWorlds.create(context)) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				ProfileManager.change(ws -> new Workspace(Profile.empty(ws.profile().name()), ws.library()));
				// Another server whose Everywhere mutes pigs, for task 6.
				ProfileManager.saveOther(OTHER, Profile.empty("Friend's server").withEverywhere(PlaceSets.EMPTY.withQuickEntries(
						List.of(SetEntry.of("p1", "minecraft:entity.pig.ambient").withMute(true)))));
				ProfileManager.flushSaves();
				SoundsPanel.resetRememberedState();
				RecentLog.get().sounds().clear();
			});
			try {
				muteRecentSound(context);
				String setId = createSetFromPicker(context);
				editEntryLevel(context, setId);
				createZoneAndAttach(context, setId);
				changeZoneColor(context);
				copyFromOtherServer(context);
				turnOnBypass(context);
			} finally {
				context.runOnClient(mc -> {
					if (ProfileManager.isBypassed()) {
						ProfileManager.setBypass(false);
					}
					SoundsPanel.resetRememberedState();
					RecentLog.get().sounds().clear();
					ProfileManager.change(ws -> {
						Workspace clean = new Workspace(Profile.empty(ws.profile().name()), ws.library());
						for (SoundSet set : ws.library().sets()) {
							if (set.name().equals(SET_NAME)) {
								clean = new Workspace(clean.profile(), clean.library().remove(set.id()));
							}
						}
						return clean;
					});
					ProfileManager.delete(OTHER);
					ProfileManager.flushSaves();
				});
			}
		}
	}

	// ---- The seven tasks ----

	/** 1. The open key, the Sounds tab, type the sound in the search field, Tab into the list, Enter on its Mute. */
	private static void muteRecentSound(ClientGameTestContext context) {
		check(play(context, VILLAGER), "a villager sound plays");
		openMainScreen(context);
		selectTab(context, SoundSculptorScreen.TAB_SOUNDS);
		tabTo(context, "the search field", w -> w == main(Minecraft.getInstance()).sounds().searchField);
		context.getInput().typeChars(VILLAGER);
		context.waitTicks(SoundsPanel.REFRESH_TICKS + 2);
		listButton(context, mc -> ((SoundSculptorScreen) mc.gui.screen()).sounds().list, VILLAGER, "Mute");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(2);
		check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, VILLAGER)),
				"task 1: Enter on Mute mutes the villager");
		context.takeScreenshot("a2-1-muted");
	}

	/** 2. Sound Sets tab, New set, type a name, Save; Edit it, Add sounds…, search, Space on the cow's box, Add, Save. */
	private static String createSetFromPicker(ClientGameTestContext context) {
		selectTab(context, SoundSculptorScreen.TAB_SETS);
		tabTo(context, "New set", label("New set"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SetNameScreen.class);
		tabTo(context, "the name field", w -> w instanceof SculptTextField);
		context.getInput().typeChars(SET_NAME);
		tabTo(context, "Save", label("Save"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SoundSculptorScreen.class);
		String setId = context.computeOnClient(mc -> {
			for (SoundSet set : ProfileManager.library().sets()) {
				if (set.name().equals(SET_NAME)) {
					return set.id();
				}
			}
			return null;
		});
		check(setId != null, "task 2: New set makes \"" + SET_NAME + "\"");

		listButton(context, mc -> setsList(mc), setId, "Edit");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SetEditorScreen.class);
		tabTo(context, "Add sounds…", label("Add sounds…"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SoundPickerScreen.class);
		// The cow was not heard: with the Recently played filter on, Tab to it and Enter turns it off.
		if (context.computeOnClient(mc -> ((SoundPickerScreen) mc.gui.screen()).sounds().recentToggle.value())) {
			tabTo(context, "Recently played", w -> w == ((SoundPickerScreen) Minecraft.getInstance().gui.screen()).sounds().recentToggle);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(1);
		}
		tabTo(context, "the picker's search field", w -> w == ((SoundPickerScreen) Minecraft.getInstance().gui.screen()).sounds().searchField);
		context.getInput().typeChars(COW);
		context.waitTicks(SoundsPanel.REFRESH_TICKS + 2);
		listButton(context, mc -> ((SoundPickerScreen) mc.gui.screen()).sounds().list, COW, "Pick");
		context.getInput().pressKey(InputConstants.KEY_SPACE);
		context.waitTicks(1);
		tabTo(context, "Add", label("Add"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SetEditorScreen.class);
		check(context.computeOnClient(mc -> ((SetEditorScreen) mc.gui.screen()).entryList.children().size()) == 1,
				"task 2: the picked cow is the set's entry");
		tabTo(context, "Save", label("Save"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> ProfileManager.library().get(setId).entries().stream().anyMatch(e -> e.pattern().text().equals(COW))),
				"task 2: the saved set holds the cow, has " + context.computeOnClient(mc -> ProfileManager.library().get(setId).entries())
						+ " on " + context.computeOnClient(mc -> mc.gui.screen()));
		return setId;
	}

	/** 3. Edit the set, Edit entry on the cow, Left twice on the level slider (100% to 90%), Save, Save. */
	private static void editEntryLevel(ClientGameTestContext context, String setId) {
		listButton(context, mc -> setsList(mc), setId, "Edit");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SetEditorScreen.class);
		listButton(context, mc -> ((SetEditorScreen) mc.gui.screen()).entryList, null, "Edit entry");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(EntryEditorScreen.class);
		tabTo(context, "the level slider", w -> w instanceof SculptSlider && w.getMessage().getString().startsWith("Level"));
		context.getInput().pressKey(InputConstants.KEY_LEFT);
		context.getInput().pressKey(InputConstants.KEY_LEFT);
		context.waitTicks(1);
		tabTo(context, "Save", label("Save"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SetEditorScreen.class);
		tabTo(context, "Save", label("Save"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SoundSculptorScreen.class);
		Integer level = context.computeOnClient(mc -> ProfileManager.library().get(setId).entries().get(0).volume());
		check(level != null && level == 90, "task 3: two Left presses set the level to 90%, was " + level);
	}

	/** 4. Zones tab, New zone (at the player), Sound Sets…, Attach set…, Attach on the new set. */
	private static void createZoneAndAttach(ClientGameTestContext context, String setId) {
		selectTab(context, SoundSculptorScreen.TAB_ZONES);
		tabTo(context, "New zone", label("New zone"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(ZoneEditorScreen.class);
		tabTo(context, "Sound Sets…", label("Sound Sets…"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(PlaceSetsScreen.class);
		Zone zone = context.computeOnClient(mc -> ProfileManager.profile().zones().isEmpty() ? null : ProfileManager.profile().zones().get(0));
		check(zone != null, "task 4: Sound Sets… saves the new zone");
		Vec3 feet = context.computeOnClient(mc -> mc.player.position());
		check(Math.abs(zone.anchor().x() - feet.x) < 1 && Math.abs(zone.anchor().z() - feet.z) < 1, "task 4: the zone is at the player, was " + zone.anchor());
		tabTo(context, "Attach set…", label("Attach set…"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(AttachSetScreen.class);
		listButton(context, mc -> firstList(mc.gui.screen()), setId, "Attach");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(2);
		check(context.computeOnClient(mc -> ProfileManager.profile().zone(zone.id()).sets().uses(setId)), "task 4: the set is attached to the zone");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(2);
		closeToMain(context);
	}

	/** 5. Zones tab, Edit zone, Tab to a palette swatch, Enter, Save. */
	private static void changeZoneColor(ClientGameTestContext context) {
		selectTab(context, SoundSculptorScreen.TAB_ZONES);
		String zoneId = context.computeOnClient(mc -> ProfileManager.profile().zones().get(0).id());
		Integer before = context.computeOnClient(mc -> ProfileManager.profile().colorOf(zoneId));
		listButton(context, mc -> ((SoundSculptorScreen) mc.gui.screen()).zonesPanel().list, zoneId, "Edit zone");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(ZoneEditorScreen.class);
		tabTo(context, "a swatch of another color", w -> w instanceof SculptColorPicker.Swatch swatch && !Integer.valueOf(swatch.rgb()).equals(before));
		int chosen = context.computeOnClient(mc -> ((SculptColorPicker.Swatch) mc.gui.screen().getFocused()).rgb());
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(1);
		tabTo(context, "Save", label("Save"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> ProfileManager.profile().colorOf(zoneId)) == chosen, "task 5: the zone takes the chosen color");
	}

	/** 6. Profiles tab, Copy on the other server's row, Tab to the dialog's Copy, Enter. */
	private static void copyFromOtherServer(ClientGameTestContext context) {
		selectTab(context, SoundSculptorScreen.TAB_PROFILES);
		listButton(context, mc -> firstList(mc.gui.screen()), OTHER, "Copy");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SculptDialog.class);
		tabTo(context, "the dialog's Copy", label("Copy"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(SoundSculptorScreen.class);
		check(context.computeOnClient(mc -> QuickActions.isMuted(ProfileManager.workspace(), PlaceRef.EVERYWHERE, "minecraft:entity.pig.ambient")),
				"task 6: Copy brings the other server's pig mute to this world");
	}

	/** 7. Tab to the header's Bypass toggle and press Enter; the Bypass key turns it off again. */
	private static void turnOnBypass(ClientGameTestContext context) {
		tabTo(context, "Bypass", w -> w.getMessage().getString().startsWith("Bypass"));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(1);
		check(context.computeOnClient(mc -> ProfileManager.isBypassed()), "task 7: Enter on Bypass turns it on");
		context.takeScreenshot("a2-7-bypass");
		context.getInput().pressKey(SoundSculptorClient.bypassKey);
		context.waitTicks(1);
		check(!context.computeOnClient(mc -> ProfileManager.isBypassed()), "the Bypass key turns it off again");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(2);
	}

	// ---- Keyboard helpers ----

	/** Presses Tab until the focused control (inside lists too) matches; fails if it never does. */
	private static void tabTo(ClientGameTestContext context, String what, Predicate<AbstractWidget> wanted) {
		for (int i = 0; i < MAX_STEPS; i++) {
			if (context.computeOnClient(mc -> focusedLeaf(mc.gui.screen()) instanceof AbstractWidget w && wanted.test(w))) {
				return;
			}
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTicks(1);
		}
		throw new AssertionError("Tab never reaches " + what + " on " + context.computeOnClient(mc -> mc.gui.screen()));
	}

	/**
	 * Tabs into {@code list}, then moves with Down/Up to the row with {@code rowKey} (the first row when {@code null})
	 * and with Right/Left to its button labelled {@code button}.
	 */
	private static void listButton(ClientGameTestContext context, java.util.function.Function<Minecraft, SculptList> list, Object rowKey, String button) {
		tabTo(context, "the list", w -> w == list.apply(Minecraft.getInstance()) || isInside(list.apply(Minecraft.getInstance()), w));
		for (int i = 0; i < MAX_STEPS; i++) {
			int step = context.computeOnClient(mc -> {
				SculptList l = list.apply(mc);
				SculptList.Row focused = l.getFocused();
				SculptList.Row target = rowKey == null ? l.children().get(0) : l.findRow(rowKey);
				check(target != null, "no row " + rowKey + " in " + l.getMessage().getString());
				return focused == null ? 1 : Integer.compare(l.indexOf(target), l.indexOf(focused));
			});
			if (step == 0) {
				break;
			}
			context.getInput().pressKey(step > 0 ? InputConstants.KEY_DOWN : InputConstants.KEY_UP);
			context.waitTicks(1);
		}
		for (int i = 0; i < MAX_STEPS; i++) {
			int step = context.computeOnClient(mc -> {
				SculptList.Row row = list.apply(mc).getFocused();
				int at = row.buttons().indexOf(row.getFocused());
				for (int b = 0; b < row.buttons().size(); b++) {
					String text = row.buttons().get(b).getMessage().getString();
					// A checkbox reads its state after its name: "Pick: not checked".
					if (text.equals(button) || text.startsWith(button + ": ")) {
						return Integer.compare(b, at);
					}
				}
				throw new AssertionError("row " + row.key() + " has no " + button + " button");
			});
			if (step == 0) {
				return;
			}
			context.getInput().pressKey(step > 0 ? InputConstants.KEY_RIGHT : InputConstants.KEY_LEFT);
			context.waitTicks(1);
		}
		throw new AssertionError("the arrow keys never reach " + button);
	}

	private static boolean isInside(SculptList list, AbstractWidget widget) {
		for (SculptList.Row row : list.children()) {
			if (row.buttons().contains(widget)) {
				return true;
			}
		}
		return false;
	}

	private static GuiEventListener focusedLeaf(Screen screen) {
		GuiEventListener focused = screen == null ? null : screen.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) {
			focused = container.getFocused();
		}
		return focused;
	}

	private static Predicate<AbstractWidget> label(String text) {
		return w -> w.getMessage().getString().equals(text);
	}

	/** Tab to the tab bar's button for {@code tab}, then Enter. */
	private static void selectTab(ClientGameTestContext context, int tab) {
		tabTo(context, "tab " + tab, w -> w == main(Minecraft.getInstance()).tabButtons().get(tab));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(1);
		check(context.computeOnClient(mc -> main(mc).currentTab()) == tab, "Enter on the tab button opens tab " + tab);
	}

	/** The Sound Sculptor key opens the screen. */
	private static void openMainScreen(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.getInput().pressKey(SoundSculptorClient.openKey);
		context.waitForScreen(SoundSculptorScreen.class);
	}

	/** Esc until the main screen is back. */
	private static void closeToMain(ClientGameTestContext context) {
		for (int i = 0; i < 5 && !context.computeOnClient(mc -> mc.gui.screen() instanceof SoundSculptorScreen); i++) {
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(2);
		}
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof SoundSculptorScreen), "Esc returns to the Sound Sculptor screen");
	}

	private static SculptList setsList(Minecraft mc) {
		return firstList(mc.gui.screen());
	}

	private static SculptList firstList(Screen screen) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof SculptList list) {
				return list;
			}
		}
		throw new AssertionError("no list on " + screen);
	}

	private static SoundSculptorScreen main(Minecraft mc) {
		check(mc.gui.screen() instanceof SoundSculptorScreen, "expected the Sound Sculptor screen, found " + mc.gui.screen());
		return (SoundSculptorScreen) mc.gui.screen();
	}

	private static boolean play(ClientGameTestContext context, String id) {
		boolean started = context.computeOnClient(mc -> {
			Vec3 at = mc.getSoundManager().getListenerTransform().position();
			SoundInstance sound = new SimpleSoundInstance(Identifier.parse(id), SoundSource.NEUTRAL, 1.0F, 1.0F, RandomSource.create(),
					false, 0, SoundInstance.Attenuation.LINEAR, at.x() + 3, at.y(), at.z(), false);
			mc.getSoundManager().play(sound);
			return mc.getSoundManager().isActive(sound);
		});
		context.waitTicks(2);
		return started;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
