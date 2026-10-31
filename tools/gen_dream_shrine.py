# Writes the Dream Shrine structure template (gzipped NBT) and its worldgen/loot JSON.
import gzip, struct, json, os, sys
OUT=sys.argv[1]
TAG_END,TAG_BYTE,TAG_INT,TAG_STRING,TAG_LIST,TAG_COMPOUND=0,1,3,8,9,10
def s(x): b=x.encode(); return struct.pack('>H',len(b))+b
def payload(t,v):
    if t==TAG_INT: return struct.pack('>i',v)
    if t==TAG_STRING: return s(v)
    if t==TAG_LIST:
        et,items=v
        return bytes([et])+struct.pack('>i',len(items))+b''.join(payload(et,i) for i in items)
    if t==TAG_COMPOUND:
        return b''.join(bytes([ct])+s(k)+payload(ct,cv) for k,(ct,cv) in v.items())+bytes([TAG_END])
def C(**kw): return (TAG_COMPOUND, kw)
def I(v): return (TAG_INT, v)
def S(v): return (TAG_STRING, v)
def L(et, items): return (TAG_LIST, (et, items))
def unwrap(x): return x[1]

W=11; H=7
blocks={}  # (x,y,z) -> (name, props, nbt)
def put(x,y,z,name,props=None,nbt=None): blocks[(x,y,z)]=(name,props or {},nbt)
c=W//2
for x in range(W):
    for z in range(W):
        d=max(abs(x-c),abs(z-c))
        if d<=5 and (abs(x-c)+abs(z-c))<=8:
            put(x,0,z,'mirabilia:polished_marble' if d<=1 else 'mirabilia:marble_bricks' if d<=3 else 'mirabilia:marble_pebbles')
# four banded pillars on the diagonals
for (px,pz) in [(c-3,c-3),(c+3,c-3),(c-3,c+3),(c+3,c+3)]:
    for y in range(1,6):
        put(px,y,pz,'mirabilia:deep_dream_glass' if y in (3,5) else 'mirabilia:marble_bricks')
# a raised dais and the chest on it
for x in range(c-1,c+2):
    for z in range(c-1,c+2):
        put(x,1,z,'mirabilia:polished_marble' if (x,z)!=(c,c) else 'mirabilia:dusky_dream_glass')
put(c,2,c,'minecraft:chest',{'facing':'south','type':'single','waterlogged':'false'},
    C(LootTable=S('mirabilia:chests/dream_shrine')))
# air above the floor so the template clears whatever grew there
for x in range(W):
    for z in range(W):
        for y in range(1,H):
            if (x,y,z) not in blocks and (x,0,z) in blocks: put(x,y,z,'minecraft:air')
palette=[]; index={}
for name,props,_ in blocks.values():
    key=(name,tuple(sorted(props.items())))
    if key not in index:
        index[key]=len(palette)
        entry={'Name':S(name)}
        if props: entry['Properties']=C(**{k:S(v) for k,v in props.items()})
        palette.append(C(**entry))
blist=[]
for (x,y,z),(name,props,nbt) in sorted(blocks.items()):
    e={'pos':L(TAG_INT,[x,y,z]),'state':I(index[(name,tuple(sorted(props.items())))])}
    if nbt: e['nbt']=nbt
    blist.append(C(**e))
root=C(DataVersion=I(3465), size=L(TAG_INT,[W,H,W]),
       palette=L(TAG_COMPOUND,[unwrap(p) for p in palette]),
       blocks=L(TAG_COMPOUND,[unwrap(b) for b in blist]),
       entities=L(TAG_COMPOUND,[]))
data=bytes([TAG_COMPOUND])+s('')+payload(*root)
os.makedirs(OUT+'/structures/dream',exist_ok=True)
with gzip.open(OUT+'/structures/dream/shrine.nbt','wb') as f: f.write(data)

def w(path,obj):
    p=os.path.join(OUT,path); os.makedirs(os.path.dirname(p),exist_ok=True)
    with open(p,'w',newline='\n') as f: json.dump(obj,f,indent=2); f.write('\n')
M='mirabilia:'
w('worldgen/template_pool/dream/shrine.json',{"fallback":"minecraft:empty","elements":[{"weight":1,"element":{
  "element_type":"minecraft:single_pool_element","location":M+"dream/shrine","processors":"minecraft:empty","projection":"rigid"}}]})
w('worldgen/structure/dream_shrine.json',{"type":"minecraft:jigsaw","biomes":"#"+M+"has_structure/dream_shrine",
  "step":"surface_structures","spawn_overrides":{},"terrain_adaptation":"beard_thin","start_pool":M+"dream/shrine",
  "size":1,"start_height":{"absolute":0},"project_start_to_heightmap":"WORLD_SURFACE_WG",
  "max_distance_from_center":80,"use_expansion_hack":False})
w('worldgen/structure_set/dream_shrines.json',{"structures":[{"structure":M+"dream_shrine","weight":1}],
  "placement":{"type":"minecraft:random_spread","spacing":24,"separation":8,"salt":871236541}})
w('tags/worldgen/biome/has_structure/dream_shrine.json',{"values":[M+"glass_expanse",M+"pillar_fields"]})
def item(name, weight, lo=1, hi=1, functions=()):
    e={"type":"minecraft:item","name":name,"weight":weight,"functions":list(functions)}
    if hi>1: e["functions"].insert(0,{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":lo,"max":hi}})
    if not e["functions"]: del e["functions"]
    return e
w('loot_tables/chests/dream_shrine.json',{"type":"minecraft:chest","pools":[
  {"rolls":{"type":"minecraft:uniform","min":2,"max":4},"entries":[
    item("hexcasting:charged_amethyst",20,1,4),
    item("hexcasting:amethyst_dust",20,4,12),
    item("minecraft:amethyst_shard",15,2,6),
    item("minecraft:echo_shard",6),
    item(M+"deep_dream_glass",8,2,6)]},
  {"rolls":1,"entries":[item(M+"media_sources_notes",1),{"type":"minecraft:empty","weight":11}]},
  {"rolls":1,"entries":[
    item("hexcasting:scroll",3,functions=[{"function":"hexcasting:pattern_scroll"}]),
    item("hexcasting:ancient_cypher",4,functions=[{"function":"hexcasting:hex_cypher"}]),
    # Deepsight is found nowhere else
    item("minecraft:book",1,functions=[{"function":"minecraft:set_enchantments","enchantments":{M+"deepsight":1}}]),
    {"type":"minecraft:empty","weight":6}]}]})
print(len(blocks),'blocks,',len(palette),'states')
