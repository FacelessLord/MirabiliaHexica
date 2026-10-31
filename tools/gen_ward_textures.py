import zlib, struct, sys, random
def save(px, path):
    raw=b''.join(b'\x00'+b''.join(bytes(p) for p in row) for row in px)
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
    open(path,'wb').write(png)
def stone(base, dark, light, seed):
    r=random.Random(seed)
    px=[[base]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v=r.random()
            px[y][x]= dark if v<0.18 else (light if v>0.88 else base)
            if x in (0,15) or y in (0,15): px[y][x]=dark
    return px
# a hexagon outline, centre (7.5,7.5)
hexagon=[(5,3),(6,3),(7,3),(8,3),(9,3),(10,3),(4,4),(11,4),(3,5),(12,5),(3,6),(12,6),(2,7),(13,7),(2,8),(13,8),(3,9),(12,9),(3,10),(12,10),(4,11),(11,11),(5,12),(6,12),(7,12),(8,12),(9,12),(10,12)]
out=sys.argv[1]
# Warding Stone: deep slate with a glowing amethyst hexagon and a bar across (a barrier)
px=stone((58,52,72,255),(38,34,50,255),(76,70,92,255),1)
for (x,y) in hexagon: px[y][x]=(190,140,255,255)
for x in range(4,12): px[7][x]=(235,205,255,255); px[8][x]=(160,110,230,255)
save(px, out+'/warding_stone.png')
# Silence Stone: sculk-dark stone with a teal hexagon and a closed eye
px=stone((30,40,48,255),(16,24,30,255),(44,58,66,255),2)
for (x,y) in hexagon: px[y][x]=(40,170,180,255)
for x in range(5,11): px[8][x]=(120,230,230,255)
for (x,y) in [(4,7),(11,7),(6,9),(9,9),(7,10),(8,10)]: px[y][x]=(120,230,230,255)
save(px, out+'/silence_stone.png')
