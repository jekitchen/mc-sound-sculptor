# Picker: Like this… adds in one step — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the sound picker's Like this… pop-up, each group has an Add button that adds it (with any sounds already
picked) and closes the picker; Done becomes Back; the screen behind every pop-up is dimmed.

**Architecture:** `SoundsPanel.groupRows()` builds an Add button per group instead of a checkbox; Add picks the
group and calls a new `addPicks` callback the picker screen passes in (its existing `add()`), undoing the pick if the set
is full. `SculptScreen.drawLayers` fills the screen with a translucent colour before drawing the overlay panel.

**Tech Stack:** Java 25, Fabric (Minecraft 26.3), Fabric client game tests.

**Spec:** `docs/superpowers/specs/2026-11-18-like-this-adds-in-one-step-design.md`

## Global Constraints

- Minecraft 26.3 only. Commit as `jekitchen <245499875+jekitchen@users.noreply.github.com>`; never `--no-verify`.
- Picker Add semantics are unchanged: one entry per pick not yet in the set, no settings; nothing saved until the set
  editor's Save.
- Tooltip text: "Add this group" / "Add this group and %s picked sounds" ("1 picked sound" for one).
- The pseudo-language ceiling (38) must not rise.
- PRD wording goes in its own docs PR for owner approval.

## Review Focus

1. **Set full when a group's Add is pressed.** Expected: toast, pop-up stays open, the group is not left picked (Task 2 test).
2. **Keyboard: Enter/Space on a group row's Add.** Expected: adds, like a click (Task 2 test presses via the focused
   button; the accessibility test still finds every control labelled).
3. **A group already in the set** keeps "✔ In this set" and no button (existing check kept).
4. **Back / Esc / click outside** close only the pop-up and return focus to the row's Like this… (Task 2 test).
5. **Dim colour hides text behind the pop-up too much or too little** (Task 1: check a screenshot by eye).

---

### Task 1: Dim the screen behind pop-ups

**Files:** Modify `src/main/java/soundsculptor/mc/ui/Theme.java` (add `SCRIM = 0x99000000`),
`src/main/java/soundsculptor/mc/ui/SculptScreen.java` (`drawLayers`, ~364).

- [ ] **Step 1:** In `drawLayers`, when `overlayOpen`, fill `0, 0, width, height` with `Theme.SCRIM` before the
  overlay's border and panel. (The toast drawn before it is dimmed too; a toast's Undo is not reachable under a pop-up anyway.)
- [ ] **Step 2: Verify** with `./gradlew runClientGameTest`: the existing `picker-like-this` and `sounds-level-popup`
  screenshots (under `build/run/clientGameTest/screenshots/`) show the screen behind the pop-up darkened and the pop-up
  readable. No automated test (drawing only): ledger a ruling.
- [ ] **Step 3: Commit** `Dim the screen behind pop-ups`.

### Task 2: Like this… groups add in one step

**Files:**
- Modify: `src/main/java/soundsculptor/mc/SoundsPanel.java` (`picker(...)`, constructor, `openLikeThis`, `groupRows`, `toggleGroup` → `addGroup`)
- Modify: `src/main/java/soundsculptor/mc/SoundPickerScreen.java` (pass `this::add`; `add()` returns `boolean` added)
- Modify: `src/main/resources/assets/soundsculptor/lang/en_us.json`
- Test: `src/gametest/java/soundsculptor/gametest/PickerClientGameTest.java` (`likeThisAddsGroupEntry`, helper `groupCheckbox` → `groupAdd`), `AccessibilityClientGameTest.java` (comment/text at ~221 only if wording changes)

**Interfaces:**
- `public static SoundsPanel picker(SculptScreen host, PickerModel picker, Runnable picksChanged, BooleanSupplier addPicks)` — `addPicks` adds every pick to the set and closes the picker; returns false if nothing was added (set full).
- `public void addGroup(String pattern)` on `SoundsPanel` (replaces `toggleGroup`): picks the group if not picked, calls `addPicks`; if it returns false, unpicks it and leaves the pop-up open.
- `SoundPickerScreen.add()` becomes `public boolean add()` (true when it added and closed).

- [ ] **Step 1: Update the game test first.** In `likeThisAddsGroupEntry`:
  - Replace the checkbox steps with: tick the `COW` sound row first (`checkbox(mc, COW).onPress(null)`), open Like this…,
    check the group row's only button reads "Add" and its tooltip is "Add this group and 1 picked sound"; press it;
    `waitForScreen(SetEditorScreen.class)`; check the draft has two entries, the cow sound and `COW_GROUP`, no settings.
  - Back: reopen the picker, open Like this…, check the pop-up's last overlay widget reads "Back"; press it; check no
    overlay and `likeThisButton(mc, COW).isFocused()`.
  - Full set: put a set at its entry limit (use the limit the existing full-set test uses, or `SoundSet` limit constant),
    open the picker on it, Like this…, press a group's Add: check a toast shows, `hasOverlay()` is still true, and
    `!picker().groupPicked(group)`.
  - Keep the "✔ In this set" check.
- [ ] **Step 2: Run** `./gradlew runClientGameTest` — Expected: compile error or FAIL at the "Add" button check.
- [ ] **Step 3: Implement.** Group row button: `SculptButton.text("soundsculptor.picker.add_group" = "Add", ...)` with the
  tooltip per Global Constraints (pick count from `picker.pickedCount()`, computed when the pop-up opens). Pop-up button
  `soundsculptor.ui.back` = "Back" (add if missing). `like_this.tip`: "Add a group of sounds like this one, such as every
  villager sound". Remove `pick_group.tip` if unused (LangKeysTest will say).
- [ ] **Step 4: Run** `./gradlew build runClientGameTest` — Expected: all pass, pseudo count ≤ 38.
- [ ] **Step 5: Commit** `Picker: Like this… adds a group in one step`.

### Task 3: PRD wording (separate docs PR)

- [ ] FR-1.7: "plus **Like this…**, which lists the sound's groups, each with **Add**: it adds that group, and any sounds
  already picked, and closes the picker." FR-4.8 unchanged apart from pointing at FR-1.7 if needed. Decision-log row
  2026-11-18 (owner: one group is the usual case; three live-looking buttons confused). Branch
  `docs/prd-like-this-one-step`, PR for owner approval.
