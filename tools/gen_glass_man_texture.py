# The Glass Man's skin: pale, clear glass over the whole 64x64 player layout, with brighter edges and streaks.
import zlib, struct, sys, random
W=H=64
r=random.Random(9)
px=[]
for y in range(H):
    row=[]
    for x in range(W):
        edge = x % 4 == 0 or y % 4 == 0
        streak = (x + y) % 9 == 0
        base=(200,225,240,110)
        if edge: base=(230,245,255,170)
        if streak: base=(245,252,255,190)
        jitter=r.randint(-8,8)
        row.append((min(255,base[0]+jitter),min(255,base[1]+jitter),255,base[3]))
    px.append(row)
raw=b''.join(b'\x00'+b''.join(bytes(p) for p in row) for row in px)
def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
open(sys.argv[1],'wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',W,H,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
