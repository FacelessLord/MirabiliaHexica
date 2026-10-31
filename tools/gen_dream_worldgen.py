import json, os, sys, copy
VAN=sys.argv[1]; OUT=sys.argv[2]
def w(path, obj):
    p=os.path.join(OUT, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p,'w',newline='\n') as f: json.dump(obj,f,indent=2); f.write('\n')
M='mirabilia:'
B=lambda n:{"Name":n}

# ---- dimension type: no sun or day cycle, a fixed night lit faintly by the galaxy
w('dimension_type/tanakos_world.json', {
  "ambient_light": 0.12, "bed_works": True, "coordinate_scale": 1.0, "effects": M+"tanakos_world",
  "fixed_time": 18000, "has_ceiling": False, "has_raids": False, "has_skylight": True,
  "height": 384, "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": 384, "min_y": -64,
  "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": 0, "natural": False,
  "piglin_safe": False, "respawn_anchor_works": False, "ultrawarm": False})

# ---- noise settings: the overworld's shape, made of dream glass, with no water, lava, ores or aquifers
ns=json.load(open(os.path.join(VAN,'noise_settings/overworld.json')))
ns['default_block']=B(M+'dream_glass')
ns['default_fluid']=B('minecraft:air')
ns['aquifers_enabled']=False
ns['ore_veins_enabled']=False
ns['disable_mob_generation']=True
# The lava floor sits at min(-54, sea level): putting the sea at the bottom removes it
ns['sea_level']=-64
def grad(name, below, above):
    return {"type":"minecraft:vertical_gradient","random_name":M+name,
            "true_at_and_below":{"absolute":below},"false_at_and_above":{"absolute":above}}
def block(n): return {"type":"minecraft:block","result_state":B(n)}
def cond(c, then): return {"type":"minecraft:condition","if_true":c,"then_run":then}
floor0={"type":"minecraft:stone_depth","offset":0,"surface_type":"floor","add_surface_depth":False,"secondary_depth_range":0}
floorDeep={"type":"minecraft:stone_depth","offset":0,"surface_type":"floor","add_surface_depth":True,"secondary_depth_range":0}
ns['surface_rule']={"type":"minecraft:sequence","sequence":[
  cond({"type":"minecraft:vertical_gradient","random_name":"minecraft:bedrock_floor",
        "true_at_and_below":{"above_bottom":0},"false_at_and_above":{"above_bottom":5}}, block("minecraft:bedrock")),
  # Dry basin and riverbed floors: pebbles over marble
  cond({"type":"minecraft:above_preliminary_surface"},
    cond({"type":"minecraft:biome","biome_is":[M+"marble_basins"]},{"type":"minecraft:sequence","sequence":[
      cond(floor0, block(M+"marble_pebbles")),
      cond(floorDeep, block(M+"marble"))]})),
  # The glass darkens with depth
  cond(grad("deep_glass", 0, 16), block(M+"deep_dream_glass")),
  cond(grad("dusky_glass", 72, 96), block(M+"dusky_dream_glass")),
  block(M+"dream_glass")]}
w('worldgen/noise_settings/tanakos_world.json', ns)

# ---- features
w('worldgen/configured_feature/marble_deposit.json', {"type":"minecraft:ore","config":{
  "discard_chance_on_air_exposure":0.0,"size":64,
  "targets":[{"state":B(M+"marble"),"target":{"predicate_type":"minecraft:tag_match","tag":M+"dream_glass"}}]}})
w('worldgen/placed_feature/marble_deposit.json', {"feature":M+"marble_deposit","placement":[
  {"type":"minecraft:count","count":3},{"type":"minecraft:in_square"},
  {"type":"minecraft:height_range","height":{"type":"minecraft:uniform","min_inclusive":{"absolute":-40},"max_inclusive":{"absolute":160}}},
  {"type":"minecraft:biome"}]})
# Rock pillars: a custom feature, so each one is shaped differently (DreamPillarFeature)
w('worldgen/configured_feature/rock_pillar.json', {"type":M+"dream_pillar","config":{}})
def pillars(name, count_max, rarity):
    w(f'worldgen/placed_feature/{name}.json', {"feature":M+"rock_pillar","placement":[
      {"type":"minecraft:rarity_filter","chance":rarity},
      {"type":"minecraft:count","count":{"type":"minecraft:uniform","value":{"min_inclusive":1,"max_inclusive":count_max}}},
      {"type":"minecraft:in_square"},{"type":"minecraft:heightmap","heightmap":"WORLD_SURFACE_WG"},
      {"type":"minecraft:biome"}]})
pillars('rock_pillars_dense', 3, 1)
pillars('rock_pillars_sparse', 1, 12)

# ---- biomes
EMPTY_SPAWNS={k:[] for k in ["ambient","axolotls","creature","misc","monster","underground_water_creature","water_ambient","water_creature"]}
def biome(name, fog, sky, extra):
    steps=[[] for _ in range(11)]
    steps[2]=["minecraft:amethyst_geode"]          # local_modifications
    steps[6]=[M+"marble_deposit"]                   # underground_ores
    for i,f in extra: steps[i].append(f)
    w(f'worldgen/biome/{name}.json', {
      # Warm on paper: vanilla piles snow on cold biomes whenever the overworld rains, precipitation or not.
      # (The dream's cold is its own mechanic.)
      "has_precipitation":False,"temperature":0.8,"downfall":0.0,
      "effects":{"fog_color":fog,"sky_color":sky,"water_color":0x8c7ad6,"water_fog_color":0x2a1f4a,
                 "ambient_particle":{"options":{"type":"minecraft:end_rod"},"probability":0.0008}},
      "spawners":EMPTY_SPAWNS,"spawn_costs":{},
      "carvers":{"air":["minecraft:cave","minecraft:cave_extra_underground","minecraft:canyon"]},
      "features":steps})
biome('glass_expanse', 0x2b2340, 0x120c24, [(9, M+"rock_pillars_sparse")])
biome('pillar_fields', 0x2b2340, 0x120c24, [(9, M+"rock_pillars_dense")])
biome('marble_basins', 0x30283f, 0x120c24, [])

# ---- dimension: biomes placed by the overworld's own climate noise
FULL=[-1.0,1.0]
def point(biome, **p):
    params={"temperature":FULL,"humidity":FULL,"continentalness":FULL,"erosion":FULL,"weirdness":FULL,"depth":0.0,"offset":0.0}
    params.update(p); return {"biome":M+biome,"parameters":params}
points=[point("marble_basins", continentalness=[-1.2,-0.19])]
# Rivers (weirdness near zero, inland) also dry into marble beds
points.append(point("marble_basins", continentalness=[-0.19,1.0], weirdness=[-0.05,0.05]))
for wr in ([-1.0,-0.05],[0.05,1.0]):
    points.append(point("glass_expanse", continentalness=[-0.19,1.0], humidity=[-1.0,0.1], weirdness=wr))
    points.append(point("pillar_fields", continentalness=[-0.19,1.0], humidity=[0.1,1.0], weirdness=wr))
w('dimension/tanakos_world.json', {"type":M+"tanakos_world","generator":{"type":"minecraft:noise",
  "settings":M+"tanakos_world","biome_source":{"type":"minecraft:multi_noise","biomes":points}}})

# ---- tags
w('tags/blocks/dream_glass.json', {"values":[M+"dream_glass",M+"dusky_dream_glass",M+"deep_dream_glass"]})
