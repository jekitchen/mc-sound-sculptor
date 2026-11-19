# Remove cylinder zones — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zones are boxes only; cylinder zones are refused in files and gone from the code, UI and tests.

**Architecture:** Follow the sphere removal (#100, commit 34abb5a): `ProfileCodec` refuses `"cylinder"` with
`ProfileText.UNSUPPORTED_SHAPE`; `Shape.Cylinder` and everything that switches on it is deleted; the zone editor's
non-box branch, shape menu and radius field go.

**Tech Stack:** Java 25, Fabric (Minecraft 26.3), JUnit 5, Fabric client game tests.

**Spec:** `docs/superpowers/specs/2026-11-18-remove-cylinders-design.md`

## Global Constraints

- Minecraft 26.3 only. Commit as `jekitchen <245499875+jekitchen@users.noreply.github.com>`; never `--no-verify`.
- Refusal text: `Zone N, field shape type: "cylinder" is not a supported shape.` (same message as sphere).
- Stress test budgets (PRD 9.1) still pass with 64 overlapping box zones.
- Pseudo-language ceiling (38) must not rise.

## Review Focus

1. **An export or import file with a cylinder zone** (set or profile import path, not only the profile loader). Expected: refused with the same message (Task 1 tests both codec paths that exist).
2. **A test that used a cylinder for a volume ordering** (smallest-first precedence). Expected: replacement boxes keep the same smaller/larger relation, so the test still checks what it checked.
3. **Stress profile still makes every zone contain the feet block** (StressProfileTest).
4. **Zone editor focus/narration after the shape menu is gone** (Accessibility game test).
5. **Leftover translation keys / docs mentioning cylinders** (LangKeysTest; grep).

---

### Task 1: Core — refuse cylinders, delete the shape

**Files:** `core/zones/Shape.java`, `core/profile/ProfileCodec.java`, `core/outline/OutlineGeometry.java`,
`core/lists/ZoneSummary.java`, `core/editor/ZoneDraft.java`, `core/editor/Draft.java` (comment), `core/stress/StressProfile.java`,
`TestEnglish.java`, and every `src/test` file using `Shape.Cylinder`.

- [ ] **Step 1: Failing test** `ProfileCodecTest.aCylinderShapeIsRefusedWithItsPlace`: `problem(zoneFile("fixed", "{\"type\": \"cylinder\", \"radius\": 8, \"height\": 4}"))` equals `Zone 2, field shape type: "cylinder" is not a supported shape.`
- [ ] **Step 2: Run** `./gradlew test --tests soundsculptor.core.profile.ProfileCodecTest` — Expected: FAIL (a cylinder loads).
- [ ] **Step 3:** In `readShape`, refuse `"cylinder"` like `"sphere"`; `choice("type", "shape type", "box")`; drop the cylinder entry from `SHAPE_FIELDS` and the writer case.
- [ ] **Step 4: Run** the test — PASS.
- [ ] **Step 5:** Delete `Shape.Cylinder`; then fix every compile error in `src/main` core (outline circles, summary, `ZoneDraft` `RADIUS`/`SHAPE`/`ShapeKind`/`setShape`, stress profile: each cylinder becomes a box of side `2r` (height as before), keeping the "contains the pen centre" reach). Remove `summary.shape.cylinder` from `TestEnglish`.
- [ ] **Step 6:** Convert every `src/test` use: a cylinder sample becomes a `Box` whose volume keeps the same order against its neighbours; tests only about cylinders (e.g. cylinder distance, cylinder outline, cylinder summary, cylinder draft fields) are deleted.
- [ ] **Step 7: Run** `./gradlew test` — Expected: all pass.
- [ ] **Step 8: Commit** `Remove cylinder zones from the core: boxes only`.

### Task 2: Zone editor, translations, game tests

**Files:** `mc/ZoneEditorScreen.java`, `lang/en_us.json`, game tests using cylinders (M7, M7c, Precedence, ImportExport, Trace, Undo, and the one-line uses).

- [ ] **Step 1:** Remove `shapeDropdown`, `radiusField`, the non-box branch and `setShape` from `ZoneEditorScreen` (the box tools are always shown); class Javadoc drops the cylinder sentence.
- [ ] **Step 2:** Remove lang keys `summary.shape.cylinder`, `zone_editor.shape*`, `zone_editor.radius*`.
- [ ] **Step 3:** Convert game tests: cylinder sample zones become boxes; checks that a cylinder keeps its shape / can become a box are deleted.
- [ ] **Step 4: Run** `./gradlew build runClientGameTest` — Expected: all pass, pseudo count ≤ 38, stress budgets pass.
- [ ] **Step 5:** `grep -rni cylinder src` returns nothing but the codec's refusal and its test.
- [ ] **Step 6: Commit** `Zone editor and game tests: boxes only`.

### Task 3: Docs (separate PR, owner approval)

- [ ] PRD: FR-8.3 boxes only; M13 drops cylinders; decision-log row 2026-11-18. CHANGELOG and MOD_PAGE: remove "cylinder zones" from planned. Branch `docs/remove-cylinders`.
