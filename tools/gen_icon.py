"""The mod's icon: the sign on the sun (a caltrop, one spike up) glowing over a dimmed sun in a violet night sky.
Pixel art at 128 x 128, also written 4x larger for Modrinth.
Usage: py tools/gen_icon.py common/src/main/resources/assets/mirabilia/icon.png icon_512.png"""
import sys, zlib, struct, math, random

N = 128

def save(px, size, scale, path):
    rows = []
    for y in range(size * scale):
        row = bytearray(b'\x00')
        for x in range(size * scale):
            row += bytes(px[y // scale][x // scale])
        rows.append(bytes(row))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size * scale, size * scale, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b''))
    open(path, 'wb').write(png)

def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))

def over(base, colour, alpha):
    return mix(base, colour, alpha)

# --- the sky: violet night, darker at the top, a scatter of stars
TOP, BOTTOM = (16, 9, 32), (46, 22, 78)
px = [[mix(TOP, BOTTOM, y / (N - 1)) for x in range(N)] for y in range(N)]
rnd = random.Random(7)
for _ in range(70):
    x, y = rnd.randrange(N), rnd.randrange(N)
    b = rnd.choice([0.35, 0.5, 0.8])
    px[y][x] = over(px[y][x], (230, 220, 255), b)
for x, y in [(18, 22), (104, 16), (112, 98), (14, 104)]:   # a few brighter, four-pointed
    for dx, dy, a in [(0, 0, 1.0), (1, 0, .45), (-1, 0, .45), (0, 1, .45), (0, -1, .45)]:
        px[y + dy][x + dx] = over(px[y + dy][x + dx], (255, 245, 255), a)

C = (63.5, 63.5)
def dist(x, y): return math.hypot(x + 0.5 - C[0] - 0.5, y + 0.5 - C[1] - 0.5)

# --- the sun: a warm disc with a soft glow, its middle dimmed as through a lens
SUN_R, GLOW_R, DIM_R = 44, 58, 27
for y in range(N):
    for x in range(N):
        d = dist(x, y)
        if d < GLOW_R:
            px[y][x] = over(px[y][x], (255, 196, 92), 0.35 * (1 - (d - SUN_R) / (GLOW_R - SUN_R)) if d > SUN_R else 0)
        if d <= SUN_R:
            # Banded like pixel art: brighter rings outward
            band = int((SUN_R - d) / 5)
            px[y][x] = mix((246, 168, 64), (255, 232, 160), band / 6)
        if d <= DIM_R:
            px[y][x] = over(px[y][x], (24, 10, 40), 0.82 + 0.1 * (1 - d / DIM_R))
        elif d <= DIM_R + 1.2:
            px[y][x] = over(px[y][x], (24, 10, 40), 0.45)

# --- the caltrop: one spike up (leaning a little towards us), three below, turned so all four read
TILT = math.radians(35)
up = (0.0, -math.cos(TILT), -math.sin(TILT))         # x right, y down, z towards the viewer is negative
def norm(v): l = math.sqrt(sum(c * c for c in v)); return tuple(c / l for c in v)
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def rot(v, axis, ang):
    k = axis; c, s = math.cos(ang), math.sin(ang)
    kv = cross(k, v); kd = sum(k[i] * v[i] for i in range(3))
    return tuple(v[i] * c + kv[i] * s + k[i] * kd * (1 - c) for i in range(3))
across = norm(cross(up, (0, 0, 1)))
spikes = [up]
for i in range(3):
    a = rot(across, up, math.radians(50) + i * 2 * math.pi / 3)
    spikes.append(norm(tuple(up[j] * (-1 / 3) + a[j] * math.sqrt(8 / 9) for j in range(3))))
LENGTH, ROOT = 40.0, 4.2

def inside(p, a, b, c):
    def s(p1, p2, p3): return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
    d1, d2, d3 = s(p, a, b), s(p, b, c), s(p, c, a)
    return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))

# Far spikes first, so near ones are drawn over them
for v in sorted(spikes, key=lambda v: -v[2]):
    tip = (C[0] + 0.5 + v[0] * LENGTH, C[1] + 0.5 + v[1] * LENGTH)
    side = norm((-v[1], v[0], 0.0)) if abs(v[0]) + abs(v[1]) > 1e-6 else (1, 0, 0)
    near = (1 - v[2]) / 2                              # 1: pointing at us, 0: away
    w = ROOT * (0.65 + 0.55 * near)
    a = (C[0] + 0.5 + side[0] * w, C[1] + 0.5 + side[1] * w)
    b = (C[0] + 0.5 - side[0] * w, C[1] + 0.5 - side[1] * w)
    glow = 0.55 + 0.45 * near
    edge = (70, 30, 110)
    for y in range(N):
        for x in range(N):
            p = (x + 0.5, y + 0.5)
            if inside(p, a, b, tip):
                # Brighter along the middle and towards the tip
                t = math.hypot(p[0] - C[0] - 0.5, p[1] - C[1] - 0.5) / LENGTH
                px[y][x] = mix(mix((150, 100, 220), (238, 212, 255), glow), (255, 250, 255), t * 0.8)
    # A dark outline so the spike reads at small sizes
    for y in range(N):
        for x in range(N):
            p = (x + 0.5, y + 0.5)
            if inside(p, a, b, tip): continue
            if any(inside((p[0] + dx, p[1] + dy), a, b, tip) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[y][x] = mix(px[y][x], edge, 0.75)
# The heart of the sign
for y in range(N):
    for x in range(N):
        if dist(x, y) <= 3.2: px[y][x] = (255, 252, 255)

pixels = [[c + (255,) for c in row] for row in px]
save(pixels, N, 1, sys.argv[1])
if len(sys.argv) > 2: save(pixels, N, 4, sys.argv[2])
