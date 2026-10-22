#!/usr/bin/env python3
"""Generate the 16x16 PNG textures for the extra mcsport stadium blocks.

Pure standard library (zlib + struct), same approach as
generate_ball_textures.py: four extra club-coloured seats plus the arena
construction blocks (wall, roof, lamp, trophy).
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/mcsport/textures/block")


def write_png(path, width, height, pixels):
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type 0
        for x in range(width):
            raw.extend(pixels[y * width + x])
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def shade(color, factor):
    return tuple(max(0, min(255, int(channel * factor))) for channel in color)


def seat_texture(base):
    """Chair texture: darker legs band, mid seat, lighter backrest."""
    pixels = []
    for y in range(16):
        for x in range(16):
            if y < 4:  # legs area
                color = shade(base, 0.62)
            elif y < 7:  # seat slab
                color = shade(base, 0.85)
            elif y < 11:  # gap shadow
                color = shade(base, 0.5)
            else:  # backrest, with a top highlight
                color = shade(base, 1.08 if y == 11 else 0.95)
            if x in (0, 15):
                color = shade(color, 0.8)
            pixels.append((*color, 255))
    return pixels


def wall_texture():
    base = (186, 190, 198)
    pixels = []
    for y in range(16):
        for x in range(16):
            color = base
            if y in (0, 7, 8, 15) or x in (0, 15):  # mortar grid
                color = shade(base, 0.72)
            elif (x + y) % 9 == 0:  # subtle speckle
                color = shade(base, 0.92)
            pixels.append((*color, 255))
    return pixels


def roof_texture():
    base = (190, 226, 234)
    pixels = []
    for y in range(16):
        for x in range(16):
            if y % 4 == 0 or x % 4 == 0:  # panel frame
                color = (126, 168, 178)
            else:
                color = shade(base, 1.0 + ((x + y) % 3) * 0.02)
            pixels.append((*color, 255))
    return pixels


def lamp_texture():
    pixels = []
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                color = (222, 196, 110)
            elif edge == 1:
                color = (246, 232, 168)
            else:
                color = (255, 248, 210)
            pixels.append((*color, 255))
    return pixels


def trophy_texture():
    base = (238, 190, 42)
    pixels = []
    for y in range(16):
        for x in range(16):
            if y >= 13:  # plinth
                color = shade(base, 0.62)
            elif y in (1, 2) and 4 <= x <= 11:  # cup rim highlight
                color = shade(base, 1.22)
            elif x in (0, 15) or y == 0:
                color = shade(base, 0.78)
            elif (x - 7.5) ** 2 + (y - 6) ** 2 < 18:  # cup body sheen
                color = shade(base, 1.12)
            else:
                color = base
            pixels.append((*color, 255))
    return pixels


SEATS = {
    "yellow_seat": (224, 176, 32),
    "green_seat": (47, 168, 79),
    "purple_seat": (122, 60, 176),
    "orange_seat": (226, 112, 28),
}


def main():
    for name, color in SEATS.items():
        write_png(os.path.join(OUT, name + ".png"), 16, 16, seat_texture(color))
    write_png(os.path.join(OUT, "stadium_wall.png"), 16, 16, wall_texture())
    write_png(os.path.join(OUT, "stadium_roof.png"), 16, 16, roof_texture())
    write_png(os.path.join(OUT, "stadium_lamp.png"), 16, 16, lamp_texture())
    write_png(os.path.join(OUT, "trophy_block.png"), 16, 16, trophy_texture())
    print("stadium block textures written to", OUT)


if __name__ == "__main__":
    main()
