#!/usr/bin/env python3
"""
mcsport texture generator.

Generates every PNG asset used by the mod with deterministic pixel art so the
whole resource pack shares one consistent vanilla-Minecraft-style art direction.
Re-run with:  python3 tools/generate_textures.py
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "mcsport", "textures")
DOCS = os.path.join(ROOT, "docs")

RNG = random.Random(4201)


def noise(base, amount=6):
    return tuple(max(0, min(255, c + RNG.randint(-amount, amount))) for c in base)


def save(img, relative_path):
    path = os.path.join(ASSETS, relative_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print(f"wrote {os.path.relpath(path, ROOT)} ({img.size[0]}x{img.size[1]})")


def artificial_turf():
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        stripe = 8 if (x // 4) % 2 == 0 else 0
        for y in range(16):
            base = (72 + stripe, 126 + stripe, 44 + stripe)
            px[x, y] = noise(base, 7)
    # A few darker grass blades for readability.
    for _ in range(24):
        x, y = RNG.randint(0, 15), RNG.randint(0, 15)
        px[x, y] = noise((58, 104, 34), 4)
    save(img, "block/artificial_turf.png")


def field_line():
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = noise((232, 232, 226), 5)
    save(img, "block/field_line.png")


def goal_post():
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            edge = x in (0, 15) or y in (0, 15)
            px[x, y] = noise((158, 160, 168), 4) if edge else noise((236, 238, 244), 4)
    # Highlight streak.
    for y in range(2, 14):
        px[3, y] = (250, 250, 252)
    save(img, "block/goal_post.png")


def goal_net(color):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    grid = (color[0], color[1], color[2], 235)
    border = (color[0], color[1], color[2], 255)
    for i in range(16):
        for j in range(16):
            if i % 4 == 0 or j % 4 == 0:
                px[i, j] = grid
            if i in (0, 15) or j in (0, 15):
                px[i, j] = border
    # Knots at intersections.
    for i in range(0, 16, 4):
        for j in range(0, 16, 4):
            px[i, j] = (min(color[0] + 40, 255), min(color[1] + 40, 255),
                        min(color[2] + 40, 255), 255)
    return img


def ball_mask():
    """Circular mask for the 16x16 item icon."""
    mask = Image.new("L", (16, 16), 0)
    px = mask.load()
    for x in range(16):
        for y in range(16):
            dx, dy = x - 7.5, y - 7.5
            if dx * dx + dy * dy <= 49:
                px[x, y] = 255
    return mask


def item_soccer_ball():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    mask = ball_mask().load()
    for x in range(16):
        for y in range(16):
            if mask[x, y]:
                px[x, y] = noise((238, 238, 236), 4) + (255,)
    # Classic black pentagon: centre patch + five surrounding patches.
    black = (24, 24, 28, 255)
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        px[x, y] = black
    patches = [(7, 3), (3, 6), (11, 6), (5, 11), (10, 11)]
    for cx, cy in patches:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                x, y = cx + dx, cy + dy
                if 0 <= x < 16 and 0 <= y < 16 and mask[x, y] and abs(dx) + abs(dy) < 3:
                    px[x, y] = black
    # Dark outline around the circle.
    for x in range(16):
        for y in range(16):
            if not mask[x, y]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and not mask[nx, ny]:
                    px[x, y] = (60, 60, 66, 255)
    save(img, "item/soccer_ball.png")


def entity_soccer_ball():
    """64x32 opaque texture wrapped by the low-poly sphere ball model."""
    img = Image.new("RGB", (64, 32))
    px = img.load()
    for x in range(64):
        for y in range(32):
            px[x, y] = noise((238, 238, 236), 4)
    black = (24, 24, 28)

    def patch(cx, cy, radius):
        for x in range(cx - radius, cx + radius + 1):
            for y in range(cy - radius, cy + radius + 1):
                if 0 <= x < 64 and 0 <= y < 32 and abs(x - cx) + abs(y - cy) <= radius + 1:
                    px[x, y] = black

    # Scattered pentagon-ish patches read consistently from every sphere band.
    for cx, cy, r in ((10, 8, 3), (32, 8, 3), (54, 8, 3),
                      (21, 23, 3), (43, 23, 3), (2, 24, 2), (62, 24, 2),
                      (32, 16, 2)):
        patch(cx, cy, r)
    save(img, "entity/soccer_ball.png")


def whistle():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    gold = (206, 168, 64, 255)
    dark_gold = (150, 116, 36, 255)
    mouth = (110, 112, 120, 255)
    # Mouthpiece.
    for y in range(7, 10):
        px[1, y] = mouth
        px[2, y] = mouth
    # Rounded body.
    for x in range(3, 14):
        for y in range(6, 12):
            px[x, y] = noise(gold[:3], 5) + (255,)
    for x in (3, 13):
        px[x, 6] = (0, 0, 0, 0)
        px[x, 11] = (0, 0, 0, 0)
    # Bottom shadow + top highlight.
    for x in range(3, 14):
        px[x, 11] = dark_gold
        px[x, 6] = (240, 208, 110, 255)
    # Blow hole and finger loop.
    px[10, 8] = (40, 32, 16, 255)
    px[10, 9] = (40, 32, 16, 255)
    for x, y in ((13, 7), (14, 7), (13, 10), (14, 10), (14, 8), (14, 9)):
        px[x, y] = dark_gold
    save(img, "item/whistle.png")


def item_basketball():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    mask = ball_mask().load()
    orange = (204, 102, 34)
    for x in range(16):
        for y in range(16):
            if mask[x, y]:
                px[x, y] = noise(orange, 7) + (255,)
    seam = (58, 30, 14, 255)
    # Curved vertical and horizontal seams.
    for y in range(16):
        for x in (7, 8):
            if mask[x, y]:
                px[x, y] = seam
    for x in range(16):
        if mask[x, 7]:
            px[x, 7] = seam
        if mask[x, 8]:
            px[x, 8] = seam
    for x in range(16):
        for y in range(16):
            if not mask[x, y]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and not mask[nx, ny]:
                    px[x, y] = (120, 56, 18, 255)
    save(img, "item/basketball.png")


def entity_basketball():
    """64x32 orange texture with dark seams, wrapped by the sphere model."""
    img = Image.new("RGB", (64, 32))
    px = img.load()
    for x in range(64):
        for y in range(32):
            px[x, y] = noise((204, 102, 34), 7)
    seam = (58, 30, 14)
    for y in range(32):
        px[15, y] = seam
        px[16, y] = seam
        px[47, y] = seam
        px[48, y] = seam
    for x in range(64):
        px[x, 15] = seam
        px[x, 16] = seam
    save(img, "entity/basketball_ball.png")


def artificial_turf_dark():
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        stripe = 6 if (x // 4) % 2 == 0 else 0
        for y in range(16):
            base = (56 + stripe, 104 + stripe, 34 + stripe)
            px[x, y] = noise(base, 7)
    save(img, "block/artificial_turf_dark.png")


def goal_net_texture():
    """White grid with transparent holes; the block stays solid for collision."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    line = (228, 230, 236, 235)
    for i in range(16):
        for j in range(16):
            if i % 3 == 0 or j % 3 == 0:
                px[i, j] = line
    for i in range(0, 16, 3):
        for j in range(0, 16, 3):
            px[i, j] = (250, 250, 252, 255)
    save(img, "block/goal_net.png")


def card(color, name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(4, 12):
        for y in range(2, 15):
            corner = (x in (4, 11) and y in (2, 14))
            if not corner:
                px[x, y] = noise(color, 5) + (255,)
    # Light border and inner highlight for a plastic-card look.
    for y in range(3, 14):
        px[4, y] = tuple(max(0, c - 45) for c in color) + (255,)
        px[11, y] = tuple(max(0, c - 45) for c in color) + (255,)
        px[5, y] = tuple(min(255, c + 35) for c in color) + (255,)
    save(img, f"item/{name}.png")


def seat(color):
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = noise(color, 7)
    # Backrest shadow at the top row, seat highlight in the middle.
    for x in range(16):
        px[x, 0] = tuple(max(0, c - 50) for c in color)
        px[x, 1] = tuple(max(0, c - 30) for c in color)
        px[x, 8] = tuple(min(255, c + 35) for c in color)
    return img


def block_seats():
    save(seat((178, 44, 44)), "block/red_seat.png")
    save(seat((44, 86, 196)), "block/blue_seat.png")


def panel_frame(base, bevel_light, bevel_dark, rivet, accent=None):
    """16x16 brushed-metal frame: bright top/left bevel, dark bottom/right."""
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = noise(base, 5)
    for i in range(16):
        px[i, 0] = bevel_light
        px[0, i] = tuple(int(c * 0.88) for c in bevel_light)
        px[i, 15] = bevel_dark
        px[15, i] = tuple(min(255, int(c * 1.12)) for c in bevel_dark)
    # Brushed horizontal streaks.
    for y in range(2, 14, 3):
        for x in range(2, 14):
            c = px[x, y]
            px[x, y] = (min(255, c[0] + 12), min(255, c[1] + 13), min(255, c[2] + 15))
    # Corner rivets.
    for rx, ry in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px[rx, ry] = rivet
        px[rx + 1 if rx < 8 else rx - 1, ry] = tuple(int(c * 0.55) for c in rivet)
    if accent is not None:
        for x in range(2, 14):
            px[x, 14] = accent
    return img


def led_screen(base, edge):
    """16x16 dark LED face: subtle scanlines plus a glowing inner border."""
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            level = base if y % 2 == 0 else base + 4
            px[x, y] = noise((level, level + 3, level + 8), 2)
    for i in range(1, 15):
        px[i, 0] = edge
        px[i, 15] = tuple(int(c * 0.75) for c in edge)
        px[0, i] = tuple(int(c * 0.9) for c in edge)
        px[15, i] = tuple(int(c * 0.9) for c in edge)
    for cx, cy in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        px[cx, cy] = tuple(int(c * 0.5) for c in edge)
    return img


def advertising_board():
    save(panel_frame((74, 82, 96), (128, 138, 156), (44, 50, 62), (176, 188, 206)),
         "block/ad_board_frame.png")
    save(led_screen(9, (26, 62, 92)), "block/ad_board_screen.png")


def scoreboard():
    save(panel_frame((62, 68, 80), (116, 124, 140), (36, 41, 51), (168, 178, 196),
                     accent=(60, 110, 168)),
         "block/scoreboard_frame.png")
    save(led_screen(7, (18, 42, 66)), "block/scoreboard_screen.png")


def trophy():
    """Polished gold with a diagonal specular streak, plus veined marble."""
    gold = Image.new("RGB", (16, 16))
    px = gold.load()
    ramp = [(255, 232, 140), (247, 210, 96), (226, 177, 58), (198, 146, 36),
            (168, 118, 26), (198, 146, 36), (226, 177, 58), (247, 210, 96)]
    for y in range(16):
        base = ramp[min(7, y // 2)]
        for x in range(16):
            shine = max(0, 10 - abs((x * 2 + y) - 26))
            px[x, y] = tuple(min(255, v + int(shine * 2.2)) for v in base)
    save(gold, "block/trophy_gold.png")

    marble = Image.new("RGB", (16, 16))
    px = marble.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = noise((26, 26, 30), 3)
    veins = [(1, 4), (2, 5), (3, 5), (4, 6), (5, 7), (6, 7), (7, 8), (8, 9),
             (9, 10), (10, 10), (11, 11), (12, 12), (13, 13), (14, 13)]
    for x, y in veins:
        px[x, y] = (74, 72, 82)
        if y + 1 < 16:
            px[x, y + 1] = (48, 47, 56)
    for x, y in [(3, 11), (4, 11), (5, 10), (6, 10), (7, 9), (8, 9), (9, 8),
                 (10, 8), (11, 7)]:
        px[x, y] = (58, 56, 66)
    for i in range(16):
        px[i, 0] = (44, 44, 50)
        px[0, i] = (38, 38, 44)
        px[i, 15] = (15, 15, 18)
    save(marble, "block/trophy_base.png")


def logo():
    """16x16 pixel logo upscaled to 128x128 for documentation / promotional use."""
    small = Image.new("RGB", (16, 16))
    px = small.load()
    for x in range(16):
        for y in range(16):
            stripe = 10 if (x // 4) % 2 == 0 else 0
            px[x, y] = noise((60 + stripe, 110 + stripe, 38 + stripe), 6)
    # White centre line and centre circle.
    for y in range(16):
        px[7, y] = (235, 235, 230)
        px[8, y] = (235, 235, 230)
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8), (6, 7), (9, 7), (7, 6), (8, 9)]:
        px[x, y] = (235, 235, 230)
    # Red goal (left) and blue goal (right).
    for y in range(2, 8):
        px[1, y] = (210, 45, 45)
        px[14, y] = (45, 90, 220)
    px[1, 2] = (255, 200, 200)
    px[14, 2] = (200, 215, 255)
    # Ball in the middle.
    px[7, 12] = (20, 20, 24)
    px[8, 12] = (20, 20, 24)
    px[6, 13] = (238, 238, 236)
    px[7, 13] = (20, 20, 24)
    px[8, 13] = (20, 20, 24)
    px[9, 13] = (238, 238, 236)
    px[7, 14] = (238, 238, 236)
    px[8, 14] = (238, 238, 236)
    os.makedirs(DOCS, exist_ok=True)
    small.resize((128, 128), Image.NEAREST).save(os.path.join(DOCS, "mcsport_logo.png"))
    print("wrote docs/mcsport_logo.png (128x128)")


def main():
    artificial_turf()
    field_line()
    goal_post()
    save(goal_net((205, 48, 48)), "block/red_goal_net.png")
    save(goal_net((48, 92, 220)), "block/blue_goal_net.png")
    item_soccer_ball()
    entity_soccer_ball()
    item_basketball()
    entity_basketball()
    card((228, 196, 36), "yellow_card")
    card((208, 40, 40), "red_card")
    block_seats()
    advertising_board()
    scoreboard()
    trophy()
    artificial_turf_dark()
    goal_net_texture()
    whistle()
    logo()
    print("All textures generated.")


if __name__ == "__main__":
    main()
