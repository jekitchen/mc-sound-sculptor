# Remove cylinder zones: boxes only (short design)

**Why.** Spheres and personal bubbles were dropped before release (owner decision 2026-11-13, #100). Cylinders were
kept as the round shape planned for 1.0 (M13): the UI could not make one, but the core, file format, outline drawing,
zone editor (for an existing cylinder) and tests still had them, and the stress test's 64 overlapping cylinders showed
up in the game-test windows. The owner decided (2026-11-18) that zones are boxes only: drawn by marking two corners,
resized side by side, as today. Nothing else in terms of shapes.

**Approaches.** (1) Remove cylinders the way spheres were removed (#100): refuse them in files, delete the code (picked).
(2) Keep the code but hide it: leaves dead code and test noise. (3) Convert old cylinders to boxes on load: no one has
cylinder zones (the UI never made them), so conversion code would only add risk.

**Design.**
- `Shape` keeps its type with one record, `Box` (Claude's call: less churn than renaming it everywhere).
- A profile or export file with a `"cylinder"` shape is refused with the unsupported-shape message spheres already get
  ("Zone 2, field shape type: "cylinder" is not a supported shape.").
- Removed: `Shape.Cylinder`, its file reading/writing, outline drawing (circles), zone-list summary text, the zone
  editor's shape menu and radius field, `ZoneDraft`'s shape and radius fields, and their translation strings.
- The stress profile builds its 64 overlapping zones from boxes of varied sizes, keeping the same overlap.
- Tests that use cylinders as sample zones use boxes; tests only about cylinders are deleted.
- Docs (separate PR, owner approval): PRD FR-8.3, M13 and the decision log; CHANGELOG and MOD_PAGE no longer list
  cylinder zones as coming.
