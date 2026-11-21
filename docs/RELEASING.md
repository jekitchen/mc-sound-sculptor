# Releasing Sound Sculptor

How to publish a release on Modrinth and CurseForge. Publishing is done by a maintainer with access to the projects on
both sites; the steps below prepare everything so that part is uploading files and pasting text.

## 1. Before the release

1. `main` is green in CI on every supported Minecraft version (build, client game tests and the compatibility smoke
   test).
2. The manual checks of the release checklist ([PRD 11.5](PRD.md)) are done for each Minecraft version. The rest of
   11.5 (layout at every GUI scale, narration, Mod Menu present and absent, keyboard tasks) is automated, and so is the
   zone precedence matrix (PRD 11.4, `PrecedenceMatrixClientGameTest`). CI's compatibility smoke test loads Sodium,
   Iris without a shader pack and Sound Physics Remastered, at the versions pinned in `build.gradle`; it checks the
   screen, a mute, a cue and a zone outline. The checks below are what it cannot see. Use the newest builds of the
   mods that load together on the Minecraft version, and note the versions you used. Note any problem on the mod page
   ([MOD_PAGE.md](MOD_PAGE.md), Compatibility).
   - Grayscale screenshot check: statuses, zone outlines and cues are still readable in grayscale.
   - Sodium and Iris with one popular shader pack (for example Complementary Reimagined): zone outlines and their name
     labels show, also through blocks, and the cue box shows above the subtitles. If the outlines vanish under the
     pack, note it on the mod page.
   - Sodium on its own, at its newest build: zone outlines show while you walk around.
   - Sound Physics Remastered: a muted villager is silent and shows its cue; a villager at 50% is quieter; with a
     repeat limit of 1 per 1 s, a crowd of villagers is heard at most once a second.
   - Boost with Sound Physics Remastered (FR-5.5): walk away from a villager set to 200%, then to 100%. At 200% it is
     never heard farther away than at 100%, reverb and echoes included. Repeat in a cave.
   - A vanilla server and singleplayer, with nothing installed on the server.
   - Noisy-farm stress test in game (PRD 11.2) with FPS from F3, and the 30-minute soak:
     `./gradlew runClientGameTest -Psoak=1800`.
3. `mod_version` in [`gradle.properties`](../gradle.properties) is the version to release (e.g. `0.1.0-alpha`).
4. [CHANGELOG.md](../CHANGELOG.md) has an entry for it; replace "(unreleased)" with the date.
5. [MOD_PAGE.md](MOD_PAGE.md) matches what ships.
6. The mod page screenshots in [`screenshots/`](screenshots/) show the current screens. To make them again (1920 x 1080,
   GUI scale 3, a staged trading hall and cow field): `./gradlew runClientGameTest -Pscreenshots=docs/screenshots`. This
   runs the whole game test suite as well; only `ModPageScreenshotsClientGameTest` takes the pictures.

## 2. Tag and build

Push a tag named after the version:

```sh
git tag v0.1.0-alpha
git push origin v0.1.0-alpha
```

One jar runs on every supported Minecraft version. The [release workflow](../.github/workflows/release.yml) builds it
against each version, checks that every build produced the same jar, and creates a **draft** GitHub release with that
jar attached and the changelog entry as notes. Check the draft, then publish it.

To build by hand instead, run `./gradlew build`. It puts `soundsculptor-<version>+mc26.3.jar` in `build/libs/` (ignore
the `-sources` jar). When more than one Minecraft version is supported, build against each with `-Pminecraft_version`
and `-Pfabric_api_version`, keep each jar, and compare them with `java tools/CompareJars.java <jar> <jar>`. The compare
must print `IDENTICAL`; if it lists differences, do not release: some code compiled differently per version (see
[PORTING.md](PORTING.md), "One jar for every version").

Smoke test the jar in a normal launcher (the official launcher with Fabric, or Prism) on **each** Minecraft version:
launch, mute a sound, make a zone, see its outline, Bypass with F10, and press Tab and the arrow keys on the Sound
Sculptor screen.

## 3. Modrinth

Project settings (once): client side **required**, server side **unsupported**; license MIT; source and issue links to
the GitHub repository; icon `src/main/resources/assets/soundsculptor/icon.png`; description from MOD_PAGE.md; gallery
from `docs/screenshots/` (the world view first, as the featured image).

Upload the jar once, **Versions → Create version**:

| Field | Value |
| --- | --- |
| Version number | `0.1.0-alpha+mc26.3` (the jar's version) |
| Version title | `Sound Sculptor 0.1.0-alpha` |
| Release channel | Alpha |
| Loaders | Fabric |
| Game versions | every supported Minecraft version: 26.3 and its 26.3.x releases |
| Dependencies | Fabric API: required. Mod Menu: optional |
| Changelog | the CHANGELOG.md entry |

## 4. CurseForge

Project settings (once): Java Edition mod, Fabric; license MIT; source link; icon, description and screenshots as
above.

Upload the jar once, **Upload file**:

| Field | Value |
| --- | --- |
| Display name | `Sound Sculptor 0.1.0-alpha` |
| Release type | Alpha |
| Game versions | every supported Minecraft version (26.3 and its point releases), plus **Fabric** and **Client** |
| Related projects | Fabric API: required dependency. Mod Menu: optional dependency |
| Changelog | the CHANGELOG.md entry (Markdown) |

## 5. After the release

1. Bump `mod_version` to the next version and add an "(unreleased)" entry to CHANGELOG.md.
2. Keep an eye on issues titled "fair play" (we aim to answer within 7 days).
3. New Minecraft versions follow [PRD 10.5](PRD.md): port at the first pre-release, ship within 7 days of the release.
