package soundsculptor.mc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import soundsculptor.core.browser.BrowserRow;
import soundsculptor.core.browser.HoldOrder;
import soundsculptor.core.browser.LikeThis;
import soundsculptor.core.browser.PickerModel;
import soundsculptor.core.browser.SoundBrowser;
import soundsculptor.core.quick.ApplyTarget;
import soundsculptor.core.quick.ApplyTo;
import soundsculptor.core.quick.QuickActionResult;
import soundsculptor.core.quick.QuickActions;
import soundsculptor.core.recent.DistanceBand;
import soundsculptor.core.recent.PlayStatus;
import soundsculptor.core.recent.RecentRow;
import soundsculptor.core.recent.RecentSounds;
import soundsculptor.core.recent.SortOrder;
import soundsculptor.core.recent.TimeAgo;
import soundsculptor.core.search.SearchQuery;
import soundsculptor.core.search.SoundEntry;
import soundsculptor.core.search.SoundNames;
import soundsculptor.core.sets.PlaceRef;
import soundsculptor.core.profile.Profile;
import soundsculptor.core.sets.SetEntry;
import soundsculptor.core.sets.SetLibrary;
import soundsculptor.core.sets.Workspace;
import soundsculptor.core.trace.EntryEdit;
import soundsculptor.mc.compat.Keys;
import soundsculptor.mc.ui.SculptButton;
import soundsculptor.mc.ui.SculptCheckbox;
import soundsculptor.mc.ui.SculptDropdown;
import soundsculptor.mc.ui.SculptList;
import soundsculptor.mc.ui.SculptScreen;
import soundsculptor.mc.ui.SculptSlider;
import soundsculptor.mc.ui.SculptTextField;
import soundsculptor.mc.ui.SculptToggle;
import soundsculptor.mc.ui.Theme;
import soundsculptor.mc.ui.Tooltips;

/**
 * The Sounds tab (PRD FR-1, FR-3, FR-12.1): one browser for every sound, replacing the Recently Played and Search
 * tabs. One search field with a "?" help (FR-3.5), the <b>Recently played</b> filter (FR-1.0) and the result count
 * ("42 sounds", FR-3.4). With the filter on, the list holds the recently played sounds that match, with Pause (P while
 * the list has focus) and Sort (FR-1.3); with it off, every known sound that matches (FR-3.1). The rows come from core
 * {@link SoundBrowser}; a row with recent data shows the FR-1.2 columns, one without shows "Seen as" (FR-3.2).
 *
 * <p>Quick actions on each row (FR-1.5, FR-3.7): Mute/Unmute (the sound's actual state at the place), Level… (a
 * slider pop-up, with Cue on/off for FR-7, which shows the actual state too), Edit entry, Add to set…
 * ({@link AddToSetScreen}) and Why? ({@link TraceScreen}). Mute, Level, Cue and Edit change the sound's single-sound
 * entry in the Quick changes of the place "Apply to" chose (FR-4.10), at once with a
 * 5-second Undo toast (FR-12.5).
 *
 * <p>In picker mode ({@link #picker}, FR-1.7) the same browser serves {@link SoundPickerScreen}: each row has a checkbox
 * (or "✔ In this set" and no checkbox) and Like this… instead of the quick actions, and there is no Apply to, Pause or
 * Sort.
 *
 * <p>While the mouse pointer is over the list it is held ({@link HoldOrder}, FR-1.3), so a row does not move away as
 * the player aims at it: no row moves, joins or leaves, the rows' values still update, and the count says "Held" and
 * how many sounds wait. The player's own search, filter, sort and Apply to changes are not held, and a row action
 * rebuilds rows in their place. Leaving the list catches up at the next refresh.
 *
 * <p>The filter, search text, sort, pause and Apply to choice are remembered while the game runs, apart for the tab and
 * the picker, so the picker never changes the tab's filter: the filter starts on the first time each opens and then
 * stays as the player left it. The list refreshes at most 4 times a second
 * (FR-1.6), each refresh building one recent snapshot and one mute predicate for every row; search waits 150 ms after
 * typing stops (FR-3.4).
 */
public final class SoundsPanel {
	/** FR-3.4: results update 150 ms after the player stops typing. */
	public static final long SEARCH_DELAY_MS = 150;
	/** FR-1.6: the list refreshes at most 4 times per second (every 5 ticks). */
	public static final int REFRESH_TICKS = 5;
	/** The row buttons, in PRD 6.1 order. */
	public static final int BUTTON_MUTE = 0;
	public static final int BUTTON_LEVEL = 1;
	public static final int BUTTON_EDIT = 2;
	public static final int BUTTON_ADD_TO_SET = 3;
	public static final int BUTTON_WHY = 4;

	private static final int MAX_APPLY_TO_WIDTH = 200;
	private static final int MIN_APPLY_TO_WIDTH = 120;
	/** The most a recent row's "Zone: <name>" gets; a long zone name is cut, and gives way in a narrow row. */
	private static final int ZONE_WIDTH = 110;
	/** The play count the plays column is measured for ("×999 · 2m ago"). */
	private static final int PLAYS_MEASURED = 999;
	private static final int HELP_WIDTH = 360;
	private static final int HELP_LINES = 7;
	private static final Identifier MUTE_ICON = icon("mute");
	private static final Identifier VOLUME_ICON = icon("volume");
	private static final Identifier LEVEL_ICON = icon("level");
	private static final Identifier EDIT_ICON = icon("edit");
	private static final Identifier ADD_ICON = icon("add");
	private static final Identifier HELP_ICON = icon("help");
	/** "✔ In this set", for what the set already has (FR-1.7); Add to set… uses the same text. */
	static final Component IN_SET = Component.translatable("soundsculptor.picker.in_set");

	/** The Sounds tab's remembered state; the picker has its own, so opening it changes nothing on the tab. */
	private static final Remembered TAB_STATE = new Remembered();
	private static final Remembered PICKER_STATE = new Remembered();

	private final SculptScreen host;
	private final Remembered state;
	/** Picker mode (FR-1.7): what the rows tick, or {@code null} on the Sounds tab. */
	private final PickerModel picker;
	/** Told after every tick or untick, so the picker's footer follows. */
	private final Runnable picksChanged;
	/** Picker mode: adds every pick to the set and closes the picker; false if nothing was added (the set is full). */
	private final BooleanSupplier addPicks;
	public SculptTextField searchField;
	public SculptButton helpButton;
	public SculptToggle recentToggle;
	/** "Apply to" (FR-1.5, FR-3.7), with the filter on or off. */
	public SculptDropdown<ApplyTarget> applyToDropdown;
	/** Shown only while the filter is on (FR-1.3). */
	public SculptToggle pauseToggle;
	/** Shown only while the filter is on (FR-1.3). */
	public SculptDropdown<SortOrder> sortDropdown;
	public SculptList list;
	/** The search examples, while the "?" pop-up is open. */
	public SculptList helpList;
	/** The Like this… groups (FR-4.8), while the pop-up is open (picker mode). */
	public SculptList likeThisList;
	/** The Level pop-up, while it is open. */
	public SculptSlider levelSlider;
	public SculptTextField levelField;
	/** The pop-up's Cue toggle, while it is open (FR-7). */
	public SculptToggle levelCue;
	/** The pop-up's repeat limit row, while it is open (FR-6, FR-1.5). */
	public LimitControls levelLimit;
	/** How many times a refresh ran (it may find nothing to do), for tests of the refresh rate. */
	public int refreshCount;
	/** How many rows were built, for tests that a refresh rebuilds only what changed. */
	public int rowsBuilt;

	private List<ApplyTarget> applyChoices = List.of(ApplyTarget.EVERYWHERE);
	/** Every known sound, built when the tab opens so names follow the language and resource packs (FR-3.1). */
	private List<SoundEntry> entries = List.of();
	private SearchQuery query = SearchQuery.parse("");
	private int count;
	/** Sounds a held list has not shown yet. */
	private int waiting;
	private boolean searchPending;
	private long searchChangedAt;
	private int ticks;

	// What the shown rows were built from (see refresh).
	private long shownGeneration = -1;
	private Profile shownProfile;
	private SetLibrary shownLibrary;
	private ApplyTarget shownTarget;
	private boolean shownDistance;
	private SearchQuery shownQuery;
	private boolean shownRecentOnly;
	private SortOrder shownSort;
	private long shownSecond;
	private boolean shownAnyRecent;
	private int shownPickVersion;
	/** Whether the last refresh held the list (the pointer was over it). */
	private boolean shownHeld;
	/**
	 * Whether the mouse has moved since the panel was built or the last key press. Vanilla puts the cursor in the middle
	 * of the window when a screen opens, which is usually over the list: a cursor that only rests there, or that a
	 * keyboard player left there, must not hold it.
	 */
	private boolean pointerMoved;
	private double restX;
	private double restY;
	private int seenKeyPresses;
	/** The mute predicate for this refresh, made once when the first row needs it. */
	private Predicate<String> refreshMuted;
	/** Counts ticks and unticks in picker mode, so a refresh after one is not skipped. */
	private int pickVersion;
	/** The Like this… pop-up's sound and its groups (counted once when it opens). */
	private String likeThisSound;
	private List<LikeThis.Group> likeThisGroups = List.of();
	/** The row columns' and Like this…'s widths, measured once per panel rather than for each of up to 2,000 rows. */
	private Widths widths;
	/** Built rows by sound ID; cleared when the workspace, Apply to or Show distance changes. */
	private final Map<String, CachedRow> rowCache = new HashMap<>();

	// The Level pop-up: one visit is one change for Undo.
	private String levelSound;
	private ApplyTarget levelTarget;
	private Workspace levelBefore;

	/** The Sounds tab. */
	public SoundsPanel(SculptScreen host) {
		this(host, TAB_STATE, null, null, null);
	}

	private SoundsPanel(SculptScreen host, Remembered state, PickerModel picker, Runnable picksChanged, BooleanSupplier addPicks) {
		this.host = host;
		this.state = state;
		this.picker = picker;
		this.picksChanged = picksChanged;
		this.addPicks = addPicks;
	}

	/**
	 * The browser in picker mode (FR-1.7): a checkbox on each row (or "In this set" when the set has the sound) and Like
	 * this…, instead of the quick actions; no Apply to, Pause or Sort, which act on places. Its filter and search text
	 * are remembered apart from the Sounds tab's.
	 *
	 * @param picksChanged runs after every tick or untick
	 * @param addPicks     adds every pick to the set and closes the picker, as its Add does; returns false if nothing was
	 *                     added (the set is full), when the picker stays open
	 */
	public static SoundsPanel picker(SculptScreen host, PickerModel picker, Runnable picksChanged, BooleanSupplier addPicks) {
		return new SoundsPanel(host, PICKER_STATE, picker, picksChanged, addPicks);
	}

	/**
	 * Back to how a new game run starts, for the tab and the picker: the filter on, no search text, most recent first,
	 * Everywhere. For tests.
	 */
	public static void resetRememberedState() {
		TAB_STATE.reset();
		PICKER_STATE.reset();
	}

	/** Remembered while the game runs (FR-1.0, FR-1.3, FR-1.5). */
	private static final class Remembered {
		boolean recentOnly;
		String searchText;
		SortOrder sort;
		boolean paused;
		/** The zone "Apply to" chose last, or null for Everywhere. Zone ids restart in every profile, so it counts only in {@link #applyWorldKey}. */
		String applyZoneId;
		/** The world (profile key) {@link #applyZoneId} was chosen in. */
		String applyWorldKey;

		Remembered() {
			reset();
		}

		void reset() {
			recentOnly = true;
			searchText = "";
			sort = SortOrder.MOST_RECENT;
			paused = false;
			applyZoneId = null;
			applyWorldKey = null;
		}
	}

	// ---- Building ----

	public void build(SculptScreen.Body body) {
		restPointer();
		Font font = Minecraft.getInstance().font;
		recentToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(Component.translatable("soundsculptor.sounds.recent_only")),
				Component.translatable("soundsculptor.sounds.recent_only"), state.recentOnly, this::setRecentOnly),
				Component.translatable("soundsculptor.sounds.recent_only.tip"));
		helpButton = SculptButton.icon(HELP_ICON, Component.translatable("soundsculptor.sounds.help.show"), this::openHelp);
		searchField = new SculptTextField(font, Math.max(60, body.width() - Theme.ICON_BUTTON_SIZE - recentToggle.getWidth() - 2 * Theme.GAP),
				Component.translatable("soundsculptor.sounds.field"))
				.placeholder(Component.translatable("soundsculptor.sounds.hint"));
		searchField.setMaxLength(200);
		searchField.setValue(state.searchText);
		searchField.onChange(text -> {
			state.searchText = text;
			searchPending = true;
			searchChangedAt = Util.getMillis();
		});
		body.row(searchField, helpButton, recentToggle);

		// The picker has no Apply to, Pause or Sort: they act on places, and its rows have no quick actions.
		if (picker == null) {
			buildPlaceRow(body);
		}

		body.label(this::countText);
		list = new SculptList(body.width(), 100, Component.translatable("soundsculptor.sounds.list"));
		list.setEmptyText(() -> Component.translatable(!state.recentOnly ? "soundsculptor.sounds.empty"
				: query.isEmpty() ? "soundsculptor.sounds.empty_recent" : "soundsculptor.sounds.empty_recent_filtered"));
		body.fill(list);

		entries = KnownSounds.entries();
		query = SearchQuery.parse(state.searchText);
		refresh();
	}

	/** Apply to, Pause and Sort (the Sounds tab only). */
	private void buildPlaceRow(SculptScreen.Body body) {
		pauseToggle = Tooltips.describe(new SculptToggle(SculptToggle.widthFor(Component.translatable("soundsculptor.sounds.pause")),
				Component.translatable("soundsculptor.sounds.pause"), state.paused, this::setPaused),
				Component.translatable("soundsculptor.sounds.pause.tip"));
		sortDropdown = Tooltips.describe(new SculptDropdown<>(sortWidth(), Component.translatable("soundsculptor.sounds.sort"),
				List.of(SortOrder.values()), SoundsPanel::sortName, state.sort, this::setSort),
				Component.translatable("soundsculptor.sounds.sort.tip"));
		applyChoices = computeApplyChoices();
		Component applyLabel = Component.translatable("soundsculptor.sounds.apply_to");
		int share = Math.max(MIN_APPLY_TO_WIDTH,
				Math.min(MAX_APPLY_TO_WIDTH, body.width() - pauseToggle.getWidth() - sortDropdown.getWidth() - 2 * Theme.GAP));
		// Never narrower than "Apply to: Everywhere" in full; a long zone name is cut instead.
		int applyWidth = Math.max(share, SculptDropdown.widthFor(applyLabel, List.of(applyName(ApplyTarget.EVERYWHERE))));
		applyToDropdown = Tooltips.describe(new SculptDropdown<>(applyWidth, applyLabel, applyChoices, SoundsPanel::applyName,
				applyTarget(), this::setApplyTarget), Component.translatable("soundsculptor.sounds.apply_to.tip"));
		// Pause and Sort keep their place while hidden, so turning the filter on or off moves nothing else. When the
		// three do not fit on one line (a narrow window, a longer translation), Sort and then Pause move to the next.
		body.flow(applyToDropdown, pauseToggle, sortDropdown);
		showFilterControls();
	}

	private static Component applyName(ApplyTarget choice) {
		return Component.translatable(choice.translationKey(), choice.args().toArray());
	}

	private static int sortWidth() {
		List<Component> names = new ArrayList<>();
		for (SortOrder order : SortOrder.values()) {
			names.add(sortName(order));
		}
		return SculptDropdown.widthFor(Component.translatable("soundsculptor.sounds.sort"), names);
	}

	private static Component sortName(SortOrder order) {
		return Component.translatable("soundsculptor.sounds.sort." + order.name().toLowerCase(Locale.ROOT));
	}

	private void showFilterControls() {
		if (pauseToggle != null) {
			pauseToggle.visible = state.recentOnly;
			sortDropdown.visible = state.recentOnly;
		}
	}

	// ---- State ----

	public boolean isRecentOnly() {
		return state.recentOnly;
	}

	/** The Recently played filter (FR-1.0); remembered while the game runs. */
	public void setRecentOnly(boolean value) {
		state.recentOnly = value;
		if (recentToggle != null) {
			recentToggle.setValue(value);
			showFilterControls();
		}
		refresh();
	}

	public boolean isPaused() {
		return state.paused;
	}

	/** Pause freezes the recently played list for reading (FR-1.3); actions and search still update it. */
	public void setPaused(boolean value) {
		state.paused = value;
		if (pauseToggle != null) {
			pauseToggle.setValue(value);
		}
		if (!value) {
			refresh();
		}
	}

	public void setSort(SortOrder value) {
		state.sort = value;
		if (sortDropdown != null) {
			sortDropdown.setValue(value);
		}
		refresh();
	}

	public String searchText() {
		return state.searchText;
	}

	/** How many sounds the list holds ("42 sounds"). */
	public int count() {
		return count;
	}

	/**
	 * Whether the list is held because the pointer is over it (FR-1.3): rows keep their place and new sounds wait. A
	 * paused list is frozen anyway, so it is not called held.
	 */
	public boolean isHeld() {
		return shownHeld && !(state.recentOnly && state.paused);
	}

	/** Whether the player is pointing at the list with the mouse: it moved since the build or the last key press. */
	private boolean pointing() {
		if (host.keyPresses() != seenKeyPresses) {
			restPointer();
		} else if (!pointerMoved) {
			Minecraft mc = Minecraft.getInstance();
			pointerMoved = mc.mouseHandler.xpos() != restX || mc.mouseHandler.ypos() != restY;
		}
		return pointerMoved && list.isHovered();
	}

	private void restPointer() {
		Minecraft mc = Minecraft.getInstance();
		restX = mc.mouseHandler.xpos();
		restY = mc.mouseHandler.ypos();
		seenKeyPresses = host.keyPresses();
		pointerMoved = false;
	}

	/** How many sounds wait to join the held list. */
	public int waiting() {
		return waiting;
	}

	/** "42 sounds", or "42 sounds · Held" and "42 sounds · Held · 3 new" while held. */
	private Component countText() {
		Component sounds = Component.translatable(count == 1 ? "soundsculptor.sounds.count.one" : "soundsculptor.sounds.count", count);
		if (!isHeld()) {
			return sounds;
		}
		return waiting > 0 ? Component.translatable("soundsculptor.sounds.held.new", sounds, waiting)
				: Component.translatable("soundsculptor.sounds.held", sounds);
	}

	// ---- Refresh ----

	/**
	 * Every tick while the tab is open: runs a search 150 ms after typing stops, and refreshes the rows every
	 * {@value #REFRESH_TICKS} ticks unless the recently played list is paused.
	 *
	 * @return whether the player walked into or out of a zone, so "Apply to" needs other choices: the screen rebuilds
	 *         (never in picker mode, which has no Apply to)
	 */
	public boolean tick() {
		ticks++;
		if (searchPending && Util.getMillis() - searchChangedAt >= SEARCH_DELAY_MS) {
			runSearch();
		}
		if (ticks % REFRESH_TICKS != 0) {
			return false;
		}
		if (!(state.recentOnly && state.paused)) {
			refresh();
		}
		return picker == null && !host.hasOverlay() && !computeApplyChoices().equals(applyChoices);
	}

	/** Runs the search now (normally 150 ms after the last keystroke). */
	public void runSearch() {
		searchPending = false;
		query = SearchQuery.parse(state.searchText);
		refresh();
	}

	/**
	 * Brings the rows up to date. The list keeps the focused row and the scroll position.
	 *
	 * <p>The full list has about 2,000 rows, so a refresh does only what changed (PRD 9.1, 11.2). It does nothing when
	 * the log's {@linkplain RecentSounds#generation generation}, the workspace, the query, the filter, the sort, Apply to
	 * and Show distance are as they were, and the clock has not reached the next second while a row shows recent data
	 * (a row may have left the last 3 minutes). Otherwise each row is rebuilt only if its columns or buttons change
	 * (whether it has recent data, its zone, or its "Seen as" category). A row whose recent values changed (a new play,
	 * a new status) keeps its buttons and only has those values updated, which its columns read when drawn: so a
	 * tooltip the player is reading on one of its buttons stays up, and "2m ago" follows the clock with no refresh.
	 * The list is replaced only if a row or the order changed. A new workspace, Apply to choice or Show distance setting
	 * rebuilds every row, because the Mute buttons and the columns depend on them. Does nothing while a pop-up is open,
	 * so focus stays in it.
	 *
	 * <p>While the player points at the list with the mouse it is held ({@link HoldOrder}): the rows keep their order,
	 * new ones wait, and rows that left the fresh list stay, brought up to date from the data they were built with.
	 * Entering or leaving the hold always refreshes.
	 *
	 * <p>In picker mode a tick or untick also counts as a change, and a row's inputs include whether it is picked and
	 * whether the set has it, so a tick rebuilds only its own row.
	 */
	public void refresh() {
		if (list == null || host.hasOverlay()) {
			return;
		}
		refreshCount++;
		long now = Util.getMillis();
		RecentSounds log = RecentLog.get().sounds();
		Workspace ws = ProfileManager.workspace();
		ApplyTarget target = applyTarget();
		boolean distance = SoundSculptorSettings.showDistance();
		long second = now / 1000;
		// Held while the pointer is over a list with rows, unless the player changed what it lists: their own search,
		// filter, sort or Apply to shows at once.
		boolean ownChange = query != shownQuery || state.recentOnly != shownRecentOnly || state.sort != shownSort || !target.equals(shownTarget);
		boolean hold = !ownChange && pointing() && !list.children().isEmpty();
		// workspace() is a new record each call: compare its parts, which change identity only when changed.
		// A held list may keep rows that left the fresh list; if the cache is cleared they are rebuilt from their old entry.
		Map<String, CachedRow> previous = rowCache;
		if (ws.profile() != shownProfile || ws.library() != shownLibrary || !target.equals(shownTarget) || distance != shownDistance) {
			previous = hold ? new HashMap<>(rowCache) : Map.of();
			rowCache.clear();
		} else if (log.generation() == shownGeneration && query == shownQuery && state.recentOnly == shownRecentOnly && state.sort == shownSort
				&& pickVersion == shownPickVersion && hold == shownHeld && (!shownAnyRecent || second == shownSecond)) {
			return;
		}
		shownProfile = ws.profile();
		shownLibrary = ws.library();
		shownTarget = target;
		shownDistance = distance;
		shownQuery = query;
		shownRecentOnly = state.recentOnly;
		shownSort = state.sort;
		shownPickVersion = pickVersion;
		shownSecond = second;
		List<RecentRow> recent = log.snapshot(now, SortOrder.MOST_RECENT, null);
		// Read after the snapshot, which drops expired rows.
		shownGeneration = log.generation();
		List<BrowserRow> found = SoundBrowser.rows(recent, entries, query, state.recentOnly, state.sort);
		refreshMuted = null;
		boolean anyRecent = false;
		List<SculptList.Row> rows = new ArrayList<>(found.size());
		Set<String> foundIds = hold ? new HashSet<>(found.size() * 2) : null;
		for (BrowserRow row : found) {
			CachedRow cached = cachedRow(row, log);
			if (cached.live() != null) {
				cached.live().update(row.recent());
			}
			anyRecent |= row.recent() != null;
			rows.add(cached.row());
			if (foundIds != null) {
				foundIds.add(row.soundId());
			}
		}
		shownAnyRecent = anyRecent;
		shownHeld = hold;
		if (hold) {
			// Rows kept on screen that left the fresh list (their sound left the log) are brought up to date too, from the
			// data they were built with: a row the player acts on must show its new state (Unmute, a tick).
			for (SculptList.Row shown : list.children()) {
				if (shown.key() instanceof String id && !foundIds.contains(id)) {
					CachedRow old = rowCache.containsKey(id) ? rowCache.get(id) : previous.get(id);
					if (old != null) {
						rows.add(cachedRow(old.source(), log).row());
					}
				}
			}
			HoldOrder.Held<SculptList.Row> held = HoldOrder.hold(list.children(), rows, SculptList.Row::key);
			rows = held.rows();
			waiting = held.waiting();
			// A kept row rebuilt with other columns (a first play, a new zone) keeps the height it had, so the row under
			// the pointer does not slide away from it while held.
			Map<Object, SculptList.Row> shownRows = new HashMap<>(list.children().size() * 2);
			for (SculptList.Row shown : list.children()) {
				shownRows.put(shown.key(), shown);
			}
			for (SculptList.Row row : rows) {
				SculptList.Row shown = shownRows.get(row.key());
				if (shown != null && shown != row) {
					row.keepHeight(shown.getHeight());
				}
			}
		} else {
			waiting = 0;
		}
		// Once the hold ends, every row takes its own height again.
		boolean released = false;
		if (!hold) {
			for (SculptList.Row row : rows) {
				if (row.keptHeight() > 0) {
					row.keepHeight(0);
					released = true;
				}
			}
		}
		count = rows.size();
		if (released || !sameRows(rows, list.children())) {
			list.setRows(rows);
		}
	}

	/**
	 * What a row is built from besides its name and ID: equal inputs mean the row would be built with the same columns
	 * and buttons. A recent row's changing values (plays, time, status, category, band) are not inputs: its
	 * {@link LiveRecent} is updated instead.
	 */
	private static Object rowInputs(BrowserRow row, RecentSounds log) {
		RecentRow recent = row.recent();
		if (recent == null) {
			String category = log.seenCategory(row.soundId());
			return category == null ? "" : category;
		}
		return new RecentInputs(recent.zoneName());
	}

	private static boolean sameRows(List<SculptList.Row> a, List<SculptList.Row> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i) != b.get(i)) {
				return false;
			}
		}
		return true;
	}

	/** A row with recent data: its zone decides whether it has a zone column, and that column's width. */
	private record RecentInputs(String zoneName) {
	}

	/** A picker row's inputs: what it shows, whether the set has the sound, and whether it is ticked. */
	private record PickInputs(Object shown, boolean inSet, boolean picked) {
	}

	/** A built row, what it was built from, and its recent values ({@code null} for a row without recent data). */
	private record CachedRow(Object inputs, SculptList.Row row, LiveRecent live, BrowserRow source) {
	}

	/** The cached row for a browser row, rebuilt if what it is built from changed. */
	private CachedRow cachedRow(BrowserRow row, RecentSounds log) {
		Object inputs = rowInputs(row, log);
		if (picker != null) {
			inputs = new PickInputs(inputs, picker.inSet(row.soundId()), picker.picked(row.soundId()));
		}
		CachedRow cached = rowCache.get(row.soundId());
		if (cached == null || !cached.inputs().equals(inputs)) {
			if (refreshMuted == null && picker == null) {
				refreshMuted = mutedAtApplyTarget();
			}
			LiveRecent live = row.recent() == null ? null : new LiveRecent(row.recent(), recentMark(row.soundId()));
			cached = new CachedRow(inputs, toListRow(row, live, refreshMuted), live, row);
			rowCache.put(row.soundId(), cached);
			rowsBuilt++;
		}
		return cached;
	}

	/**
	 * A recent row's latest values (FR-1.2), which its columns read each time they are drawn; a refresh updates them
	 * without rebuilding the row. Each text is made once per change (the plays text when the play count or the minute
	 * label changes), so drawing a row makes no new text.
	 */
	private static final class LiveRecent {
		/** "✔ In this set" in picker mode for a sound the set has, shown instead of the status; else {@code null}. */
		private final String mark;
		private RecentRow row;
		private String category;
		private String status;
		private String band;
		private TimeAgo ago;
		private String plays;

		LiveRecent(RecentRow row, String mark) {
			this.mark = mark;
			update(row);
		}

		void update(RecentRow latest) {
			if (latest.equals(row)) {
				return;
			}
			row = latest;
			category = latest.category() == null ? "?" : categoryName(latest.category()).getString();
			status = mark != null ? mark : statusText(latest.status(), latest.dropped()).getString();
			band = Component.translatable(latest.band().translationKey()).getString();
			plays = null;
		}

		String category() {
			return category;
		}

		String status() {
			return status;
		}

		String band() {
			return band;
		}

		String id() {
			return row.id();
		}

		/** "×3 · 2m ago", following the clock: the label changes once a minute, with no refresh needed. */
		String plays() {
			TimeAgo now = TimeAgo.of(Util.getMillis() - row.lastPlayedMs());
			if (plays == null || !now.equals(ago)) {
				ago = now;
				plays = Component.translatable("soundsculptor.sounds.plays", row.count(), agoText(now)).getString();
			}
			return plays;
		}

		/** "Last played 1m 23s ago", to the second, for the plays column's tooltip. */
		Component exact() {
			return exactAgo(Util.getMillis() - row.lastPlayedMs());
		}
	}

	/** Picker mode (FR-1.7): a sound the set has says so where its status would be; else {@code null}. */
	private String recentMark(String soundId) {
		return picker != null && picker.inSet(soundId) ? IN_SET.getString() : null;
	}

	/**
	 * @param live  the row's recent values, or {@code null} for a row without recent data
	 * @param muted which sounds are muted at the Apply to place; {@code null} in picker mode
	 */
	private SculptList.Row toListRow(BrowserRow row, LiveRecent live, Predicate<String> muted) {
		String id = row.soundId();
		String name = row.subtitle() == null ? SoundNames.readable(id) : row.subtitle();
		boolean inSet = picker != null && picker.inSet(id);
		String mark = recentMark(id);
		Widths widths = widths();
		List<SculptList.Row.Column> columns = new ArrayList<>();
		columns.add(new SculptList.Row.Column(() -> name, 0, 0));
		if (live == null) {
			String seen = mark != null ? mark : seenText(id);
			columns.add(new SculptList.Row.Column(() -> seen, widths.seen(), 0));
			columns.add(new SculptList.Row.Column(() -> id, 0, 1));
		} else {
			recentColumns(columns, row.recent().zoneName(), live, widths);
		}
		if (picker != null) {
			return new SculptList.Row(id, columns, pickButtons(id, inSet));
		}
		boolean isMuted = muted.test(id);
		return new SculptList.Row(id, columns, List.of(muteButton(id, isMuted), levelButton(id), editButton(id),
				addToSetButton(id), whyButton(id)));
	}

	/**
	 * FR-1.2: subtitle, category and status (text plus an icon character, never color alone) on top; ID, the zone that
	 * decided the latest play (FR-9), plays with time since the last, and the distance band (unless hidden) below.
	 *
	 * <p>The zone name may be long: its column is at most {@value #ZONE_WIDTH} px, cut with "…", and gives way in a
	 * narrow row so the ID stays readable.
	 *
	 * <p>The columns read {@code live}, so a new play or status shows without rebuilding the row. Hovering the plays
	 * column shows the exact time since the last play, to the second, counting up while it is shown.
	 *
	 * @param zoneName the zone that decided the latest play, or {@code null}; a row whose zone changes is rebuilt
	 */
	private static void recentColumns(List<SculptList.Row.Column> columns, String zoneName, LiveRecent live, Widths widths) {
		columns.add(new SculptList.Row.Column(live::category, widths.category(), 0));
		// The status may give way in a narrow row ("⏸ Limited ×510" is wider than the others), so the name stays readable.
		columns.add(new SculptList.Row.Column(live::status, widths.status(), 0, true));
		columns.add(new SculptList.Row.Column(live::id, 0, 1));
		if (zoneName != null) {
			Component zone = Component.translatable("soundsculptor.sounds.zone", zoneName);
			String zoneText = zone.getString();
			columns.add(new SculptList.Row.Column(() -> zoneText, Math.min(ZONE_WIDTH, SculptList.Row.Column.widthFor(zone)), 1, true));
		}
		columns.add(new SculptList.Row.Column(live::plays, widths.plays(), 1, false, live::exact));
		if (SoundSculptorSettings.showDistance()) {
			columns.add(new SculptList.Row.Column(live::band, widths.band(), 1));
		}
	}

	/** FR-3.2: "Seen as: Friendly Creatures" when the sound played this session outside the last 3 minutes, else "". */
	private static String seenText(String soundId) {
		String category = RecentLog.get().sounds().seenCategory(soundId);
		return category == null ? "" : seenAs(category).getString();
	}

	private static Component seenAs(String category) {
		return Component.translatable("soundsculptor.sounds.seen_as", categoryName(category));
	}

	private static Component categoryName(String category) {
		return Component.translatable("soundCategory." + category);
	}

	/** The status with its icon character, e.g. "▶ Playing" (FR-1.2: text and an icon, never color alone). */
	private static Component statusText(PlayStatus status) {
		return Component.translatable("soundsculptor.sounds.status_line." + status.name().toLowerCase(Locale.ROOT),
				Component.translatable(status.translationKey()));
	}

	/**
	 * The status, with the dropped count when a repeat limit dropped plays: "⏸ Limited ×12" (FR-6.8). The count is over
	 * the same 3 minutes as the play count, which counts only the plays that started.
	 */
	private static Component statusText(PlayStatus status, int dropped) {
		if (status == PlayStatus.LIMITED && dropped > 0) {
			return Component.translatable("soundsculptor.sounds.status_line.limited_count", Component.translatable(status.translationKey()), dropped);
		}
		return statusText(status);
	}

	/**
	 * The fixed row columns' widths, each fitting every text the column can show, so rows line up and nothing is cut;
	 * and the Like this… button's.
	 */
	private record Widths(int category, int status, int seen, int plays, int band, int likeThis) {
	}

	/** Measured the first time a row is built, in the language shown now. */
	private Widths widths() {
		if (widths != null) {
			return widths;
		}
		List<Component> categories = new ArrayList<>();
		List<Component> seen = new ArrayList<>();
		for (SoundSource source : SoundSource.values()) {
			categories.add(categoryName(source.getName()));
			seen.add(seenAs(source.getName()));
		}
		List<Component> statuses = new ArrayList<>();
		for (PlayStatus status : PlayStatus.values()) {
			statuses.add(statusText(status));
		}
		statuses.add(statusText(PlayStatus.LIMITED, PLAYS_MEASURED));
		List<Component> bands = new ArrayList<>();
		for (DistanceBand band : DistanceBand.values()) {
			bands.add(Component.translatable(band.translationKey()));
		}
		if (picker != null) {
			statuses.add(IN_SET);
			seen.add(IN_SET);
		}
		// Every label a recent row can show: it leaves the list RecentSounds.WINDOW_MS after its last play.
		List<Component> plays = new ArrayList<>();
		for (long millis = 0; millis < RecentSounds.WINDOW_MS; millis += 60_000) {
			plays.add(Component.translatable("soundsculptor.sounds.plays", PLAYS_MEASURED, agoText(TimeAgo.of(millis))));
		}
		widths = new Widths(widthFor(categories), widthFor(statuses), widthFor(seen), widthFor(plays),
				widthFor(bands), SculptButton.widthFor(Component.translatable("soundsculptor.picker.like_this")));
		return widths;
	}

	private static int widthFor(List<Component> texts) {
		return SculptList.Row.Column.widthFor(texts.toArray(Component[]::new));
	}

	/** "&lt;1m ago", "1m ago", "2m ago" or "&gt;1h ago" (FR-1.2). */
	private static Component agoText(TimeAgo ago) {
		return Component.translatable(ago.translationKey(), ago.value());
	}

	/** "Last played 45s ago", "Last played 1m 23s ago" or "Last played 1h 2m 5s ago". */
	private static Component exactAgo(long millis) {
		TimeAgo.Exact exact = TimeAgo.exact(millis);
		Component time;
		if (exact.hours() > 0) {
			time = Component.translatable("soundsculptor.sounds.ago.exact.hours", exact.hours(), exact.minutes(), exact.seconds());
		} else if (exact.minutes() > 0) {
			time = Component.translatable("soundsculptor.sounds.ago.exact.minutes", exact.minutes(), exact.seconds());
		} else {
			time = Component.translatable("soundsculptor.sounds.ago.exact.seconds", exact.seconds());
		}
		return Component.translatable("soundsculptor.sounds.ago.exact", time);
	}

	// ---- Row buttons ----

	/**
	 * Mute / Unmute: the icon shows the sound's actual state at the place "Apply to" chose, whichever set decides it
	 * (crossed-out speaker when muted); the label, tooltip and narration say what pressing it does.
	 */
	private SculptButton muteButton(String soundId, boolean muted) {
		return SculptButton.icon(muted ? MUTE_ICON : VOLUME_ICON,
				Component.translatable(muted ? "soundsculptor.sounds.unmute" : "soundsculptor.sounds.mute"), () -> toggleMute(soundId));
	}

	private SculptButton levelButton(String soundId) {
		return SculptButton.icon(LEVEL_ICON, Component.translatable("soundsculptor.sounds.level"), () -> openLevel(soundId));
	}

	/** Edit entry: the sound's single-sound entry in the Quick changes of the place "Apply to" chose (a new one if none). */
	private SculptButton editButton(String soundId) {
		return SculptButton.icon(EDIT_ICON, Component.translatable("soundsculptor.sounds.edit"),
				() -> setScreen(EntryEditorScreen.forSound(host, applyTarget().place(), soundId)));
	}

	private SculptButton addToSetButton(String soundId) {
		return SculptButton.icon(ADD_ICON, Component.translatable("soundsculptor.sounds.add_to_set"), () -> openAddToSet(soundId));
	}

	/** Why?: the sound trace for the sound's most recent play (FR-14.1). */
	private SculptButton whyButton(String soundId) {
		return SculptButton.icon(HELP_ICON, Component.translatable("soundsculptor.sounds.why"), () -> setScreen(new TraceScreen(host, soundId)));
	}

	/** Which sounds are muted at the place "Apply to" chose: one resolver for a whole rebuild, not one per row. */
	private Predicate<String> mutedAtApplyTarget() {
		return QuickActions.mutedAt(ProfileManager.workspace(), applyTarget().place());
	}

	// ---- Quick actions ----

	/**
	 * Mute/Unmute (FR-1.5, FR-4.10): edits the sound's single-sound entry in the Quick changes of the place "Apply to"
	 * chose, saves at once and shows a 5-second toast with Undo. A new mute takes the profile's Cue default; Unmute on a
	 * sound another set mutes writes "Not muted".
	 */
	public void toggleMute(String soundId) {
		ApplyTarget target = applyTarget();
		PlaceRef where = target.place();
		Workspace ws = ProfileManager.workspace();
		String worldKey = ProfileManager.key();
		// Through the memory, so Unmute right after Mute puts the entry back exactly as it was.
		EntryEdit edit = ProfileManager.mutes().toggleMute(worldKey, ws, where, soundId, SoundSculptorSettings.newMutesGetCue());
		QuickActionResult result = QuickActions.apply(ws, where, soundId, edit);
		if (!result.ok()) {
			host.showToast(ProfileManager.component(result.error()), null);
			return;
		}
		if (result.changed()) {
			ProfileManager.mutes().applied(worldKey, where, soundId, edit, result.before(), result.after());
			String toastKey = edit instanceof EntryEdit.MuteWithCue ? "soundsculptor.sounds.toast.muted" : "soundsculptor.sounds.toast.unmuted";
			QuickUndo.applyWorkspace(host, this::refreshAfterUndo, current -> QuickActions.apply(current, where, soundId, edit).after(),
					placeKey(toastKey, target), placeArgs(soundId, target));
		}
		refresh();
	}

	/**
	 * Cue on/off (FR-1.5, FR-7), in the Level, cue and limit pop-up: turns the sound's cue at the place "Apply to" chose on or
	 * off, whichever set decides it now, through its single-sound entry in that place's Quick changes; saved at once and
	 * undone with the pop-up's other changes. Clearing the entry's own Cue is preferred when that alone does it, so Mute,
	 * Cue off and Unmute leave no entry behind. The pop-up's toggle then shows the actual state again.
	 */
	public void toggleCue() {
		if (levelSound == null) {
			return;
		}
		PlaceRef where = levelTarget.place();
		String soundId = levelSound;
		EntryEdit edit = QuickActions.toggleCue(ProfileManager.workspace(), where, soundId);
		QuickActionResult result = QuickActions.apply(ProfileManager.workspace(), where, soundId, edit);
		if (!result.ok()) {
			host.showToast(ProfileManager.component(result.error()), null);
		} else if (result.changed()) {
			ProfileManager.change(current -> QuickActions.apply(current, where, soundId, edit).after());
		}
		if (levelCue != null) {
			levelCue.setValue(QuickActions.isCueOn(ProfileManager.workspace(), where, soundId));
		}
	}

	/**
	 * Level, cue and limit… (FR-1.5): a slider and a number field for the sound's level in the Quick changes of the place
	 * "Apply to" chose, its repeat limit (FR-6, {@link #applyLimit}) and its Cue on/off (FR-7, {@link #toggleCue}). Cue
	 * and Limit show the sound's actual state at the place, whichever set decides it. Changes apply as they are made;
	 * closing the pop-up offers one Undo for everything changed in it. An empty field clears the level. Values are
	 * checked like the entry editor's (FR-12.7).
	 */
	public void openLevel(String soundId) {
		host.closeOverlay();
		levelSound = soundId;
		levelTarget = applyTarget();
		levelBefore = ProfileManager.workspace();
		SetEntry entry = QuickActions.quickEntry(levelBefore, levelTarget.place(), soundId);
		LevelPopup popup = LevelPopup.open(host, entry == null ? SetEntry.of("new", soundId) : entry, this::applyLevel, this::finishLevel,
				QuickActions.isCueOn(levelBefore, levelTarget.place(), soundId), this::toggleCue,
				QuickActions.limitAt(levelBefore, levelTarget.place(), soundId), this::applyLimit);
		levelSlider = popup.slider;
		levelField = popup.field;
		levelCue = popup.cue;
		levelLimit = popup.limit;
	}

	/**
	 * Sets the sound's repeat limit from the pop-up in the place's Quick changes, or clears it ({@code null}); saved at
	 * once, without its own toast. Quick changes is pinned first, so its limit always wins at the place (FR-4.10). An
	 * entry cannot say "no limit", so turning it off while another set there still limits the sound shows that set's limit
	 * again and says so.
	 */
	public void applyLimit(soundsculptor.core.sets.Limit limit) {
		if (levelSound == null) {
			return;
		}
		PlaceRef where = levelTarget.place();
		String soundId = levelSound;
		EntryEdit edit = limit == null ? EntryEdit.ClearLimit.INSTANCE
				: new EntryEdit.SetLimit(limit.count(), limit.windowMs(), limit.perSource());
		QuickActionResult result = QuickActions.apply(ProfileManager.workspace(), where, soundId, edit);
		if (!result.ok()) {
			host.showToast(ProfileManager.component(result.error()), null);
			return;
		}
		if (result.changed()) {
			ProfileManager.change(current -> QuickActions.apply(current, where, soundId, edit).after());
		}
		soundsculptor.core.sets.Limit still = limit == null ? QuickActions.limitAt(ProfileManager.workspace(), where, soundId) : null;
		if (still != null && levelLimit != null) {
			levelLimit.show(still);
			host.showToast(Component.translatable("soundsculptor.sounds.limit.still", soundName(soundId)), null);
		}
	}

	/** Sets the level from the pop-up, or clears it ({@code null}); saved at once, without its own toast. */
	private void applyLevel(Integer level) {
		if (levelSound == null) {
			return;
		}
		PlaceRef where = levelTarget.place();
		String soundId = levelSound;
		EntryEdit edit = new EntryEdit.Volume(level);
		QuickActionResult result = QuickActions.apply(ProfileManager.workspace(), where, soundId, edit);
		if (!result.ok()) {
			host.showToast(ProfileManager.component(result.error()), null);
			return;
		}
		if (result.changed()) {
			ProfileManager.change(current -> QuickActions.apply(current, where, soundId, edit).after());
		}
	}

	/** The Level pop-up closed: one Undo toast for everything changed in it. */
	private void finishLevel() {
		String soundId = levelSound;
		ApplyTarget target = levelTarget;
		Workspace before = levelBefore;
		levelSound = null;
		levelTarget = null;
		levelBefore = null;
		levelSlider = null;
		levelField = null;
		levelCue = null;
		levelLimit = null;
		if (soundId == null) {
			return;
		}
		Workspace after = ProfileManager.workspace();
		if (!after.equals(before)) {
			QuickUndo.offer(host, this::refreshAfterUndo, before, after, placeKey(levelToastKey(before, after, target.place(), soundId), target),
					List.of(placeArgs(soundId, target)));
		}
		refresh();
		focusRowButton(soundId, BUTTON_LEVEL);
	}

	/** What the pop-up's Undo toast says: the level, the cue, the limit, or that several of them changed. */
	private static String levelToastKey(Workspace before, Workspace after, PlaceRef where, String soundId) {
		SetEntry was = QuickActions.quickEntry(before, where, soundId);
		SetEntry now = QuickActions.quickEntry(after, where, soundId);
		boolean levelChanged = !java.util.Objects.equals(was == null ? null : was.volume(), now == null ? null : now.volume());
		boolean limitChanged = !java.util.Objects.equals(was == null ? null : was.limit(), now == null ? null : now.limit());
		boolean cueOn = QuickActions.isCueOn(after, where, soundId);
		boolean cueChanged = QuickActions.isCueOn(before, where, soundId) != cueOn;
		if (limitChanged) {
			return levelChanged || cueChanged ? "soundsculptor.sounds.toast.settings" : "soundsculptor.sounds.toast.limit";
		}
		if (levelChanged && cueChanged) {
			return "soundsculptor.sounds.toast.level_cue";
		}
		if (cueChanged) {
			return cueOn ? "soundsculptor.sounds.toast.cue_on" : "soundsculptor.sounds.toast.cue_off";
		}
		return "soundsculptor.sounds.toast.level";
	}

	/** Add to set…: choose a library set or a new one, then this sound or one of its Like this groups (FR-1.5, FR-4.8). */
	public void openAddToSet(String soundId) {
		setScreen(new AddToSetScreen(host, soundId, soundName(soundId), this::refreshAfterUndo));
	}

	/** A toast key for Everywhere, or its "_in" form naming the zone. */
	private static String placeKey(String key, ApplyTarget target) {
		return target.isEverywhere() ? key : key + "_in";
	}

	private static String[] placeArgs(String soundId, ApplyTarget target) {
		return target.isEverywhere() ? new String[] {soundName(soundId)} : new String[] {soundName(soundId), target.zoneName()};
	}

	/** After Undo, the screen may show a newer panel (e.g. after returning from Add to set…): refresh what it shows. */
	private void refreshAfterUndo() {
		if (host instanceof SoundSculptorScreen main) {
			main.refreshLists();
		} else {
			refresh();
		}
	}

	private void focusRowButton(String soundId, int button) {
		SculptList.Row row = list == null ? null : list.findRow(soundId);
		if (row != null) {
			host.focusPath(list.revealRow(row, button));
		}
	}

	// ---- Picker mode (FR-1.7, FR-4.8) ----

	/** A picker row's buttons: the checkbox (none when the set has the sound), then Like this…. */
	private List<SculptButton> pickButtons(String soundId, boolean inSet) {
		Component likeLabel = Component.translatable("soundsculptor.picker.like_this");
		SculptButton likeThis = Tooltips.describe(SculptButton.text(likeLabel, widths().likeThis(), () -> openLikeThis(soundId)),
				Component.translatable("soundsculptor.picker.like_this.tip"));
		if (inSet) {
			return List.of(likeThis);
		}
		SculptCheckbox box = new SculptCheckbox(Component.translatable("soundsculptor.picker.pick"),
				Component.translatable("soundsculptor.picker.pick.tip"), picker.picked(soundId), () -> togglePick(soundId));
		return List.of(box, likeThis);
	}

	/** Ticks or unticks a sound; nothing for a sound the set has. Rebuilds only its row, which keeps keyboard focus. */
	public void togglePick(String soundId) {
		if (picker == null) {
			return;
		}
		picker.toggleSound(soundId);
		pickVersion++;
		refresh();
		picksChanged.run();
	}

	/**
	 * Like this… (FR-1.7, FR-4.8): the sound's groups in a pop-up, closest first, each with how many known sounds it
	 * matches and Add, or "In this set" when the set has the group. Add adds the group, with any sounds already picked,
	 * and closes the picker. Back, Esc or a click outside closes only the pop-up and returns to the row's Like this….
	 */
	public void openLikeThis(String soundId) {
		host.closeOverlay();
		likeThisSound = soundId;
		likeThisGroups = LikeThis.groups(soundId, KnownSounds.ids());
		int x = list.getX();
		int y = list.getY();
		// Over the whole list, so no row behind it shows at the side.
		int width = Math.min(list.getWidth(), host.width - x - Theme.PADDING);
		int height = Math.max(SculptList.ROW_HEIGHT + Theme.WIDGET_HEIGHT + 3 * Theme.GAP,
				host.height - SculptScreen.FOOTER_HEIGHT - Theme.PADDING - y);
		SculptButton back = SculptButton.text(Component.translatable("soundsculptor.ui.back"), host::closeOverlay);
		int listHeight = height - back.getHeight() - 3 * Theme.GAP;
		SculptList groups = new SculptList(width - 2 * Theme.GAP, listHeight,
				Component.translatable("soundsculptor.picker.like_this.list", soundName(soundId)));
		groups.setPosition(x + Theme.GAP, y + Theme.GAP);
		groups.setWidth(width - 2 * Theme.GAP);
		groups.setHeight(listHeight);
		back.setPosition(x + width - Theme.GAP - back.getWidth(), groups.getBottom() + Theme.GAP);
		likeThisList = groups;
		groups.setRows(groupRows());
		host.openOverlay(x, y, width, height, List.of(groups, back), groups, () -> {
			String sound = likeThisSound;
			likeThisList = null;
			likeThisSound = null;
			likeThisGroups = List.of();
			SculptList.Row row = list.findRow(sound);
			if (row != null) {
				focusRowButton(sound, row.buttons().size() - 1);
			}
		});
		if (!groups.children().isEmpty()) {
			groups.focusRow(host, groups.children().get(0));
		}
	}

	/**
	 * A Like this group's Add: picks the group and adds every pick, closing the picker. If the set is full nothing is
	 * added, the group is not left picked and the pop-up stays open (the picker shows the limit).
	 */
	public void addGroup(String pattern) {
		if (picker == null || likeThisList == null || picker.groupInSet(pattern)) {
			return;
		}
		boolean pickedHere = !picker.groupPicked(pattern);
		if (pickedHere) {
			picker.toggleGroup(pattern);
		}
		if (addPicks.getAsBoolean()) {
			return; // added, and the picker has closed
		}
		if (pickedHere) {
			picker.toggleGroup(pattern);
		}
		picksChanged.run();
	}

	/** "Add this group", or "Add this group and 2 picked sounds" when sounds are already picked. */
	private Component addGroupTip() {
		int sounds = picker.pickedCount();
		return sounds == 0 ? Component.translatable("soundsculptor.picker.add_group.tip")
				: sounds == 1 ? Component.translatable("soundsculptor.picker.add_group.tip.one")
				: Component.translatable("soundsculptor.picker.add_group.tip.many", sounds);
	}

	private List<SculptList.Row> groupRows() {
		List<SculptList.Row> rows = new ArrayList<>(likeThisGroups.size());
		for (LikeThis.Group group : likeThisGroups) {
			String pattern = group.pattern();
			boolean inSet = picker.groupInSet(pattern);
			String mark = inSet ? IN_SET.getString() : "";
			String matches = ProfileManager.component(group.matches()).getString();
			List<SculptList.Row.Column> columns = List.of(
					new SculptList.Row.Column(() -> pattern, 0, 0),
					new SculptList.Row.Column(() -> mark, SculptList.Row.Column.widthFor(IN_SET), 0),
					new SculptList.Row.Column(() -> matches, 0, 1));
			List<SculptButton> buttons = inSet ? List.of() : List.of(Tooltips.describe(
					SculptButton.text(Component.translatable("soundsculptor.picker.add_group"), () -> addGroup(pattern)), addGroupTip()));
			rows.add(new SculptList.Row(pattern, columns, buttons));
		}
		return rows;
	}

	// ---- Apply to ----

	/** Where quick actions put their change (FR-1.5, FR-3.7): Everywhere, or the chosen zone while the player stands in it. */
	public ApplyTarget applyTarget() {
		boolean sameWorld = state.applyWorldKey != null && state.applyWorldKey.equals(ProfileManager.key());
		return ApplyTo.select(applyChoices, sameWorld ? state.applyZoneId : null);
	}

	/** Chooses where quick actions put their change; refreshes the rows, whose Mute/Unmute follows the choice. */
	public void setApplyTarget(ApplyTarget target) {
		state.applyZoneId = target.zoneId();
		state.applyWorldKey = ProfileManager.key();
		if (applyToDropdown != null && !applyToDropdown.value().equals(target)) {
			applyToDropdown.setValue(target);
		}
		refresh();
	}

	/** The "Apply to" choices offered now. */
	public List<ApplyTarget> applyChoices() {
		return applyChoices;
	}

	/** Everywhere, then each zone the player is standing in (core {@link ApplyTo}). */
	private static List<ApplyTarget> computeApplyChoices() {
		Minecraft mc = Minecraft.getInstance();
		if (!ProfileManager.isLoaded() || mc.player == null) {
			return List.of(ApplyTarget.EVERYWHERE);
		}
		String key = ProfileManager.key() == null ? "" : ProfileManager.key();
		return ApplyTo.choices(ProfileManager.profile(), key, WorldSession.dimension(mc), WorldSession.feet(mc));
	}

	// ---- Search help ----

	/**
	 * The "?" button (FR-3.5): the search syntax examples in a pop-up list, one per row with what it means, so each is
	 * read by the narrator. Esc or Hide closes it and returns to the button.
	 */
	public void openHelp() {
		int x = list.getX();
		int y = helpButton.getBottom() + 2;
		int width = Math.min(HELP_WIDTH, host.width - x - Theme.PADDING);
		int height = Math.max(SculptList.ROW_HEIGHT + Theme.WIDGET_HEIGHT + 3 * Theme.GAP,
				host.height - SculptScreen.FOOTER_HEIGHT - Theme.PADDING - y);
		SculptButton hide = SculptButton.text(Component.translatable("soundsculptor.sounds.help.hide"), host::closeOverlay);
		int listHeight = height - hide.getHeight() - 3 * Theme.GAP;
		SculptList examples = new SculptList(width - 2 * Theme.GAP, listHeight, Component.translatable("soundsculptor.sounds.help.list"));
		examples.setPosition(x + Theme.GAP, y + Theme.GAP);
		examples.setWidth(width - 2 * Theme.GAP);
		examples.setHeight(listHeight);
		List<SculptList.Row> rows = new ArrayList<>();
		for (int i = 1; i <= HELP_LINES; i++) {
			String example = Component.translatable("soundsculptor.sounds.help." + i + ".example").getString();
			String meaning = Component.translatable("soundsculptor.sounds.help." + i + ".meaning").getString();
			rows.add(new SculptList.Row(i, List.of(new SculptList.Row.Column(() -> example, 0, 0),
					new SculptList.Row.Column(() -> meaning, 0, 1)), List.of()));
		}
		examples.setRows(rows);
		hide.setPosition(x + width - Theme.GAP - hide.getWidth(), examples.getBottom() + Theme.GAP);
		helpList = examples;
		host.openOverlay(x, y, width, height, List.of(examples, hide), examples, () -> {
			helpList = null;
			host.setFocused(helpButton);
		});
		examples.focusRow(host, rows.get(0));
	}

	public boolean isHelpShown() {
		return helpList != null;
	}

	// ---- Keys ----

	/** P toggles Pause while the list has focus and the Recently played filter is on (FR-1.3). */
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == Keys.P && picker == null && state.recentOnly && list != null && host.getFocused() == list
				&& !host.hasOverlay()) {
			setPaused(!state.paused);
			return true;
		}
		return false;
	}

	// ---- Helpers ----

	/** The sound's subtitle, or a readable name made from its ID, for toasts. */
	private static String soundName(String soundId) {
		return KnownSounds.name(soundId);
	}

	private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("soundsculptor", "icon/" + name);
	}
}
