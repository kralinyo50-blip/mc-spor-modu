#!/usr/bin/env python3
"""
SportCraft texture generator.

Generates every PNG asset used by the mod with deterministic pixel art so the
whole resource pack shares one consistent vanilla-Minecraft-style art direction.
Re-run with:  python3 tools/generate_textures.py
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "sportcraft", "textures")
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
    """Opaque, tileable per-face ball texture for the cube entity model."""
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = noise((238, 238, 236), 4)
    black = (24, 24, 28)
    # Centre pentagon-ish patch.
    for x in range(6, 10):
        for y in range(6, 10):
            px[x, y] = black
    px[6, 6] = (238, 238, 236)
    px[9, 6] = (238, 238, 236)
    # Corner patches so adjacent faces read as one patterned ball.
    for cx, cy in ((1, 1), (14, 1), (1, 14), (14, 14)):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                px[cx + dx, cy + dy] = black
    # Edge midpoint patches.
    for cx, cy in ((7, 1), (1, 7), (14, 7), (7, 14)):
        px[cx, cy] = black
        px[cx + 1, cy] = black
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
    img = Image.new("RGB", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            px[x, y] = noise((204, 102, 34), 7)
    seam = (58, 30, 14)
    for y in range(16):
        px[7, y] = seam
        px[8, y] = seam
    for x in range(16):
        px[x, 7] = seam
        px[x, 8] = seam
    # Corner seam patches keep neighbouring faces consistent.
    for cx, cy in ((1, 1), (14, 14)):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                px[cx + dx, cy + dy] = seam
    save(img, "entity/basketball_ball.png")


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


def scoreboard():
    img = Image.new("RGB", (16, 16))
    px = img.load()
    frame = (96, 64, 30)
    board = (24, 26, 32)
    for x in range(16):
        for y in range(16):
            px[x, y] = noise(frame, 5)
    for x in range(2, 14):
        for y in range(2, 14):
            px[x, y] = noise(board, 4)
    # Digit-like score marks in amber.
    amber = (240, 188, 60)
    for x, y in [(5, 5), (5, 6), (5, 7), (6, 8), (10, 5), (10, 6), (10, 7), (9, 8)]:
        px[x, y] = amber
    # Centre colon.
    px[7, 6] = amber
    px[8, 6] = amber
    px[7, 9] = amber
    px[8, 9] = amber
    save(img, "block/scoreboard.png")


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
    small.resize((128, 128), Image.NEAREST).save(os.path.join(DOCS, "sportcraft_logo.png"))
    print("wrote docs/sportcraft_logo.png (128x128)")


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
    scoreboard()
    whistle()
    logo()
    print("All textures generated.")


if __name__ == "__main__":
    main()
