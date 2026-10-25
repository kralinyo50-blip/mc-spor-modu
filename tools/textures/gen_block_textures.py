#!/usr/bin/env python3
"""Procedural 16x16 pixel-art generator for every McSport block texture.

Deterministic (per-texture seed), dependency: Pillow only. Output paths mirror
the resource pack layout so models/blockstates stay untouched. RGB files keep
an opaque mode; net/lamp/trophy/roof/wall keep their alpha channel.
"""
import os
import random

from PIL import Image

BLOCK_DIR = os.path.join(
    os.path.dirname(__file__), "..", "..",
    "src/main/resources/assets/mcsport/textures/block")


def canvas(rgba=False):
    return Image.new("RGBA" if rgba else "RGB", (16, 16), (0, 0, 0, 0) if rgba else (0, 0, 0))


def put(img, x, y, color, alpha=255):
    x %= 16
    y %= 16
    if img.mode == "RGBA":
        img.putpixel((x, y), (color[0], color[1], color[2], alpha))
    else:
        img.putpixel((x, y), color)


def shade(base, delta, rng):
    jitter = rng.randint(-3, 3)
    return tuple(max(0, min(255, c + delta + jitter)) for c in base)


def turf(path, base, stripe, rgba=False):
    rng = random.Random("turf:" + path)
    img = canvas(rgba)
    for y in range(16):
        for x in range(16):
            tone = stripe if x >= 8 else base
            delta = rng.choice([-6, -3, 0, 0, 3, 6])
            put(img, x, y, shade(tone, delta, rng))
    # Mown-grass directional tufts and a few dry blades.
    for _ in range(26):
        x, y = rng.randrange(16), rng.randrange(16)
        put(img, x, y, shade(base, 14, rng))
        if y + 1 < 16:
            put(img, x, y + 1, shade(base, -10, rng))
    for _ in range(8):
        put(img, rng.randrange(16), rng.randrange(16), (150, 168, 96))
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def field_line(path):
    rng = random.Random("line")
    img = canvas(False)
    base = (235, 234, 226)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            put(img, x, y, shade(base, -10 if edge else rng.choice([-3, 0, 0, 3]), rng))
    for _ in range(10):
        put(img, rng.randrange(16), rng.randrange(16), (214, 212, 200))
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def goal_post(path):
    rng = random.Random("post")
    img = canvas(False)
    for y in range(16):
        for x in range(16):
            # Cylindrical metal: bright centre band, shaded edges.
            delta = 18 - abs(x - 7) * 4 - (2 if y > 12 else 0)
            put(img, x, y, shade((238, 240, 242), delta, rng))
    for (bx, by) in ((4, 5), (11, 5), (4, 11), (11, 11)):
        put(img, bx, by, (150, 154, 160))
        put(img, bx - 1, by - 1, (252, 252, 252))
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def net(path, color):
    rng = random.Random("net:" + path)
    img = canvas(True)
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0:
                alpha = 215 if (x % 4 == 0 and y % 4 == 0) else 175
                put(img, x, y, color, alpha)
    for x in range(0, 16, 4):  # brighter knots at crossings
        for y in range(0, 16, 4):
            put(img, x, y, tuple(min(255, c + 35) for c in color), 235)
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def seat(path, base, rgba):
    rng = random.Random("seat:" + path)
    img = canvas(rgba)
    dark = tuple(max(0, c - 45) for c in base)
    light = tuple(min(255, c + 40) for c in base)
    for y in range(16):
        for x in range(16):
            if y < 3:  # backrest
                color = dark if x in (0, 15) else shade(base, rng.choice([-4, 0, 4]), rng)
            elif y < 12:  # cushion
                color = light if y == 3 else shade(base, rng.choice([-3, 0, 0, 3]), rng)
                if y == 11:
                    color = dark
            else:  # legs / underside
                color = (54, 48, 44) if x in (1, 2, 13, 14) else (0, 0, 0, 0) if rgba else dark
            if rgba and y >= 12 and x not in (1, 2, 13, 14):
                put(img, x, y, (0, 0, 0), 0)
            else:
                put(img, x, y, color)
    # Cushion seam down the middle for a padded look.
    for y in range(4, 11):
        put(img, 7, y, dark)
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def scoreboard(path):
    rng = random.Random("board")
    img = canvas(False)
    frame, screen = (30, 32, 40), (16, 24, 40)
    for y in range(16):
        for x in range(16):
            border = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
            put(img, x, y, shade(frame if border else screen, rng.choice([-2, 0, 2]), rng))
    # Faint scanlines.
    for y in range(3, 14, 2):
        for x in range(2, 14):
            put(img, x, y, (20, 30, 50))
    # Team indicator dots and score-like digit bars.
    put(img, 3, 4, (220, 70, 70))
    put(img, 12, 4, (70, 120, 230))
    for x in range(5, 11):
        put(img, x, 4, (120, 230, 150))
    for y in range(7, 12):
        put(img, 5, y, (210, 235, 215))
        put(img, 10, y, (210, 235, 215))
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def stadium_wall(path):
    rng = random.Random("wall")
    img = canvas(True)
    base = (158, 162, 168)
    for y in range(16):
        for x in range(16):
            seam = x == 8 or y == 8
            put(img, x, y, shade(base, -18 if seam else rng.choice([-4, 0, 0, 4]), rng))
    for _ in range(14):
        put(img, rng.randrange(16), rng.randrange(16), (132, 136, 142))
    for x in range(16):  # coping highlight on top
        put(img, x, 0, (190, 194, 200))
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def stadium_roof(path):
    rng = random.Random("roof")
    img = canvas(True)
    base = (148, 188, 214)
    for y in range(16):
        for x in range(16):
            rib = x in (0, 5, 10, 15)
            put(img, x, y, shade(base, -22 if rib else rng.choice([-3, 0, 0, 3]), rng))
    for y in range(0, 16, 5):  # rivets on each rib
        for x in (0, 5, 10, 15):
            put(img, x, y, (214, 232, 244))
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def stadium_lamp(path):
    rng = random.Random("lamp")
    img = canvas(True)
    housing = (44, 48, 58)
    for y in range(16):
        for x in range(16):
            put(img, x, y, shade(housing, rng.choice([-3, 0, 3]), rng))
    # Glowing lens with a warm halo.
    for y in range(3, 13):
        for x in range(3, 13):
            dist = max(abs(x - 7.5), abs(y - 7.5))
            if dist <= 2.0:
                put(img, x, y, (255, 246, 210), 255)
            elif dist <= 3.4:
                put(img, x, y, (255, 214, 120), 235)
            elif dist <= 4.4:
                put(img, x, y, (255, 190, 80), 150)
    put(img, 6, 6, (255, 255, 245), 255)
    put(img, 7, 7, (255, 255, 245), 255)
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def trophy(path):
    img = canvas(True)
    gold, light, dark = (212, 168, 60), (248, 220, 120), (150, 108, 30)
    # Cup bowl (rows 3-7), stem (8-10), base (11-13), handles.
    bowl = {3: (4, 11), 4: (3, 12), 5: (3, 12), 6: (4, 11), 7: (5, 10)}
    for y, (lo, hi) in bowl.items():
        for x in range(lo, hi + 1):
            put(img, x, y, gold)
    for x in (2, 13):  # handles
        for y in (4, 5, 6):
            put(img, x, y, dark)
    for y in range(8, 11):
        put(img, 7, y, gold)
        put(img, 8, y, gold)
    for x in range(5, 11):
        put(img, x, 11, gold)
    for x in range(4, 12):
        put(img, x, 12, dark)
    for x in range(6, 10):  # shine streak
        put(img, x, 4, light)
    put(img, 5, 5, light)
    put(img, 7, 9, light)
    img.save(os.path.join(BLOCK_DIR, path), optimize=True)


def main():
    os.makedirs(BLOCK_DIR, exist_ok=True)
    turf("artificial_turf.png", (74, 148, 64), (84, 160, 72))
    turf("artificial_turf_dark.png", (52, 112, 46), (60, 124, 52))
    field_line("field_line.png")
    goal_post("goal_post.png")
    net("goal_net.png", (238, 238, 238))
    net("red_goal_net.png", (220, 86, 86))
    net("blue_goal_net.png", (86, 130, 230))
    seats = {
        "red_seat.png": ((200, 64, 60), False),
        "blue_seat.png": ((60, 96, 200), False),
        "yellow_seat.png": ((224, 182, 44), True),
        "green_seat.png": ((58, 168, 84), True),
        "purple_seat.png": ((132, 74, 190), True),
        "orange_seat.png": ((226, 124, 34), True),
    }
    for name, (color, rgba) in seats.items():
        seat(name, color, rgba)
    scoreboard("scoreboard.png")
    stadium_wall("stadium_wall.png")
    stadium_roof("stadium_roof.png")
    stadium_lamp("stadium_lamp.png")
    trophy("trophy_block.png")
    print("block textures regenerated:", len(os.listdir(BLOCK_DIR)))


if __name__ == "__main__":
    main()
