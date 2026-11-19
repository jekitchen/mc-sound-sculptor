# Mod page text

The description for Modrinth and CurseForge, kept in the repository so it stays accurate and reviewable. Copy the part
between the lines into the project's description on each site. Keep it in step with [CHANGELOG.md](../CHANGELOG.md)
and the README's "For server admins" section.

**Summary** (Modrinth's one-line summary and CurseForge's short description, under 256 characters):

> Mute or turn down any sound, everywhere or only while you stand in zones you draw, without ever hearing more than
> vanilla allows.
> Client-only, nothing sent to servers.

---

# Sound Sculptor

Too many villagers? A noisy farm next to your base? An ambient sound that drives you up the wall? **Sound Sculptor** lets
you mute or turn down any individual sound, everywhere or only while you stand in areas you choose, and it never lets
you hear more than vanilla Minecraft would.

Press <kbd>;</kbd> in game to open it.

## What you can do

- **See what you're hearing.** The *Sounds* tab lists every sound in the game, mods and resource packs included, and
  narrows as you type: `villager`, `villager|zombie`, `villager -ambient` or `block.*.step`. Turn on *Recently played*
  to see only what you just heard, with its subtitle, how often it played, and whether it's Close, Near or Far. Mute
  it with one click.
- **Build Sound Sets.** A Sound Set is a named list of sounds to mute or turn down: one sound, or a whole family
  (`minecraft:entity.villager.*`). Pick sounds from the list, or type `villager hurt` and choose the sound or its
  family. Your sets work in every world; use one set in several places and editing it once changes them all.
- **Draw zones.** Mark two corners of your trading hall, farm or base and it becomes a zone; nudge any side in or out.
  While you stand in it, the Sound Sets you attach to it decide every sound you hear, wherever it comes from: silence
  rain, music and thunder in your base and get them back as you walk out. Outlines show where your zones are, in
  their own colors, with their names.
- **Ask why.** "Why does it sound like this?" shows where you stood, every set that touched a sound, which entry won
  and why, and lets you change it on the spot.
- **Undo** any quick change within 5 seconds, with a click or <kbd>Ctrl</kbd>+<kbd>Z</kbd>.
- **One profile per server and world**, created automatically and saved across restarts. Copy your sets from one
  server to another, or export a profile to share it.
- **Bypass** with <kbd>F10</kbd> to hear plain vanilla sound at any time, without losing a single setting.

Everything works with the keyboard alone and with the narrator, and the screens are tested at every GUI scale.

## For server admins: fair play

Sound Sculptor only changes how loud sounds are on the player's own computer. It never tells a player anything that vanilla
Minecraft's sounds and subtitles don't already tell them. This is a hard rule of the project, not a setting.

- **Nothing sent to your server.** No custom packets, no plugin channel, no client brand change: your server sees a
  vanilla client. Verify it with any packet logger.
- **Mute and volume only remove sound**, like vanilla's own volume sliders, just per sound.
- **Recently played lists only sounds the player was close enough to hear in vanilla.** Distance is Close, Near or Far,
  never blocks and never a direction, and players can hide it.
- **Zone outlines show only zones the player drew.**

Coming in 1.0, with the same rule: a volume boost that is still silent wherever vanilla is silent, on-screen cues only
where vanilla shows subtitles, and an inspector that only selects mobs in clear sight.

Questions or concerns: open an issue with "fair play" in the title at
<https://github.com/jekitchen/mc-sound-sculptor/issues>.

## Requirements

- Minecraft Java Edition **26.3**
- **Fabric Loader** 0.20.1 or newer and **Fabric API**
- Optional: **Mod Menu**, for a config button
- Client only: don't install it on a server.

## Compatibility

- Tested with Mod Menu installed and without it, and with Sodium, Iris with a shader pack, and Sound Physics
  Remastered.
- Tested on a vanilla server and in singleplayer.

## Status

This is an **alpha**: the features above are complete and tested, but expect rough edges. Planned for 1.0: volume boost
above 100%, cues for muted sounds, repeat limits and the mob inspector.

Sound Sculptor is open source under the MIT license: <https://github.com/jekitchen/mc-sound-sculptor>.

---
