# Inkstone (near-black, faint blue-violet grain) and four Glyphstone sigils (inkstone inscribed with glowing rings and figures).
import zlib, struct, sys, random, math
def save(px, path):
    raw=b''.join(b'\x00'+b''.join(bytes(p) for p in row) for row in px)
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    open(path,'wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def mix(a,b,t): t=max(0,min(1,t)); return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))+(255,)
def ink(seed):
    r=random.Random(seed)
    px=[[mix((14,12,22),(30,26,44),r.random()**2) for x in range(16)] for y in range(16)]
    for _ in range(10):  # a few faint glints
        x,y=r.randrange(16),r.randrange(16); px[y][x]=mix(px[y][x],(70,60,110),0.6)
    return px
import math
GLOW=(190,120,255); CORE=(244,220,255); HALO=(92,58,150)
C=7.5

def ring(mark, r, width=0.55):
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-C,y-C)
            if abs(d-r)<width: mark(x,y,1.0)
            elif abs(d-r)<width+0.75: mark(x,y,0.35)

def polygon(mark, r, corners, start, strength=0.85):
    pts=[(C+r*math.cos(start+k*2*math.pi/corners),C+r*math.sin(start+k*2*math.pi/corners)) for k in range(corners)]
    for i in range(corners):
        (ax,ay),(bx,by)=pts[i],pts[(i+1)%corners]
        for t in range(41):
            mark(int(round(ax+(bx-ax)*t/40)),int(round(ay+(by-ay)*t/40)),strength)

def ticks(mark, pts, strength=0.7):
    for (x,y) in pts: mark(x,y,strength)

CORNER_TICKS=[(1,1),(2,1),(1,2),(14,1),(13,1),(14,2),(1,14),(1,13),(2,14),(14,14),(13,14),(14,13)]
SIDE_TICKS=[(7,0),(8,0),(15,7),(15,8),(7,15),(8,15),(0,7),(0,8)]

def sigil(seed, draw):
    """A tile of a magic circle: glowing strokes, softly haloed, on inkstone."""
    px=ink(seed); lit={}
    def mark(x,y,k):
        if 0<=x<16 and 0<=y<16: lit[(x,y)]=max(lit.get((x,y),0),k)
    draw(mark)
    for (x,y),k in lit.items():
        px[y][x]=mix(px[y][x], CORE if k>=1.0 else GLOW, k) if k>0.5 else mix(px[y][x],HALO,0.8)
    return px

def centre(mark):
    for (x,y) in [(7,7),(8,8)]: mark(x,y,1.0)
    for (x,y) in [(8,7),(7,8)]: mark(x,y,0.6)

GLYPHS=[
    # A ring with a triangle in it, runes at the corners
    lambda m: (ring(m,5.2), polygon(m,4.6,3,-math.pi/2), ticks(m,CORNER_TICKS), centre(m)),
    # A ring with a square set on its point, runes at the sides
    lambda m: (ring(m,5.6,0.45), polygon(m,4.2,4,0.0,1.0), ticks(m,SIDE_TICKS), centre(m)),
    # Two rings, a hexagon between them
    lambda m: (ring(m,5.8,0.45), ring(m,2.4,0.45), polygon(m,4.1,6,0.0,0.6), ticks(m,CORNER_TICKS,0.5)),
    # A ring crossed by a crescent, and a star of dots
    lambda m: (ring(m,5.8,0.45), [m(int(round(C+3.6*math.cos(a/10))),int(round(C+3.6*math.sin(a/10)-1.5)),1.0) for a in range(4,28)],
               [m(int(round(C+2.4*math.cos(a/10))),int(round(C+2.4*math.sin(a/10)-0.5)),0.6) for a in range(8,24)],
               ticks(m,[(7,4),(8,4)],1.0), ticks(m,SIDE_TICKS,0.5)),
]
out=sys.argv[1]
save(ink(21), out+'/inkstone.png')
for i,g in enumerate(GLYPHS): save(sigil(22+i, g), out+f'/glyphstone_{i}.png')
