import zlib, struct, sys, random, math
def save(px, path):
    raw=b''.join(b'\x00'+b''.join(bytes(p) for p in row) for row in px)
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    open(path,'wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def mix(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))+(255,)
def glass(base, edge, shine, seed):
    r=random.Random(seed); px=[[None]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            t=(x+y)/30.0 + r.uniform(-0.06,0.06)
            c=mix(base, edge, min(1,max(0,t)))
            # faint diagonal streaks like a glassy sheen
            if (x - y) % 7 == 0 and r.random()<0.8: c=mix(c, shine, 0.45)
            if x in (0,15) or y in (0,15): c=mix(c, edge, 0.5)
            px[y][x]=c
    for (x,y) in [(3,2),(4,2),(2,3)]: px[y][x]=mix(px[y][x],shine,0.8)
    return px
def marble(seed):
    r=random.Random(seed); px=[[None]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v=math.sin((x*0.9+y*0.5)+3*math.sin(y*0.4+seed))*0.5+0.5
            c=mix((236,233,228),(214,210,206),r.random()*0.5)
            if v>0.93: c=mix(c,(150,146,150),0.7)
            px[y][x]=c
    return px
def pebbles(seed):
    r=random.Random(seed); px=[[mix((150,146,150),(120,116,122),r.random()) for x in range(16)] for y in range(16)]
    for _ in range(14):
        cx,cy=r.randrange(16),r.randrange(16); rad=r.choice([1,1.5,2])
        tone=mix((240,236,232),(196,192,190),r.random())
        for y in range(16):
            for x in range(16):
                dx=min(abs(x-cx),16-abs(x-cx)); dy=min(abs(y-cy),16-abs(y-cy))
                if dx*dx+dy*dy<=rad*rad: px[y][x]=tone if (dy<rad-0.5 or rad<1.5) else mix(tone,(100,96,100),0.4)
    return px
out=sys.argv[1]
save(glass((226,220,240),(184,170,214),(255,255,255),1), out+'/dream_glass.png')
save(glass((150,120,196),(104,80,150),(214,196,250),2), out+'/dusky_dream_glass.png')
save(glass((52,40,86),(28,20,52),(120,100,190),3), out+'/deep_dream_glass.png')
