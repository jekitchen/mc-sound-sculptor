# Changelog

All notable changes to Sound Sculptor. Versions follow `major.minor.patch`, with `-alpha` or `-beta` for test releases.
Each release ships one jar that runs on every supported Minecraft version.

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
singleplayer. Realms is not tested yet: a Realm's settings are kept under its server address, so if that address
changes between visits, the Realm may start with a fresh profile.

### Not yet included (planned for 1.0)
Volume boost above 100%, on-screen cues for muted sounds, repeat limits and the point-at-a-mob inspector.
