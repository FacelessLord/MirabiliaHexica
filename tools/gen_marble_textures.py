# Marble, Polished Marble, Marble Bricks and Marble Pebbles: a mottled grey-white stone with fine dark veins.
import zlib, struct, sys, random, math
def save(px, path):
    h=len(px); w=len(px[0])
    raw=b''.join(b'\x00'+b''.join(bytes(p) for p in row) for row in px)
    def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    open(path,'wb').write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def mix(a,b,t): t=max(0,min(1,t)); return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))+(255,)
def value_noise(seed, cell):
    r=random.Random(seed); n=16//cell
    g=[[r.random() for _ in range(n)] for _ in range(n)]
    def at(x,y):
        fx,fy=x/cell,y/cell; x0,y0=int(fx)%n,int(fy)%n; x1,y1=(x0+1)%n,(y0+1)%n
        tx,ty=fx-int(fx),fy-int(fy); tx=tx*tx*(3-2*tx); ty=ty*ty*(3-2*ty)
        a=g[y0][x0]+(g[y0][x1]-g[y0][x0])*tx; b=g[y1][x0]+(g[y1][x1]-g[y1][x0])*tx
        return a+(b-a)*ty
    return at
# Cool, near-white greys, so marble sits well beside the lilac dream glass
LIGHT=(244,244,248); MID=(226,226,232); DARK=(190,190,200); VEIN=(150,150,166)
def stone(seed, veins=True, contrast=1.0):
    r=random.Random(seed); n1=value_noise(seed,8); n2=value_noise(seed+1,4); n3=value_noise(seed+2,2)
    tint=value_noise(seed+3,8)
    px=[[None]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v=0.45*n1(x,y)+0.3*n2(x,y)+0.25*n3(x,y)+r.uniform(-0.11,0.11)
            v=0.5+(v-0.5)*1.35*contrast
            c=mix(MID,LIGHT,(v-0.38)*2.0) if v>0.38 else mix(DARK,MID,v/0.38)
            # Patches lean a little brighter or a little cooler (faintly lilac), never yellow
            t=tint(x,y)
            c=mix(c,(250,250,252),(t-0.6)*1.2) if t>0.6 else mix(c,(212,210,228),(0.4-t)*1.2) if t<0.4 else c
            px[y][x]=c
    # Veins: a few wandering cracks, wrapping at the edges so the texture tiles
    for _ in range(3 if veins else 0):
        x,y=r.randrange(16),r.randrange(16)
        for step in range(r.randint(5,12)):
            px[y%16][x%16]=mix(px[y%16][x%16],VEIN,0.4 if step%3 else 0.6)
            # Mostly sideways, wandering up and down, sometimes skipping a pixel
            x+=1 if r.random()<0.75 else 0
            y+=r.choice([-1,0,0,1])
    return px
def polished(seed):
    # Marble ground smooth: its own pattern, slightly softened, inside a bevelled frame
    px=stone(seed, veins=True, contrast=0.85)
    for y in range(16):
        for x in range(16):
            px[y][x]=mix(px[y][x],(244,244,248),0.15)
    for i in range(16):
        px[0][i]=mix(px[0][i],(252,252,254),0.6); px[i][0]=mix(px[i][0],(252,252,254),0.6)   # lit top and left
        px[15][i]=mix(px[15][i],(150,150,164),0.7); px[i][15]=mix(px[i][15],(150,150,164),0.7) # shadowed bottom and right
        if 0<i<15:
            px[1][i]=mix(px[1][i],(250,250,252),0.3); px[i][1]=mix(px[i][1],(250,250,252),0.3)
            px[14][i]=mix(px[14][i],DARK,0.35); px[i][14]=mix(px[i][14],DARK,0.35)
    return px
def bricks(seed):
    base=stone(seed, veins=False)
    for y in range(16):
        for x in range(16):
            row=y//4; off=0 if row%2==0 else 4
            if y%4==3 or (x+off)%8==7: base[y][x]=mix(base[y][x],DARK,0.8)
            elif y%4==0: base[y][x]=mix(base[y][x],(250,250,252),0.35)
    return base
def pebbles(seed):
    # Packed pebbles: every pixel belongs to its nearest pebble centre (wrapping, so it tiles); thin dark
    # cracks where two pebbles meet, each pebble rounded with light from the top left.
    r=random.Random(seed)
    def wrap(d): return (d+8)%16-8
    centres=[]
    while len(centres)<10:
        cx,cy=r.uniform(0,16),r.uniform(0,16)
        if all(wrap(cx-a)**2+wrap(cy-b)**2>16 for a,b,_ in centres): centres.append((cx,cy,mix(DARK,LIGHT,r.uniform(0.45,1.0))))
    px=[[None]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            ds=sorted(((wrap(x+0.5-cx))**2+(wrap(y+0.5-cy))**2, i) for i,(cx,cy,_) in enumerate(centres))
            (d1,i1),(d2,_)=ds[0],ds[1]
            cx,cy,tone=centres[i1]
            dx,dy=wrap(x+0.5-cx),wrap(y+0.5-cy)
            edge=math.sqrt(d2)-math.sqrt(d1)
            if edge<0.6: px[y][x]=mix(tone,(128,128,144),0.85); continue
            light=-(dx+dy)/5.0
            c=mix(tone,(252,252,254),0.35*light) if light>0 else mix(tone,DARK,-0.45*light)
            if edge<1.4: c=mix(c,(170,170,184),0.45)
            px[y][x]=c
    return px
out=sys.argv[1]
save(stone(11), out+'/marble.png')
save(polished(33), out+'/polished_marble.png')
save(bricks(13), out+'/marble_bricks.png')
save(pebbles(14), out+'/marble_pebbles.png')
