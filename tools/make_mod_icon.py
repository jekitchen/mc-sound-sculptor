"""Draws Sound Sculptor's mod icon: a speaker with sound waves inside a dashed zone ring, on a dark tile.

Drawn at 2048 px and scaled down, so every edge is smooth. Writes:
  src/main/resources/assets/soundsculptor/icon.png  (128 px, shown by Mod Menu)
  docs/icon-512.png                                  (512 px, for Modrinth and CurseForge)

Run from the repository root: python tools/make_mod_icon.py (needs Pillow).
"""
import math

from PIL import Image, ImageDraw, ImageFilter

S = 2048  # drawing size
C = S / 2

BG_TOP = (36, 48, 62)
BG_BOTTOM = (16, 22, 30)
EDGE = (70, 92, 116)
RING = (74, 168, 232)
SPEAKER = (238, 242, 246)
WAVE = (238, 242, 246)


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius, fill=255)
    return mask


def arc_with_round_caps(draw, centre, radius, start, end, width, fill):
    """An arc from start to end degrees (clockwise from 3 o'clock, as Pillow counts), with rounded ends."""
    cx, cy = centre
    box = (cx - radius, cy - radius, cx + radius, cy + radius)
    draw.arc(box, start, end, fill=fill, width=width)
    # Pillow's arc is drawn inward from the box: the stroke's centre line is width / 2 inside it.
    mid = radius - width / 2
    for angle in (start, end):
        a = math.radians(angle)
        x, y = cx + mid * math.cos(a), cy + mid * math.sin(a)
        r = width / 2
        draw.ellipse((x - r, y - r, x + r, y + r), fill=fill)


def draw_icon():
    tile_radius = S * 0.2
    # Background: a vertical gradient in a rounded tile, with a thin lighter edge.
    gradient = Image.new("RGBA", (S, S))
    gd = ImageDraw.Draw(gradient)
    for y in range(S):
        gd.line((0, y, S, y), fill=lerp(BG_TOP, BG_BOTTOM, y / (S - 1)) + (255,))
    icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    icon.paste(gradient, (0, 0), rounded_mask(S, tile_radius))
    d = ImageDraw.Draw(icon)
    inset = S * 0.012
    d.rounded_rectangle((inset, inset, S - 1 - inset, S - 1 - inset), tile_radius - inset, outline=EDGE + (255,), width=round(S * 0.012))

    # The zone: a dashed ring with a soft glow behind it.
    ring_radius = S * 0.4
    ring_width = round(S * 0.045)
    dashes = 14
    # The visible gap between two dashes, as a share of the icon's width; the rounded ends reach into the gap, so the arc
    # leaves room for them too.
    visible_gap = S * 0.0387
    mid_radius = ring_radius - ring_width / 2
    gap = math.degrees((visible_gap + ring_width) / mid_radius)
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gl = ImageDraw.Draw(glow)
    for i in range(dashes):
        start = i * 360 / dashes - 90 + gap / 2
        arc_with_round_caps(gl, (C, C), ring_radius, start, start + 360 / dashes - gap, ring_width, RING + (150,))
    icon.alpha_composite(glow.filter(ImageFilter.GaussianBlur(S * 0.02)))
    for i in range(dashes):
        start = i * 360 / dashes - 90 + gap / 2
        arc_with_round_caps(d, (C, C), ring_radius, start, start + 360 / dashes - gap, ring_width, RING + (255,))

    # The speaker: a box and a cone drawn as one shape, whose corners are then rounded together (blur, then threshold),
    # placed so that it and its waves sit in the middle of the ring.
    sx = C - S * 0.07
    box_w, box_h = S * 0.1, S * 0.17
    cone_w, cone_h = S * 0.15, S * 0.37
    left = sx - box_w - cone_w / 2
    cone_left = left + box_w
    shape = Image.new("L", (S, S), 0)
    sd = ImageDraw.Draw(shape)
    sd.rectangle((left, C - box_h / 2, cone_left + S * 0.01, C + box_h / 2), fill=255)
    sd.polygon([
        (cone_left, C - box_h / 2),
        (cone_left + cone_w, C - cone_h / 2),
        (cone_left + cone_w, C + cone_h / 2),
        (cone_left, C + box_h / 2),
    ], fill=255)
    shape = shape.filter(ImageFilter.GaussianBlur(S * 0.012)).point(lambda v: 255 if v >= 128 else 0)
    # Soften the threshold's stair steps a little; the final scale-down smooths the rest.
    shape = shape.filter(ImageFilter.GaussianBlur(1.5))
    icon.paste(Image.new("RGBA", (S, S), SPEAKER + (255,)), (0, 0), shape)
    d = ImageDraw.Draw(icon)

    # Sound waves: three arcs, fading outward.
    wave_centre = (cone_left + cone_w - S * 0.02, C)
    wave_width = round(S * 0.042)
    for i, (radius, alpha, span) in enumerate(((S * 0.1, 255, 46), (S * 0.17, 205, 44), (S * 0.24, 150, 42))):
        arc_with_round_caps(d, wave_centre, radius + wave_width / 2, -span, span, wave_width, WAVE + (alpha,))
    return icon


def main():
    icon = draw_icon()
    icon.resize((512, 512), Image.LANCZOS).save("docs/icon-512.png", optimize=True)
    icon.resize((128, 128), Image.LANCZOS).save("src/main/resources/assets/soundsculptor/icon.png", optimize=True)
    print("wrote docs/icon-512.png and src/main/resources/assets/soundsculptor/icon.png")


if __name__ == "__main__":
    main()
