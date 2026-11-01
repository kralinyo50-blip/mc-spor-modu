#!/usr/bin/env python3
"""
Offline preview of the mod's 3D block models.

Minecraft cannot be launched in this environment, so this script renders a
rough isometric view of a block model JSON (averaging the real texture colours)
to catch geometry mistakes before a JAR is built. It is a sanity check only,
not a faithful Minecraft renderer: no lighting, no AO, no UV mapping.

Usage:
    python3 tools/preview_models.py                # writes docs/model_preview.png
    python3 tools/preview_models.py ad_board       # one model, prints the path
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "mcsport")
DOCS = os.path.join(ROOT, "docs")

COS30 = math.cos(math.radians(30))
SIN30 = math.sin(math.radians(30))
CELL = 480          # per-model canvas size
SCALE = 13.0        # pixels per model unit
DEFAULT_MODELS = ["trophy_block", "ad_board", "scoreboard"]


def texture_colour(reference):
    """Average RGB of the texture a model face points at."""
    name = reference.split(":")[-1].split("/")[-1]
    image = Image.open(os.path.join(ASSETS, "textures", "block", name + ".png")).convert("RGBA")
    pixels = list(image.get_flattened_data() if hasattr(image, "get_flattened_data")
                  else image.getdata())
    count = len(pixels)
    return (sum(p[0] for p in pixels) // count,
            sum(p[1] for p in pixels) // count,
            sum(p[2] for p in pixels) // count)


def project(x, y, z, origin_x, origin_y):
    return ((x - z) * COS30 * SCALE + origin_x,
            ((x + z) * SIN30 - y) * SCALE + origin_y)


def shade(colour, factor):
    return tuple(max(0, min(255, int(channel * factor))) for channel in colour)


# Quad corners per face name, plus the fake light factor for that direction.
FACES = {
    "up": (lambda e: [(e[0], e[4], e[2]), (e[3], e[4], e[2]), (e[3], e[4], e[5]), (e[0], e[4], e[5])], 1.00),
    "down": (lambda e: [(e[0], e[1], e[2]), (e[0], e[1], e[5]), (e[3], e[1], e[5]), (e[3], e[1], e[2])], 0.35),
    "south": (lambda e: [(e[0], e[1], e[5]), (e[3], e[1], e[5]), (e[3], e[4], e[5]), (e[0], e[4], e[5])], 0.84),
    "east": (lambda e: [(e[3], e[1], e[2]), (e[3], e[1], e[5]), (e[3], e[4], e[5]), (e[3], e[4], e[2])], 0.64),
    "north": (lambda e: [(e[3], e[1], e[2]), (e[0], e[1], e[2]), (e[0], e[4], e[2]), (e[3], e[4], e[2])], 0.72),
    "west": (lambda e: [(e[0], e[1], e[2]), (e[0], e[1], e[5]), (e[0], e[4], e[5]), (e[0], e[4], e[2])], 0.50),
}


def render_model(name):
    path = os.path.join(ASSETS, "models", "block", name + ".json")
    model = json.load(open(path))
    textures = model.get("textures", {})
    cache = {}
    quads = []
    for element in model["elements"]:
        box = tuple(element["from"]) + tuple(element["to"])
        for face, (corners, light) in FACES.items():
            if face not in element["faces"]:
                continue
            points = corners(box)
            depth = sum(x + y + z for (x, y, z) in points)
            reference = element["faces"][face]["texture"].lstrip("#")
            key = textures.get(reference, reference)
            if key not in cache:
                cache[key] = texture_colour(key)
            quads.append((depth, points, shade(cache[key], light)))
    # Painter's algorithm: far quads first.
    quads.sort(key=lambda quad: quad[0])

    image = Image.new("RGBA", (CELL, CELL), (22, 24, 30, 255))
    draw = ImageDraw.Draw(image)
    origin_x, origin_y = CELL / 2, CELL * 0.70
    for _, points, colour in quads:
        draw.polygon([project(x, y, z, origin_x, origin_y) for (x, y, z) in points], fill=colour)
    return image


def main():
    names = sys.argv[1:] or DEFAULT_MODELS
    if len(names) == 1:
        out = os.path.join("/tmp", "preview_" + names[0] + ".png")
        render_model(names[0]).save(out)
        print(out)
        return

    sheet = Image.new("RGBA", (CELL * len(names), CELL + 40), (16, 18, 24, 255))
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(names):
        sheet.alpha_composite(render_model(name), (index * CELL, 40))
        draw.text((index * CELL + 18, 16), name.upper(), fill=(255, 224, 130, 255))
    os.makedirs(DOCS, exist_ok=True)
    out = os.path.join(DOCS, "model_preview.png")
    sheet.save(out)
    print("wrote " + os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
