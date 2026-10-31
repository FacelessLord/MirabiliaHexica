# Generators

One-off scripts that wrote some of the mod's resources. The output is committed, so they only need running again
to change it. Plain Python 3, no packages.

| Script | Writes | Run (from the repo root) |
|---|---|---|
| `gen_dream_worldgen.py` | Tanako's World: dimension, dimension type, noise settings (from vanilla's overworld), biomes, features | `py tools/gen_dream_worldgen.py <vanilla worldgen dir> common/src/main/resources/data/mirabilia` |
| `gen_dream_shrine.py` | The Dream Shrine template (`structures/dream/shrine.nbt`), its structure, set, pool, biome tag and loot | `py tools/gen_dream_shrine.py common/src/main/resources/data/mirabilia` |
| `gen_dream_textures.py` | Dream glass textures | `py tools/gen_dream_textures.py common/src/main/resources/assets/mirabilia/textures/block` |
| `gen_marble_textures.py` | Marble, Polished Marble, Marble Bricks and Marble Pebbles textures | `py tools/gen_marble_textures.py common/src/main/resources/assets/mirabilia/textures/block` |
| `gen_lost_dreamer_texture.py` | The Lost Dreamer's glowing crack layer | `py tools/gen_lost_dreamer_texture.py common/src/main/resources/assets/mirabilia/textures/entity/lost_dreamer_glow.png` |
| `gen_ink_textures.py` | Inkstone and the four Glyphstone sigils (`glyphstone_0`–`3`) | `py tools/gen_ink_textures.py common/src/main/resources/assets/mirabilia/textures/block` |
| `gen_ward_textures.py` | Warding and Silence Stone textures | `py tools/gen_ward_textures.py common/src/main/resources/assets/mirabilia/textures/block` |

`<vanilla worldgen dir>` is `data/minecraft/worldgen` extracted from the 1.20.1 client jar
(Loom keeps one at `~/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar`).
- `gen_machine_textures.py`: the Media Discharger, Ambient and Solar Collectors and Crystallizer.
- `gen_advancements.py`: every advancement JSON, from one table, and the `mirabilia:all` item tag.
- `gen_icon.py`: the mod icon (128 px in the jar, and a 512 px copy for Modrinth).
