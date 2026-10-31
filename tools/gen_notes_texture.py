# The "On the Findings about Media Sources" item: a small dark book with a violet wellspring on its cover.
import zlib, struct, sys
T=(0,0,0,0); COVER=(46,32,74,255); EDGE=(26,18,44,255); PAGES=(226,218,200,255); GLOW=(196,140,255,255); SPINE=(80,56,120,255)
px=[[T]*16 for _ in range(16)]
for y in range(2,15):
    for x in range(3,14):
        px[y][x]=COVER
    px[y][3]=SPINE; px[y][13]=EDGE
for x in range(4,14): px[14][x]=PAGES; px[2][x]=EDGE
for y in range(3,14): px[y][14]=PAGES
# A wellspring: a ring with drops rising from it
for (x,y) in [(7,10),(8,10),(9,10),(6,9),(10,9),(6,8),(10,8),(7,7),(9,7),(8,5),(8,6),(7,4),(9,4)]: px[y][x]=GLOW
raw=b''.join(b'\x00'+b''.join(bytes(p) for p in row) for row in px)
def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
open(sys.argv[1],'wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
