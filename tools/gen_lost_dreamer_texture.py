# The Lost Dreamer's glow layer: mostly black (invisible when drawn additively), with violet cracks and eyes.
# It sits over vanilla's zombie villager skin, laid out the same way (64x64).
import zlib, struct, sys, random
W=H=64
r=random.Random(77)
px=[[(0,0,0,255)]*W for _ in range(H)]
def put(x,y,c):
    if 0<=x<W and 0<=y<H: px[y][x]=c
VIOLET=(150,70,230,255); PALE=(220,170,255,255); DIM=(70,30,120,255)
# Wandering cracks everywhere on the sheet (they land on body, arms and legs)
for _ in range(60):
    x,y=r.randrange(W),r.randrange(H)
    for _ in range(r.randint(3,9)):
        put(x,y,VIOLET if r.random()<0.6 else DIM)
        x+=r.choice([-1,0,1]); y+=r.choice([0,1,1])
# Eyes: the head's front face is u 8..16, v 8..18 on the villager layout
for (x,y) in [(9,12),(10,12),(13,12),(14,12)]: put(x,y,PALE)
for (x,y) in [(9,13),(14,13)]: put(x,y,VIOLET)
raw=b''.join(b'\x00'+b''.join(bytes(p) for p in row) for row in px)
def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
open(sys.argv[1],'wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',W,H,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
