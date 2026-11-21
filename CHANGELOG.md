# Changelog

All notable changes to Sound Sculptor. Versions follow `major.minor.patch`, with `-alpha` or `-beta` for test releases.
Each release ships one jar that runs on every supported Minecraft version.

## 1.0.0 (in progress)

Work toward 1.0, after the alpha. Not released yet.

### Features
- **Cues for muted sounds:** when a muted sound plays close enough that vanilla would show its subtitle, a line such as
  "(muted) Explosion" appears in its own box just above the subtitles, bottom right, with vanilla's `<` or `>` arrow.
  Lines are in italics, start with "(muted)" and a muted-speaker icon, merge repeats ("×4") and stop at 5. Like
  subtitles, each line follows its latest play in range, hides while you are out of range, and fades over 3 seconds
  times vanilla's Notification Time. It works with vanilla Subtitles on or off. A play dropped by a repeat limit never
  shows a cue.
- **Muted sounds leave vanilla's subtitle box:** a sound Sound Sculptor mutes (or sets to 0%) no longer shows a vanilla
  subtitle; its cue is the only note, and with Cue off nothing shows. Vanilla's own volume sliders work as before.
- **Cue setting on entries:** the entry editor has Cue (Unchanged, On, Off) under Mute. On the Sounds tab, a row's
  Level… button is now **Level and cue…**, and its pop-up has a Cue toggle. Every entry line in "Why does it sound like
  this?" has a Cue button. A new mute gets Cue on, unless you change that in Settings.
- **Settings › General:** Show muted-sound cues (also an optional hotkey in Controls, unbound by default), Cue sounds
  without subtitles (shows the sound's ID instead) and New mutes get Cue (per world or server).
- **Settings › Accessibility** (new page): Cue color (six named colors; the default is light blue-gray), Cue text size
  (100 to 200%) and the cue box position (left/right and up/down, with a button to put it back). The box always stays on
  screen.
- A quick Cue change never turns a switched-off Quick changes entry back on.
- **Repeat limits:** an entry can cap how often its sounds start, from 1 to 100 plays per 0.05 to 10 seconds, for
  example "3 per 1 s" for villager mumbles. Plays over the limit are dropped. The count is shared by every matching
  sound, or with **Per source** kept per block, so each villager can still be heard. Each place counts on its own, also
  for a set attached in several places. A looping sound counts once, when it starts, and is never cut off. Each
  per-source entry remembers up to 1,024 positions, and a busy entry never pushes out another's.
- **Limit in the entry editor:** a new row under Level: Limit (Off by default), the count, "per", the window in seconds
  and Per source, with a reset button. Cue moved beside Level to make room.
- **Limited on the Sounds tab:** a row whose plays were dropped shows "⏸ Limited ×12" (the dropped count over the last 3
  minutes; the play count counts only plays that started). Its **Level, cue and limit…** pop-up (was Level and cue…)
  shows the sound's limit at the place and changes it in Quick changes. A typed count or window takes effect on Enter,
  when you leave the field or when the pop-up closes; an empty or wrong value shows its error and changes nothing.
- **Limit in "Why does it sound like this?":** the result says "Repeat limit: at most 3 per 1 s, shared" and whether
  the last play was Limited. Each entry line's Level… panel has the entry's limit too.
- **Settings › Diagnostics:** "Repeat limits: 90 played, 510 dropped. Most in one window: 3 (limit 3 per 1 s)", for the
  three limits that came closest to their count, also in Copy report. Limits count afresh when you join another world
  or server.
- **Volume boost to 200%:** every level control (the entry editor, the Sounds row's pop-up and the Level… panel in "Why
  does it sound like this?") now goes from 0 to 200%. Above 100% a warning icon and "May distort. Protect your
  hearing." show under the slider, and the narrator reads it. The final loudness never goes above twice vanilla's full
  volume, and Master at 0% still silences everything.
- **Boost never adds range:** a boosted sound is louder only where vanilla already plays it. It fades to silence at
  the same distance as in vanilla, and subtitles, cues and the Sounds list stop at the same distance too.
- **Asked once per world:** the first time a change adds a level above 100% to a world or server (setting one or
  raising one, attaching a set that has one, importing or copying one in), a dialog explains distortion and hearing
  safety. Allow boost goes on, and is saved only with that change; Cancel changes nothing. Lowering a boost, or any
  other change, never asks. A shared set made louder in another world plays at 100% here until you allow boost here:
  the Sound Sculptor screen asks when it next opens.
- **Audio devices that cannot boost:** if your audio device does not accept a gain above 1.0, levels above 100% play
  at 100%, and the level controls say so.
- "Why does it sound like this?" says "Above 100%: boost applied." or, when boost is off, that it plays at 100%.

### Tests and compatibility
- **Zone precedence matrix:** every case of PRD 11.4 (P1 to P20) runs as one client game test,
  `PrecedenceMatrixClientGameTest`. Each case checks the level, mute and repeat limit, the zone named for the sound and
  the deciding place, set and entry in "Why does it sound like this?", and prints a `MATRIX` line.
- **Compatibility smoke test:** `./gradlew runClientGameTest -Pcompat=true` starts the game with Sodium, Iris (no
  shader pack) and Sound Physics Remastered, pinned to their 26.3 builds. It opens the screen, mutes a sound from the
  Sounds tab, checks its cue and a zone outline in a screenshot, and fails on any error from Sound Sculptor in the log.
  CI runs it on every push.
- `-Pgametests=<name>,<name>` runs only the named client game tests.
- `./gradlew build` checks that the jar stays under 1 MB and holds only Sound Sculptor's own files.
- The mod's description in `fabric.mod.json` now says it can boost and rate-limit sounds too.

## 0.1.0-alpha (unreleased)

First public alpha, for Minecraft Java Edition 26.3 with Fabric: `soundsculptor-<version>+mc26.3.jar`.

### Features
- **Sounds tab:** every sound the game knows, mods and resource packs included, narrowing as you type: plain words, `|`
  for either, `-` to exclude, `"phrases"` and `*` wildcards (`villager -ambient`, `block.*.step`). Turn on **Recently
  played** to see only what you heard in the last 3 minutes, with its subtitle (or a readable name), category, play
  count, how long ago ("<1m ago", "2m ago"; hover for the exact time), a Close/Near/Far distance band (can be hidden)
  and its status. The list holds still while you point at it; Pause and Sort. Every row has Mute, Level…, Edit entry,
  **Add to set…** and Why?. Press <kbd>;</kbd> to open (rebindable).
- **Sound Sets:** a Sound Set is a named list of entries, each for one sound or a group of sounds
  (`minecraft:entity.villager.*`), that mutes them or sets their level (0 to 100%). Sets live in a library on the
  **Sound Sets** tab (New, Duplicate, Rename, Delete) and work in every world. Attach sets to **Everywhere** or to any
  zone, as many as you like and in the order you choose: the higher set wins where two disagree, and inside a set the
  more specific entry wins. A set attached in several places is linked: edit it once and every place changes. The set
  editor's **Attached to** panel shows where a set is used, and Save says which places it will change.
- **Building sets:** **Add sounds…** in the set editor opens the same Sounds list with a checkbox on each row. **Like
  this** offers a sound's groups, closest first, each with how many sounds it matches and its own Add. Type plain words
  in an entry's pattern field (`villager hurt`) to find a sound, then pick **This sound** or **Like this**. The entry
  editor previews the sounds a pattern matches.
- **Quick changes:** Mute, Level… and Edit entry on the Sounds tab go into the place's own **Quick changes**, a set that
  belongs to Everywhere or to one zone and always comes first there. **Apply to** chooses the place: Everywhere, or a
  zone you are standing in. Save Quick changes to the library to reuse them.
- **Zones:** boxes you draw by marking two corners in the world, then fine-tune side by side (Grow, Shrink), with the
  size shown while you mark. While your feet are in a zone, its Sound Sets decide how every sound you hear plays,
  wherever the sound comes from: mute rain, music and thunder in your base, and they come back as you walk out. Where
  zones overlap, the smallest one you stand in wins (equal sizes: the one higher in the list); Everywhere applies where
  no zone does. Outlines in the zone's color with its name, shown always, only while editing, or never.
- **How sounds are decided:** a sound is decided when it starts, by where you stand then. Looping sounds (a minecart
  rolling, bees, biome ambience) follow you in and out of zones and follow your edits at once. Music, UI clicks and
  Voice/Speech sounds can be changed too; vanilla's sliders still apply first.
- **"Why does it sound like this?"** For any sound: where you stood ("You were in: Pen → Base → Everywhere"), the sets
  with a matching entry, which entry won and why, how many places use a linked set, and the final volume. Every entry
  line has On/Off, Mute and Level, and every zone line has On/Off; changes save at once and the trace updates
  ("Predicted for next play"). **Add to Quick changes** lifts a library set's mute in one place without changing the
  set. Copy as text.
- **Undo:** quick actions, set and zone list actions, and edits from "Why?" apply at once and show a 5-second toast with
  **Undo** (<kbd>Ctrl</kbd>+<kbd>Z</kbd>, <kbd>Cmd</kbd>+<kbd>Z</kbd> on Mac).
- **Profiles:** one per server and world, created on first join and kept across restarts. The **Profiles** tab lists
  them with Sound Set and zone counts and when you last joined. Copy another profile's Sound Sets on Everywhere and its
  settings into this world (zones are never copied), rename, reset or delete a profile. In singleplayer, "Give this copy
  its own profile" for a copied world. Outside a world nothing changes.
- **Export and import:** Export saves a profile, with its zones and the Sound Sets it uses, to
  `config/soundsculptor/exports/`. Import a file by dropping it on the Profiles tab or from
  `config/soundsculptor/imports/`. A dialog says what will change first; imported sets join your library, asking for a
  new name when one is taken, and zones made for another server are skipped. A broken file changes nothing.
- **Bypass** (<kbd>F10</kbd>) puts every sound, playing ones included, back to vanilla at once. A broken setting or an
  error never stops a sound.
- **Diagnostics:** sounds per second, time per sound, cache hit rate, memory, and Copy report.
- **Mod Menu** (optional) opens the Sound Sculptor screen.
- Keyboard-only use, narrator support, and layouts checked at GUI scales 1 to 4, down to 854 × 480.

### Fair play
Sound Sculptor only changes how loud sounds are on your own computer, and never shows more than vanilla sounds and subtitles
do. It sends nothing to servers. See "For server admins" in the README.

### For testers of earlier builds
- The mod was called Audio Zone during development. Settings now live in `config/soundsculptor/`.
- Profiles from earlier test builds can't be read; they are set aside with a message.
- Rules became Sound Sets, and Recently Played and Search became one Sounds tab.
- Minecraft 26.2 is no longer supported; earlier test builds ran on 26.2 too.
- Zones follow where you stand instead of where a sound comes from. Outside mode and fade edges are gone (zones that
  used them load without them), and so are personal bubbles.

### Tested with
Mod Menu (present and absent), Sodium, Iris with a shader pack and Sound Physics Remastered; a vanilla server and
singleplayer.

### Not yet included (planned for 1.0)
Volume boost above 100%, on-screen cues for muted sounds, repeat limits and the point-at-a-mob inspector.
