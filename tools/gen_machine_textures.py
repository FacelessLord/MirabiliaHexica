"""Textures for the machines of media. Usage: py tools/gen_machine_textures.py common/src/main/resources/assets/mirabilia/textures/block"""
import zlib, struct, sys, random

def save(px, path):
    raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in px)
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(png)

def noisy(base, dark, light, seed, rim=None):
    r = random.Random(seed)
    px = [[base] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v = r.random()
            px[y][x] = dark if v < 0.15 else (light if v > 0.88 else base)
            if rim and (x in (0, 15) or y in (0, 15)): px[y][x] = rim
    return px

def frame(px, colour, inner=None):
    for i in range(16):
        for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]: px[y][x] = colour
        if inner:
            for (x, y) in [(i, 1), (i, 14), (1, i), (14, i)]:
                if 1 <= i <= 14: px[y][x] = inner

out = sys.argv[1]
AM, AM_D, AM_L = (150, 100, 210, 255), (100, 60, 160, 255), (215, 175, 255, 255)
CU, CU_D, CU_L = (190, 105, 70, 255), (140, 70, 45, 255), (225, 140, 100, 255)
FE, FE_D, FE_L = (190, 190, 196, 255), (120, 120, 130, 255), (225, 225, 232, 255)
GOLD, GOLD_L = (235, 190, 60, 255), (255, 235, 150, 255)

# Media Discharger: copper casing, an amethyst core on top with a rod stud, side seams with sparks
px = noisy(CU, CU_D, CU_L, 1, CU_D)
for y in range(5, 11):
    for x in range(5, 11): px[y][x] = AM if (x + y) % 3 else AM_L
for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]: px[y][x] = (250, 245, 210, 255)
save(px, out + '/media_discharger_top.png')
px = noisy(CU, CU_D, CU_L, 2, CU_D)
for x in range(16): px[4][x] = CU_D; px[11][x] = CU_D
for (x, y) in [(3, 7), (4, 8), (5, 7), (6, 8), (9, 7), (10, 8), (11, 7), (12, 8)]: px[y][x] = (200, 230, 255, 255)
save(px, out + '/media_discharger_side.png')
save(noisy(CU_D, (110, 55, 35, 255), CU, 3, CU_D), out + '/media_discharger_bottom.png')

# Ambient Collector: budding amethyst with a pale, open eye (the farmer's mind)
px = noisy(AM, AM_D, AM_L, 4)
for (x, y) in [(2, 2), (13, 3), (3, 12), (12, 13), (7, 1), (1, 8)]: px[y][x] = AM_L
eye = [(5, 7), (6, 6), (7, 6), (8, 6), (9, 6), (10, 7), (6, 8), (7, 9), (8, 9), (9, 8), (5, 8), (10, 8)]
for (x, y) in eye: px[y][x] = (245, 240, 255, 255)
for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]: px[y][x] = (60, 200, 120, 255)
save(px, out + '/ambient_collector.png')

# Solar Collector: glass top over a gold sun with four rays (the caltrop), iron frame
px = [[(170, 205, 230, 255)] * 16 for _ in range(16)]
r = random.Random(5)
for y in range(16):
    for x in range(16):
        if r.random() < 0.08: px[y][x] = (200, 230, 245, 255)
for y in range(6, 10):
    for x in range(6, 10): px[y][x] = GOLD
for (x, y) in [(7, 7), (8, 8)]: px[y][x] = GOLD_L
for i in range(2, 6):
    for (x, y) in [(i, i), (15 - i, i), (i, 15 - i), (15 - i, 15 - i)]: px[y][x] = GOLD
frame(px, FE_D, FE)
save(px, out + '/solar_collector_top.png')
px = noisy(FE, FE_D, FE_L, 6, FE_D)
for x in range(1, 15): px[2][x] = (170, 205, 230, 255)
for x in range(5, 11): px[9][x] = GOLD
save(px, out + '/solar_collector_side.png')
save(noisy(FE_D, (95, 95, 105, 255), FE, 7, FE_D), out + '/solar_collector_bottom.png')

# Crystallizer: iron frame with a glass window onto amethyst; a funnel on top, a spout below
px = noisy(FE, FE_D, FE_L, 8, FE_D)
for y in range(4, 12):
    for x in range(4, 12): px[y][x] = (40, 40, 48, 255)
for y in range(6, 10):
    for x in range(6, 10): px[y][x] = AM_D
save(px, out + '/crystallizer_top.png')
px = noisy(FE, FE_D, FE_L, 9, FE_D)
for y in range(3, 13):
    for x in range(3, 13): px[y][x] = (175, 210, 230, 255)
for (x, y) in [(7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10)]:
    px[y][x] = AM if (x + y) % 2 else AM_L
save(px, out + '/crystallizer_side.png')
px = noisy(FE_D, (95, 95, 105, 255), FE, 10, FE_D)
for y in range(6, 10):
    for x in range(6, 10): px[y][x] = (30, 30, 36, 255)
save(px, out + '/crystallizer_bottom.png')
