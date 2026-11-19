# Sounds list: hold while pointing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** While the mouse is over the Sounds list (tab and picker), rows keep their place: none move, appear or
disappear, while the values they show keep updating; the count line says "Held".

**Architecture:** A pure core rule (`HoldOrder`) merges a fresh refresh into the rows on screen by key: shown rows keep
their order and are swapped for their fresh copy, rows that left stay, new rows wait. `SoundsPanel.refresh()` applies it
when the list is hovered and the change did not come from the player's own search, filter, sort or Apply to choice.

**Tech Stack:** Java 25, Fabric (Minecraft 26.3), JUnit 5 for core, Fabric client game tests.

**Spec:** `docs/superpowers/specs/2026-11-18-sounds-hold-while-pointing-design.md`

## Global Constraints

- Minecraft 26.3 only; one jar (`soundsculptor-<version>+mc26.3.jar`).
- Commit as `jekitchen <245499875+jekitchen@users.noreply.github.com>` only; never `--no-verify`. Worktree under
  `C:\Claude\Projects\mc-audio-zone\.worktrees\<branch>`.
- The list refreshes at most 4 times a second (FR-1.6, `REFRESH_TICKS = 5`); leaving the list catches up on the next one.
- Count text: "42 sounds · Held" and "42 sounds · Held · 3 new" (English, `en_us.json`).
- Pause and Sort are unchanged; Pause still freezes everything.
- The PRD (FR-1.3) wording goes in its own docs PR for owner approval, not with the code.

## Review Focus

1. **The game-test cursor sits over the list by default.** If it does, every other Sounds/picker game test gets a held
   list and breaks. Expected: tests that do not ask for a hold never get one (Task 2, Step 1 checks; Step 6 parks the
   cursor if needed).
2. **The list is empty when the pointer enters it.** Holding an empty list would hide the first sounds behind "No sounds
   played recently". Expected: an empty list is never held (Task 1 test `emptyShownListIsNotHeld`).
3. **Pointer leaves but nothing new has played since.** `refresh()` returns early when the log generation is unchanged,
   which would leave the held order on screen. Expected: the first refresh after the hold ends rebuilds the order (Task 2
   game test, last check).
4. **Row action while held** (Mute clears the row cache, so every row is rebuilt). Expected: rows are rebuilt in place,
   nothing joins or moves (Task 2 game test, Mute step).
5. **Typing a search with the pointer resting over the list.** Expected: results update at once, not held (Task 2 game
   test, search step).

---

### Task 1: Core hold rule

**Files:**
- Create: `src/main/java/soundsculptor/core/browser/HoldOrder.java`
- Test: `src/test/java/soundsculptor/core/browser/HoldOrderTest.java`

**Interfaces:**
- Produces:
  - `public static <T> HoldOrder.Held<T> HoldOrder.hold(List<T> shown, List<T> fresh, Function<T, ?> key)`
  - `public record Held<T>(List<T> rows, int waiting)` (nested in `HoldOrder`)
  - Rule: if `shown` is empty, return `(fresh, 0)`. Otherwise `rows` = for each shown row in order, the fresh row with
    the same key if there is one, else the shown row itself; `waiting` = number of fresh rows whose key is not shown.
    O(n) with a `HashMap` of fresh rows by key.

- [ ] **Step 1: Write the failing tests** (`HoldOrderTest`, rows as small records `R(String key, int value)`, key `R::key`)
  - `keepsShownOrderWhenFreshReorders`: shown [a1, b1], fresh [b2, a2] → rows [a2, b2], waiting 0.
  - `swapsInFreshCopiesSoValuesUpdate`: shown [a1], fresh [a5] → `rows.get(0).value() == 5`.
  - `keepsRowsThatLeft`: shown [a1, b1], fresh [b2] → rows [a1, b2], waiting 0.
  - `countsNewRowsAsWaitingWithoutShowingThem`: shown [a1], fresh [c1, a2, d1] → rows [a2], waiting 2.
  - `emptyShownListIsNotHeld`: shown [], fresh [a1, b1] → rows [a1, b1], waiting 0.
- [ ] **Step 2: Run** `./gradlew test --tests soundsculptor.core.browser.HoldOrderTest` — Expected: compile failure (no `HoldOrder`).
- [ ] **Step 3: Implement `HoldOrder`** (final class, private constructor, Javadoc citing FR-1.3 and the spec).
- [ ] **Step 4: Run** the same command — Expected: 5 tests PASS.
- [ ] **Step 5: Commit** `Sounds list: core rule for holding rows in place`.

### Task 2: Hold the Sounds list while the pointer is over it

**Files:**
- Modify: `src/main/java/soundsculptor/mc/SoundsPanel.java` (`refresh()` ~403-462, count label ~252, class Javadoc)
- Modify: `src/main/resources/assets/soundsculptor/lang/en_us.json` (next to `soundsculptor.sounds.count`)
- Test: `src/gametest/java/soundsculptor/gametest/SoundsTabClientGameTest.java` (new `holdWhilePointing`)
- Maybe modify: `src/gametest/java/soundsculptor/gametest/TestWorlds.java` (Step 6)

**Interfaces:**
- Consumes: `HoldOrder.hold(...)`, `HoldOrder.Held<T>` from Task 1, with `SculptList.Row::key`.
- Produces (public, for tests): `public boolean isHeld()` and `public int waiting()` on `SoundsPanel`; a package-private
  test seam `BooleanSupplier pointerOverList` on `SoundsPanel` only if Step 1 shows the cursor cannot be moved in a game test.

- [ ] **Step 1: Find out how the game-test cursor behaves.** In a scratch run of `SoundsTabClientGameTest`, print
  `panel.list.isHovered()` right after `open(...)`, then call `context.getInput().setCursorPos(x, y)` with the list's
  centre converted to window pixels (`* mc.getWindow().getGuiScale()`), wait 2 ticks and print it again. Record: does
  `isHovered()` start false, and does `setCursorPos` make it true? (`isHovered` is set when the widget is drawn, so it
  needs a frame.) If `setCursorPos` does not work, use the `pointerOverList` seam (default `() -> list.isHovered()`).
- [ ] **Step 2: Write the failing game test `holdWhilePointing`** (add to `runTest` after `idleRefreshRebuildsNothing`).
  Filter on, Most recent. Background music may add rows, so check only the relative order of the test's own IDs:
  1. Play `ZOMBIE`, then `VILLAGER` (helper `play`). Order: VILLAGER before ZOMBIE.
  2. Pointer over the list; wait `SETTLE`. Check `isHeld()`, and the count label text ends with " · Held".
  3. Play `ZOMBIE` again and `COW` (new); wait `SETTLE`. Check: VILLAGER still before ZOMBIE; `findRow(COW) == null`;
     `waiting() >= 1`; the label contains " · Held · " and " new"; the ZOMBIE row shows "×2".
  4. Type `entity` in the search field (pointer still over the list); wait 6 ticks. Check COW's row is listed (a search
     is not held). Clear the search; wait 6 ticks.
  5. Pointer still over the list: press Mute on the VILLAGER row (helper `press`). Check the order of the rows shown
     before the press is unchanged (compare key lists) and no new key joined. Then press Unmute to put it back.
  6. Play ZOMBIE again, then pointer off the list (cursor to 0,0); wait `SETTLE`, playing nothing. Check `!isHeld()`,
     ZOMBIE before VILLAGER (it played last), and the label has no "Held".
- [ ] **Step 3: Run** `./gradlew runClientGameTest` — Expected: FAIL at step 2 (`isHeld` missing or false).
- [ ] **Step 4: Implement the hold in `SoundsPanel`.**
  - `holding` = pointer over list && `!list.children().isEmpty()`. Paused lists do not refresh at all, so nothing changes there.
  - A refresh is **not held** when `query`, `state.recentOnly`, `state.sort` or the Apply to target differ from the
    `shown*` values (compare before they are updated): those are the player's own changes.
  - Add `shownHeld` to the early-return condition, so the refresh after the hold ends always rebuilds the order.
  - After building `rows`, when held: `Held<Row> h = HoldOrder.hold(list.children(), rows, Row::key)`; show `h.rows()`,
    set `waiting = h.waiting()`, `count = h.rows().size()`. Otherwise `waiting = 0`.
  - `isHeld()` returns `shownHeld && !(state.recentOnly && state.paused)`.
  - Count label: when `isHeld()`, `soundsculptor.sounds.held` ("%s · Held") or, with `waiting > 0`,
    `soundsculptor.sounds.held.new` ("%s · Held · %s new"), the first argument being the existing count component.
  - Class Javadoc: one paragraph on the hold (FR-1.3).
- [ ] **Step 5: Run** `./gradlew build runClientGameTest` — Expected: `holdWhilePointing` passes, all other game tests
  and unit tests (`LangKeysTest`, `PseudoLocaleTest`) pass; the pseudo-language count stays ≤ 38.
- [ ] **Step 6: If Step 1 found the cursor starting over the list**, park it at (0, 0) in `TestWorlds.create` (or the
  shared open helper) and rerun Step 5. Otherwise skip.
- [ ] **Step 7: Commit** `Sounds list holds its rows while the pointer is over it`.

### Task 3: PRD wording (separate docs PR, owner approval)

**Files:**
- Modify: `docs/PRD.md` (FR-1.3, line ~128; decision log, new row dated 2026-11-18)

- [ ] **Step 1:** Append to FR-1.3: "While the mouse pointer is over the list, the list is held: rows do not move, appear
  or disappear, but the values they show keep updating, and the result count says Held (with how many new sounds are
  waiting). Leaving the list catches up. The player's own search, filter, sort, Apply to and row actions are not held.
  The same applies in the sound picker."
- [ ] **Step 2:** Decision-log row: "2026-11-18 | The Sounds list holds while the mouse is over it (owner: rows jumping
  to the top made them hard to click; Pause had to be remembered) | Project owner".
- [ ] **Step 3: Commit** on its own branch `docs/prd-hold-while-pointing` and open a PR for owner approval.
