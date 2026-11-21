"""Writes Sound Sculptor's 16x16 GUI icon sprites as PNGs (white glyph, dark 1px shadow, transparent background).

Usage (from the repo root): python tools/make_icons.py src/main/resources/assets/soundsculptor/textures/gui/sprites/icon

Edit the ASCII art below to change or add an icon, then rerun. Each icon is 16x16; "#" is a white pixel.
"""
import os, struct, sys, zlib

ICONS = {
    "mute": [
        "................",
        "................",
        "......#.........",
        ".....##.........",
        "....###...#...#.",
        ".#####....##.##.",
        ".#####.....###..",
        ".#####......#...",
        ".#####.....###..",
        ".#####....##.##.",
        "....###...#...#.",
        ".....##.........",
        "......#.........",
        "................",
        "................",
        "................",
    ],
    "volume": [
        "................",
        "..........#.....",
        "......#....#....",
        ".....##..#..#...",
        "....###...#..#..",
        ".#####..#..#.#..",
        ".#####...#.#..#.",
        ".#####...#.#..#.",
        ".#####...#.#..#.",
        ".#####..#..#.#..",
        "....###...#..#..",
        ".....##..#..#...",
        "......#....#....",
        "..........#.....",
        "................",
        "................",
    ],
    "edit": [
        "................",
        "...........##...",
        "..........####..",
        ".........#.####.",
        "........###.##..",
        ".......#####....",
        "......#####.....",
        ".....#####......",
        "....#####.......",
        "...#####........",
        "..#####.........",
        "..####..........",
        "..###...........",
        "..##............",
        "................",
        "................",
    ],
    "help": [
        "................",
        ".....######.....",
        "....##....##....",
        "...##......##...",
        "...........##...",
        "..........##....",
        ".........##.....",
        "........##......",
        ".......##.......",
        ".......##.......",
        "................",
        ".......##.......",
        ".......##.......",
        "................",
        "................",
        "................",
    ],
    "close": [
        "................",
        "................",
        "...##......##...",
        "...###....###...",
        "....###..###....",
        ".....######.....",
        "......####......",
        "......####......",
        ".....######.....",
        "....###..###....",
        "...###....###...",
        "...##......##...",
        "................",
        "................",
        "................",
        "................",
    ],
}

WHITE = (255, 255, 255, 255)
SHADOW = (40, 40, 40, 255)
CLEAR = (0, 0, 0, 0)


def png(pixels, w, h):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in pixels[y * w:(y + 1) * w]) for y in range(h))
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def render(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "icons must be 16x16"
    px = [CLEAR] * 256
    for y, row in enumerate(rows):  # shadow first, offset by (1, 1)
        for x, c in enumerate(row):
            if c == "#" and x + 1 < 16 and y + 1 < 16:
                px[(y + 1) * 16 + x + 1] = SHADOW
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "#":
                px[y * 16 + x] = WHITE
    return png(px, 16, 16)


# Small white glyphs with no shadow, drawn tinted next to text (any size; "#" is a white pixel).
GLYPHS = {
    # The cue box's muted speaker (FR-7.3): 9x7, the height of a capital letter, drawn in the cue text color.
    "cue_muted": [
        "..#......",
        ".##.#...#",
        "###..#.#.",
        "###...#..",
        "###..#.#.",
        ".##.#...#",
        "..#......",
    ],
    # The boost warning (FR-5.4): 9x8, a triangle with an exclamation mark cut out, drawn in the warning color under a
    # level slider above 100%.
    "warning": [
        "....#....",
        "...###...",
        "..##.##..",
        "..##.##..",
        ".###.###.",
        ".#######.",
        "####.####",
        "#########",
    ],
}


def render_glyph(rows):
    h, w = len(rows), len(rows[0])
    assert all(len(r) == w for r in rows), "glyph rows must be the same width"
    return png([WHITE if c == "#" else CLEAR for row in rows for c in row], w, h)


out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for name, rows in ICONS.items():
    with open(os.path.join(out, name + ".png"), "wb") as f:
        f.write(render(rows))
    print("wrote", name)
for name, rows in GLYPHS.items():
    with open(os.path.join(out, name + ".png"), "wb") as f:
        f.write(render_glyph(rows))
    print("wrote", name)
