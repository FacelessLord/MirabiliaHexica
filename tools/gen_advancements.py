"""Writes data/mirabilia/advancements/*.json from the table below. Usage: py tools/gen_advancements.py"""
import json, os, re

ROOT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources')
OUT = os.path.join(ROOT, 'data', 'mirabilia', 'advancements')
DONE = {"done": {"trigger": "minecraft:impossible"}}

def placed(block): return {"placed": {"trigger": "minecraft:placed_block", "conditions": {"location": [
    {"condition": "minecraft:location_check", "predicate": {"block": {"blocks": [block]}}}]}}}
def has(item=None, **pred):
    p = dict(pred)
    if item: p["items"] = [item]
    return {"has": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [p]}}}
def inside(structure): return {"inside": {"trigger": "minecraft:location", "conditions": {"player": [
    {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"location": {"structure": structure}}}]}}}

# name: (parent, icon, frame, hidden, criteria, requirements or None for all)
TABLE = {
 "root": (None, "hexcasting:charged_amethyst", "task", False,
          {**has(tag="mirabilia:all"), "charged_air": {"trigger": "minecraft:impossible"}}, [["has", "charged_air"]]),
 # Media
 "lightning_rod": ("root", "mirabilia:media_discharger", "task", False, placed("mirabilia:media_discharger"), None),
 "high_voltage": ("lightning_rod", "minecraft:lightning_rod", "task", True, DONE, None),
 "background_harvest": ("root", "mirabilia:ambient_collector", "task", False, DONE, None),
 "stuffy": ("root", "minecraft:amethyst_cluster", "task", False, DONE, None),
 "media_sources": ("root", "mirabilia:media_sources_notes", "goal", True,
                   {"has_notes": has("mirabilia:media_sources_notes")["has"]}, None),
 "supply_chain": ("media_sources", "minecraft:chest", "task", False, DONE, None),
 "fully_provided": ("supply_chain", "hexcasting:battery", "task", False, DONE, None),
 # Lenses and the Sun
 "wider_view": ("root", "mirabilia:greater_lens", "task", False, has("mirabilia:greater_lens"), None),
 "see_through": ("wider_view", "minecraft:enchanted_book", "task", False,
                 has(enchantments=[{"enchantment": "mirabilia:deepsight"}]), None),
 "inscription": ("root", "hexcasting:lens", "task", False, DONE, None),
 "well_armed": ("root", "minecraft:iron_chestplate", "task", False, DONE, None),
 "caltrop": ("root", "minecraft:sunflower", "task", True, DONE, None),
 "different_every_day": ("caltrop", "minecraft:clock", "challenge", True, DONE, None),
 # Spellcraft
 "short_speech": ("root", "minecraft:writable_book", "task", False, DONE, None),
 "full_vocabulary": ("short_speech", "minecraft:written_book", "goal", False, DONE, None),
 "hoarfrost": ("root", "minecraft:snow_block", "task", False, DONE, None),
 "silence": ("root", "mirabilia:silence_stone", "task", False, placed("mirabilia:silence_stone"), None),
 # Tanako's World
 "glass_dream": ("root", "mirabilia:dream_glass", "task", False,
                 {"entered": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "mirabilia:tanakos_world"}}}, None),
 "fell_through": ("glass_dream", "mirabilia:deep_dream_glass", "task", True, DONE, None),
 "cold_welcome": ("glass_dream", "minecraft:powder_snow_bucket", "task", False, DONE, None),
 "came_back_wrong": ("glass_dream", "minecraft:fermented_spider_eye", "task", True,
                     {"wrong": {"trigger": "minecraft:effects_changed", "conditions": {"effects": {"mirabilia:came_back_wrong": {}}}}}, None),
 "act_of_will": ("glass_dream", "minecraft:ender_eye", "task", False, DONE, None),
 "mind_castle": ("glass_dream", "mirabilia:polished_marble", "task", False, inside("mirabilia:marble_castle"), None),
 "listening_post": ("glass_dream", "mirabilia:glyphstone", "goal", False, inside("mirabilia:listening_post"), None),
 "noon": ("root", "mirabilia:solar_collector", "task", False, DONE, None),
 "crystal_of_light": ("noon", "minecraft:amethyst_shard", "task", False, DONE, None),
 "kind_thoughts": ("noon", "minecraft:beacon", "challenge", False, DONE, None),
 # The Glass Man
 "someone_watching": ("glass_dream", "minecraft:glass_pane", "task", True, DONE, None),
 "just_in_time": ("someone_watching", "minecraft:feather", "task", False, DONE, None),
 "new_shell": ("someone_watching", "minecraft:glass", "task", True, DONE, None),
 "a_deal": ("new_shell", "hexcasting:focus", "task", False, DONE, None),
 "glass_village": ("a_deal", "minecraft:bell", "challenge", False, DONE, None),
}

# The feature each advancement belongs to: it isn't loaded while that's off (see config/Features.kt). No
# advancement hangs from one of a different feature, so switching one off never strands another.
FEATURE = {
 "lightning_rod": "media_discharger", "high_voltage": "media_discharger", "background_harvest": "ambient_collector",
 "stuffy": "ambient_media", "media_sources": "wellsprings", "supply_chain": "wellsprings", "fully_provided": "wellsprings",
 "see_through": "deepsight", "inscription": "imbue_lens", "well_armed": "imbue_armor_or_tool",
 "caltrop": "sun_caltrop", "different_every_day": "sun_caltrop", "short_speech": "macros", "full_vocabulary": "macros",
 "glass_dream": "tanakos_world", "fell_through": "dream_pull", "cold_welcome": "tanakos_world",
 "came_back_wrong": "tanakos_world", "act_of_will": "tanakos_world", "mind_castle": "tanakos_world",
 "listening_post": "tanakos_world", "noon": "solar_collector", "crystal_of_light": "crystal_of_light",
 "kind_thoughts": "solar_collector", "someone_watching": "glass_man", "just_in_time": "glass_man",
 "new_shell": "glass_man", "a_deal": "glass_man", "glass_village": "glass_man",
 "silence": "warding_stones",
}

for name, (parent, icon, frame, hidden, criteria, reqs) in TABLE.items():
    adv = {}
    if name in FEATURE:
        adv["fabric:load_conditions"] = [{"condition": "mirabilia:feature", "feature": FEATURE[name]}]
        adv["conditions"] = [{"type": "mirabilia:feature", "feature": FEATURE[name]}]
    if parent: adv["parent"] = f"mirabilia:{parent}"
    display = {"icon": {"item": icon},
               "title": {"translate": f"advancements.mirabilia.{name}.title"},
               "description": {"translate": f"advancements.mirabilia.{name}.description"},
               "frame": frame, "show_toast": True, "announce_to_chat": frame != "task" or not hidden, "hidden": hidden}
    if parent is None:
        display["background"] = "mirabilia:textures/block/marble.png"
    adv["display"] = display
    adv["criteria"] = criteria
    adv["requirements"] = reqs or [[k] for k in criteria]
    with open(os.path.join(OUT, name + '.json'), 'w', newline='\n') as f:
        json.dump(adv, f, indent=2)
        f.write('\n')

# Every item of ours, for the root
items_kt = open(os.path.join(ROOT, '..', 'kotlin', 'com', 'mirabiliahexica', 'registry', 'MirabiliaItems.kt'), encoding='utf-8').read()
names = re.findall(r'(?:register|blockItem)\("([a-z_]+)"\)', items_kt)
tag = os.path.join(ROOT, 'data', 'mirabilia', 'tags', 'items', 'all.json')
os.makedirs(os.path.dirname(tag), exist_ok=True)
with open(tag, 'w', newline='\n') as f:
    json.dump({"replace": False, "values": [f"mirabilia:{n}" for n in names]}, f, indent=2)
    f.write('\n')
print(len(TABLE), 'advancements,', len(names), 'items')
