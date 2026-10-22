#!/usr/bin/env python3
"""Generate mcsport soccer ball textures with the standard library only.

Outputs:
  - textures/entity/soccer_ball.png  128x64 equirectangular map wrapped by the
    UV-sphere BallModel (white leather + twelve black pentagons of a truncated
    icosahedron, anti-aliased).
  - textures/item/soccer_ball.png    16x16 inventory icon of the same ball.

Run:  python tools/generate_ball_textures.py
"""
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENTITY_OUT = os.path.join(
    ROOT, "src/main/resources/assets/mcsport/textures/entity/soccer_ball.png")
ITEM_OUT = os.path.join(
    ROOT, "src/main/resources/assets/mcsport/textures/item/soccer_ball.png")

WHITE = (244, 244, 242, 255)
BLACK = (17, 17, 19, 255)
# Exact truncated-icosahedron apothem is 0.2875 rad; shave it slightly so
# thin white leather seams remain between each pentagon and its hexagons.
PENTAGON_RADIUS = 0.265  # angular centre-to-edge radius in radians
FIVE = 2.0 * math.pi / 5.0


def normalize(v):
    length = math.sqrt(sum(c * c for c in v))
    return tuple(c / length for c in v)


def pentagon_centers():
    """Twelve normalized icosahedron vertices = pentagon centres of a soccer ball."""
    phi = (1.0 + math.sqrt(5.0)) / 2.0
    raw = [
        (0.0, 1.0, phi), (0.0, -1.0, phi), (0.0, 1.0, -phi), (0.0, -1.0, -phi),
        (1.0, phi, 0.0), (-1.0, phi, 0.0), (1.0, -phi, 0.0), (-1.0, -phi, 0.0),
        (phi, 0.0, 1.0), (-phi, 0.0, 1.0), (phi, 0.0, -1.0), (-phi, 0.0, -1.0),
    ]
    centers = []
    for c in raw:
        c = normalize(c)
        up = (0.0, 1.0, 0.0)
        if abs(c[1]) > 0.9:
            up = (1.0, 0.0, 0.0)
        e1 = normalize((up[1] * c[2] - up[2] * c[1],
                        up[2] * c[0] - up[0] * c[2],
                        up[0] * c[1] - up[1] * c[0]))
        e2 = (c[1] * e1[2] - c[2] * e1[1],
              c[2] * e1[0] - c[0] * e1[2],
              c[0] * e1[1] - c[1] * e1[0])
        centers.append((c, e1, e2))
    return centers


CENTERS = pentagon_centers()


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def pentagon_coverage(direction):
    """0.0 = white leather, 1.0 = deep inside a black pentagon (anti-aliased)."""
    best_rho = math.pi
    best_frame = None
    for c, e1, e2 in CENTERS:
        rho = math.acos(max(-1.0, min(1.0, dot(direction, c))))
        if rho < best_rho:
            best_rho = rho
            best_frame = (c, e1, e2)
    c, e1, e2 = best_frame
    bearing = math.atan2(dot(direction, e2), dot(direction, e1))
    wrapped = (bearing + FIVE / 2.0) % FIVE - FIVE / 2.0
    edge = PENTAGON_RADIUS / math.cos(wrapped)
    # One-texel soft transition for smooth silhouettes on the patches.
    coverage = (edge - best_rho) / 0.035 + 0.5
    return max(0.0, min(1.0, coverage))


def blend(coverage):
    return tuple(round(WHITE[i] * (1.0 - coverage) + BLACK[i] * coverage)
                 for i in range(4))


def equirectangular(width, height):
    pixels = [[WHITE] * width for _ in range(height)]
    for y in range(height):
        lat = math.pi / 2.0 - math.pi * (y + 0.5) / height
        for x in range(width):
            lon = 2.0 * math.pi * (x + 0.5) / width
            direction = (math.cos(lat) * math.cos(lon),
                         math.sin(lat),
                         math.cos(lat) * math.sin(lon))
            pixels[y][x] = blend(pentagon_coverage(direction))
    return pixels


def sphere_icon(size):
    """Front-facing orthographic sphere used for the inventory icon."""
    pixels = [[(0, 0, 0, 0)] * size for _ in range(size)]
    centre = (size - 1) / 2.0
    radius = centre - 0.15
    for y in range(size):
        for x in range(size):
            nx = (x - centre) / radius
            ny = -(y - centre) / radius
            planar = nx * nx + ny * ny
            if planar > 1.0:
                continue
            nz = math.sqrt(max(0.0, 1.0 - planar))
            coverage = pentagon_coverage((nx, ny, nz))
            # Slightly darken the rim for a spherical shaded look.
            shade = 0.78 + 0.22 * nz
            color = blend(coverage)
            alpha = 255
            if planar > 0.93:
                alpha = int(255 * max(0.0, (1.0 - planar) / 0.07))
            pixels[y][x] = (
                min(255, round(color[0] * shade)),
                min(255, round(color[1] * shade)),
                min(255, round(color[2] * shade)),
                alpha)
    return pixels


def write_png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type 0 (none)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))
    compressed = zlib.compress(bytes(raw), 9)

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return out + struct.pack(">I", crc)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", compressed)
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)
    print("wrote", path, f"{width}x{height}")


if __name__ == "__main__":
    write_png(ENTITY_OUT, equirectangular(256, 128))
    write_png(ITEM_OUT, sphere_icon(16))
