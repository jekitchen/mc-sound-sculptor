# Zones follow where you stand: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zones decide every sound by where the player's feet are, smallest zone first, decided at sound start, with looping sounds re-decided on enter/leave, edits and Bypass off.

**Architecture:** `Precedence` keeps a "where you are" list (indexes of enabled zones in this world and dimension that contain the feet block, smallest volume first, then list order), recomputed only when the feet block, world, dimension or snapshot changes. `decide(soundId, out)` walks that list per property. `PlayingSounds` tracks every started sound with its looping flag and factor; it re-decides loops when the where-you-are version or the snapshot changes, and resets everything on Bypass on. Zone mode, edges and fades are deleted from the model.

**Tech Stack:** Java 25, Fabric (MC 26.2 / 26.3), JUnit 5, Fabric client game tests.

**Spec:** `docs/PRD.md` on branch `docs/listener-zones` (PR #107): FR-8, FR-8.4, FR-8.7, FR-8.8, FR-9, FR-13.1, FR-14, 7.2, 7.4, 9.1, 11.4.

## Global Constraints

- Commit only as `jekitchen <245499875+jekitchen@users.noreply.github.com>`; never `--no-verify`.
- Hot path allocates nothing once each sound ID has been seen (PRD 7.4).
- Budgets (9.1): typical sound start under 5 µs, worst case (64 zones all containing the player) under 10 µs; where-you-are recompute under 0.1 ms; re-deciding playing loops under 0.2 ms; Bypass reset within one tick.
- Old profile fields `mode`, `edge` (zone) and `edge` (settings) are ignored on read and never written; no migration.
- Channel changes run through `ChannelHandle.execute`, never directly.
- Outside a world (no profile loaded) every sound plays as vanilla.

## Review Focus

- Player is null or not yet placed while a profile is loaded: sounds decide with Everywhere only, no exception.
- A looping sound muted at start must start silently (factor `SILENT`) and be tracked; a muted one-shot is cancelled.
- A loop started while Bypass was on must follow the profile at once when Bypass turns off.
- Vanilla recalculating a tracked sound's volume (slider change, tickable sound) keeps that sound's own factor, not a fresh decision.
- A 1-block-tall box on the floor never contains the player standing on that floor.

---

### Task 1: Remove zone mode, edges and fades from the model

**Files:**
- Delete: `src/main/java/soundsculptor/core/zones/ZoneMode.java`, `ZoneEdge.java`, `src/test/java/soundsculptor/core/zones/ZoneWeightTest.java`, `ShapeDistanceTest.java` (if it only tests fade helpers)
- Modify: `Zone.java` (drop `mode`, `edge`, `withMode`, `withEdge`, `weight`; add `containsBlock`), `Shape.java` (drop `depthInside`/`distanceOutside` if unused), `ProfileSettings.java` + `ProfileSetting.java` (drop `defaultEdge` / `DEFAULT_EDGE`), `ProfileCodec.java` (stop writing `mode`/`edge`; ignore them on read), `ZoneDraft.java` (drop MODE/EDGE/FADE), `ZoneSummary.java` (drop mode part; `location` uses `containsBlock` of the feet block), `OutlineGeometry.java` (no dashed/fade), `ApplyTo.java` (feet block), `StressProfile.java`, `ZoneOutlines.java`, `ZoneListPanel.java`, `ProfileManager.java`, `TraceEditContext.java`, every test that names them, lang `summary.mode.*`.

**Interfaces:**
- Produces: `Zone(String id, String name, boolean enabled, String worldKey, String dimension, Point anchor, Shape shape, PlaceSets sets)` (the only constructor); `boolean Zone.containsBlock(int bx, int by, int bz)`: the block's center `(b + 0.5)` is inside the shape; `boolean Zone.isActiveIn(String worldKey, String dimension)`: enabled and same world and dimension. `new Precedence(PlaceSets everywhere, List<Zone> zones, SetLibrary library)` only. `ZoneSummary.parts(Zone, String worldKey, String dimension, int bx, int by, int bz)`; `ApplyTo.choices(Profile, String worldKey, String dimension, int bx, int by, int bz)`.

- [ ] Write `ZoneTest.containsBlock`: box anchor (0.5, 64.5, 0.5) size 1×1×1 contains block (0,64,0), not (0,65,0); box from floor blocks y=64 (anchor y 64.5, height 1) does not contain feet block y=65; Grow Up (height 2, anchor y 65) does.
- [ ] Write `ProfileCodecTest`: a zone JSON with `"mode":"outside","edge":{"mode":"fade","fade":4}` loads as a plain zone with no problem reported, and saving writes neither key.
- [ ] Run, see them fail; make the model changes; fix every compile error in main and test (tests that only covered Outside/fade are deleted, not ported).
- [ ] `./gradlew test` green; commit "Zones lose Outside mode and fade edges".

### Task 2: Precedence decides by where you are

**Files:**
- Modify: `core/zones/Precedence.java`, `core/zones/SoundDecision.java` (drop `loudnessWeight`)
- Test: rewrite `PrecedenceTest`, `PrecedenceSetsTest`, `PrecedenceFullZonesTest`; delete `PrecedenceResolvedTest`; keep `PrecedenceCacheBudgetTest` passing.

**Interfaces:**
- Produces:
  - `boolean at(String worldKey, String dimension, int bx, int by, int bz)`: updates where you are; returns true and bumps `whereVersion()` only when the zone list changed. Allocation-free when the inputs did not change.
  - `void nowhere()`: no feet block known (no player): where you are is Everywhere only.
  - `long whereVersion()`, `int whereCount()`, `int whereZone(int k)` (index into `zones()`, smallest first).
  - `void decide(String soundId, SoundDecision out)`: per property, first zone in the where list whose row resolution sets it, else Everywhere.
  - `static int compareSmallestFirst(double volA, int indexA, double volB, int indexB)` for the trace.
- Removes: the positional `decide`, `decideResolved`, `resolve`, `anyZoneCouldSetLoudness`, `beats`, `isDistanceTie`, `profileEdge`.

- [ ] Tests, each with zone sets built from `Fixtures`: P1, P2, P3 (step from A into B-only part: 20% then 80%), P4, P4b, P5, P5b (swap order), P8, P13–P16, P17 (`at` inside A; decide is muted whatever the sound position, decide takes none), P18, disabled zone ignored, other dimension ignored, `nowhere()` gives Everywhere, `at` with the same block twice returns false and keeps `whereVersion`.
- [ ] Implement; the where list is an `int[]` of capacity `MAX_ZONES` filled by testing every active zone with `containsBlock` and insertion-sorting by `(volume, index)`.
- [ ] `PrecedenceCacheBudgetTest` and `HotPathBudgetTest` still pass (update calls); commit "Precedence follows where you stand".

### Task 3: Sound start decider: every sound, looping-aware mute

**Files:**
- Modify: `core/start/SoundStart.java` (drop `vanillaCovered`, add `looping`), `SoundStartDecider.java`, `SoundStartResult.java` docs, `core/trace/LastPlay.java` + `LastPlays.java` (record where you stood: `feetX/Y/Z` ints and a `known` flag, replacing the sound position)
- Test: `SoundStartDeciderTest`, `SoundStartBudgetTest`, `SoundStartDeciderMemoryTest`

**Interfaces:**
- Consumes: Task 2 `decide(soundId, out)`; the hook calls `precedence.at(...)` before `start`.
- Produces: `SoundStart.looping`, `SoundStart.feetKnown`, `feetX`, `feetY`, `feetZ` (recorded into `LastPlay`); `result.cancel()` is true for limited sounds and for muted sounds that are not looping; muted loops get `factor 0`, `cancel false`.

- [ ] Tests: muted one-shot cancels; muted loop does not cancel and has factor 0; a non-positional sound in a muting zone is muted (P11 shape: music ID, UI ID); range gate still stops limit use for out-of-range positional sounds; Bypass leaves the sound unhandled; recorded `LastPlay` carries the feet block.
- [ ] Implement; commit "Every sound in scope; muted loops start silently".

### Task 4: Playing sounds: track all, re-decide loops on events, Bypass reset

**Files:**
- Modify: `core/playing/PlayingSounds.java`, `mc/PlayingChannels.java`, `mc/SoundHook.java`, `SoundSculptorClient.java` tick wiring if needed
- Test: rewrite `PlayingSoundsTest`, `PlayingSoundsBudgetTest`

**Interfaces:**
- Produces (core):
  - `boolean start(long handle, String soundId, boolean looping, float vanillaGain, float factor)`
  - `float recalculated(long handle, float vanillaGain)`: stores the vanilla gain and returns `vanillaGain * factor` (NaN if not tracked)
  - `int update(Precedence p, long whereVersion, boolean bypassed, GainListener l)`: on Bypass on (or staying on) every sound goes to factor 1; otherwise, when `p`, `whereVersion` or the bypass state changed, every looping sound is re-decided (`factor = min(percent, 200) / 100`, mute → 0); non-looping sounds keep their factor. Reports only changed gains. Does nothing on a quiet tick.
  - `void stop(long handle)`, `void clear()`, `int size()`, `float gain(long)`.
- mc: the hook tracks every sound that starts while a profile is loaded (handled or Bypass), positional or not; `applyVolume` uses `recalculated`, and an untracked sound with a profile and no Bypass uses a fresh `decide` (table full: fail open toward the current rules). The tick reads the player's `blockPosition()` and dimension, calls `precedence.at(...)` (or `nowhere()`), drops stopped channels and calls `update`. `isVanillaCovered` is deleted.

- [ ] Tests: loop muted on entering (where version bump) and back on leaving; one-shot unchanged on where change; profile change re-decides loops only; Bypass on resets a 30% one-shot and a muted loop to vanilla; Bypass off re-decides the loop and leaves the one-shot at vanilla; recalculated keeps the factor; quiet tick reports nothing; budget: 255 tracked loops re-decided under 0.2 ms.
- [ ] Implement; commit "Loops follow you; Bypass resets playing sounds".

### Task 5: Trace: where you were

**Files:**
- Modify: `core/trace/SoundTracer.java`, `ZoneLine.java` (`zone`, `PlaceTrace sets`; drop weight and distance), `ZoneWinReason.java` (`ONLY_ZONE`, `SMALLEST`, `SAME_SIZE_LIST_ORDER`), `TraceNotice.java` (drop `VANILLA_COVERED`, `NON_POSITIONAL`; `NOT_HEARD_YET` text "Not heard yet: predicted for where you are."), `TraceText.java`, `TraceEditor`/`TraceEditContext` callers, `mc/TraceScreen.java`, lang strings
- Test: `SoundTracerTest`, `SoundTracerFullZonesTest`, `TraceTextTest`, `TraceTextLinesTest`, `TraceEditorTest`

**Interfaces:**
- Produces: `SoundTracer.trace(Precedence p, SetLibrary lib, ToIntFunction<String> placesUsing, String soundId, LastPlay play, boolean bypass)`; for a play with a known feet block it sets where you stood with `p.at(...)` on a copy-safe path (the trace builds its own `Precedence`, `TraceEditContext` already does), else uses the current where list passed by the caller via `p` as is.
- Lang: `trace.text.zones` → "You were in: %s"; `trace.text.no_zones` → "No zone: Everywhere"; `trace.text.zone` → "%1$s (%2$s)" with the shape summary; reasons "Only zone that sets it", "Smallest zone that sets it", "Same size; higher in the zone list".

- [ ] Tests: two nested zones both setting villagers: lines smallest first, winner SMALLEST; equal sizes: SAME_SIZE_LIST_ORDER; zone containing the sound but not you is absent; not heard yet uses the current where list; text renders "You were in: Pen → Base → Everywhere".
- [ ] Implement; commit "Trace shows where you were".

### Task 6: Hints, labels and the 1-tall marking hint

**Files:** `mc/ZoneMarking.java` (hint line), `mc/ZoneListPanel.java` (feet block), Sounds tab Apply to (feet block), `core/search/SoundSearch.java`, `core/editor/PatternPreview.java`, `mc/KnownSounds.java`, `mc/TraceScreen.java` (music and UI IDs are searchable again), lang (`zones.hint`, `zones.sets.tip`, new `marking.one_tall`).

- [ ] Tests: `SoundSearchTest` finds `minecraft:music.game` and `minecraft:ui.button.click`; a `ZoneMarkingHint`-level core test if the hint text is built in core, else covered by the game test.
- [ ] Implement; commit "Zones tab, search and marking follow the new zones".

### Task 7: Stress profile, game tests, changelog

**Files:** `core/stress/StressProfile.java` (64 zones all containing the player's spot), `StressProfileTest`, `HotPathBudgetTest`, game tests that build zones (`PrecedenceSetsClientGameTest`, `M8StressClientGameTest`, others found by compile errors), `CHANGELOG.md`.

- [ ] Game tests: stand in a zone that mutes villagers, villager sound outside it is muted (P17); stand outside, sound inside: audible (P18).
- [ ] `./gradlew build` and `./gradlew runClientGameTest` green on 26.3 and 26.2; commit; push; PR.
