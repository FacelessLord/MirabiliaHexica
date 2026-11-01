# Mirabilia Hexica: design brief

Handoff document. A new Claude session (or contributor) should read this first: it holds the decisions
already made. Update it when a decision changes or an open question is answered.

## Decided

* **Name:** Mirabilia Hexica ("hex wonders"). **Mod ID:** `mirabilia` (pattern IDs look like `mirabilia:freeze`).
  **Package:** `com.mirabiliahexica`.
* **Build:** Architectury Loom multiloader, `common/` + `fabric/` + `forge/`, Kotlin, Mojang mappings + Parchment.
  All versions live in `gradle/libs.versions.toml`.
* **Lang files:** plain `assets/mirabilia/lang/en_us.json` (the pkJson5 flattening plugin Hex uses is broken on Gradle 8.14).

## Targets

* Minecraft 1.20.1, Hex Casting 0.11.x first (currently built against 0.11.4). Loaders: Fabric + Forge.
  Test that the Forge jar runs on NeoForge 1.20.1.
* Later: Minecraft 1.21.1, Hex Casting 0.12 (Fabric + NeoForge; HexMod `1.21` branch, still `0.12.0-devel`
  as of Oct 2026). 0.12 changes Thoth's Gambit argument order.

## Reference code (read before writing anything)

* Hex Casting: https://github.com/FallingColors/HexMod (`main` = 1.20.1 / 0.11.x, `1.21` = 1.21.1 / 0.12)
  * Pattern registry: `Common/.../common/lib/hex/HexActions.java` (`HexPattern.fromAngles(sig, HexDir)`)
  * Per-world great spells: tag `data/hexcasting/tags/action/per_world_pattern.json`
  * Book: Patchouli, `assets/hexcasting/patchouli_books/thehexbook`; lang `en_us.flatten.json5`
* HexDummy (official addon template this project is adapted from): https://github.com/FallingColors/HexDummy
* HexTweaks (grand spells, Infuse Will): https://github.com/walksanatora/HexTweaks (`1.20` branch)
* Hex Research (Imbue Mind, Cognitive Inducer): https://github.com/dashkal16/HexResearch
* Hexal (wisps; closest thing to persistent spells): https://github.com/Talia-12/Hexal

## Hex Casting facts the design relies on (checked in source)

* Lens zoom: `HexAttributes.GRID_ZOOM` (range 0.5–4.0, default 1.0). Scrying Lens adds `+0.33 MULTIPLY_BASE`
  in head slot or either hand (`ItemLens`). Grid size = `baseScale / gridZoom` in `GuiSpellcasting.hexSize()`.
* Casting hooks for addons: `CastingEnvironment.addCreateEventListener((env, tag) -> env.addExtension(...))`.
  Components in `CastingEnvironmentComponent`: `IsVecInRange`, `HasEditPermissionsAt`, `ExtractMedia.Pre/Post`,
  `PostExecution`, `PostCast`.
* Ambit: default 32 blocks (`HexAttributes.AMBIT_RADIUS`), sentinel radius 16.
* Overcast (health to media): `PlayerBasedCastEnv.extractMediaFromInventory`, rate
  `HexConfig.common().mediaToHealthRate()`, damage type `HexDamageTypes.OVERCAST`.
* Casting items (`ItemPackagedHex.use`) run on `CastingVM.empty(env)`: a fresh stack and ravenmind, separate
  from the staff.
* Loot functions to reuse: `AddPerWorldPatternToScrollFunc` (great spell scrolls), `AddHexToAncientCypherFunc`,
  `AmethystReducerFunc`.
* Flay Mind recipes: `BrainsweepRecipe`, recipe type `HexRecipeStuffRegistry.BRAINSWEEP_TYPE`.
* Media units: `MediaConstants.DUST_UNIT` = 10,000; Charged Amethyst = 10 dust.

## Features (in build order)

### 1. Simple patterns

* Sneaking check: entity → boolean (`isShiftKeyDown` / crouching).
* Jumping check: entity → boolean, heuristic: just left the ground and moving upward. (The server does not
  receive the jump key; a heuristic was accepted.)
* Freeze: position, radius → nothing. Water becomes ice; dry top faces get a snow layer; casting again adds
  layers (additive, up to 8). Cost scales with radius.
* Thaw: the reverse (remove snow one layer at a time, ice back to water).

### 2. Greater Lens

* New wearable lens item: more grid zoom than the Scrying Lens (proposed +1.0 instead of +0.33; attribute max
  is 4.0). Same slots as the lens (head, either hand, bauble slot via `HexBaubleItem`).
* Made only by a great spell (no crafting recipe).

### 3. Lens imbuement (replaces the earlier enchantments + display spell idea)

* A great spell imbues the Greater Lens with a hex. While worn, the lens runs it every so often (paying media) and
  draws the `[position or entity, value]` pairs it leaves over the wearer's screen at those places. Players build
  their own "sight": e.g. entities within 32 blocks paired with their names shows who's behind a wall.

### 4. Phials

* Merge two Phials into one holding both amounts, or fill from a whole dropped stack of amethyst at once.
  Capacity cap. One great spell (Join Phials) does both.
* Blood Charge (great spell): spend chosen health to fill a Phial at the overcast rate; never takes the last
  half-heart.

### 5. Persistent spells (Ra-inspired)

* A "standing spell" bound to a block or to the caster (not an entity like Hexal wisps). Media to start, upkeep
  per tick (from a Phial or the caster). Stops when media runs out or when dispelled; patterns to query remaining
  time and add media.
* For balance reference, a Hexal Cyclic Wisp costs 3 dust to summon and about 0.65 dust/second upkeep, running
  its hex up to 20 times a second.

### 6. Magic suppression (materials come from Tanako's World) (built: see "Feature 6" below)

* Uses `IsVecInRange` / `HasEditPermissionsAt`:
  * Warding Stone: positions within about 8 blocks count as out of range for every caster (optional owner
    exemption).
  * Silence Stone: casters inside the area have nothing in range at all; stack and math patterns still work,
    but every spell fails.
  * Warded armor: the wearer counts as out of range for other players' spells.
    *Note:* range checks see the environment and a position, not which entity is targeted; per-wearer protection
    probably needs an extra hook on entity-targeting spells. Verify in source before building.

### 7. Tanako's World (dimension, biggest piece, last) (first part built: see "Feature 7" below)

Inspired by qntm's novel Ra (chapter "Deeper Magic"). Use the ideas and names as a tribute; write all text
fresh, never copy lines from the novel.

* Entry: sleep in a bed near an Akashic Record. (Optional: a great spell has a small chance to pull the caster in.)
* Arrival: a random safe position on every visit.
* Terrain: the overworld noise router with default block = dream glass and default fluid = air (oceans and
  rivers become dry basins). Surface rules: marble pebbles on riverbeds and basin floors, glass shades by height.
  No ore veins or aquifers; caves and ravines can stay.
* Biomes: only ours, so no vanilla structures or features appear. Features: stacked rock pillars, marble
  deposits, amethyst variants.
* Structures: built with structure blocks; chests with Hex Casting loot (Artifacts, great spell scrolls via
  `AddPerWorldPatternToScrollFunc`, cyphers, amethyst variants, oddities). HexTweaks grand spells are per-player,
  so a scroll would fill in for whoever opens it first (needs a fallback when HexTweaks is absent).
* The artifact / listening station: one very rare landmark, a huge ink-black structure, scaled to roughly
  200–300 blocks across. It holds the Akashic Records reading and the best loot.
  *Note:* jigsaw structures in 1.20.1 reach at most 128 blocks from their centre (~256 across); beyond that needs
  custom structure-piece code or a jigsaw shell around a coded core.
* Sky: custom sky renderer (per loader: Fabric `DimensionRenderingRegistry`, Forge
  `RegisterDimensionSpecialEffectsEvent`). A procedural three-armed spiral galaxy that rotates slowly around a
  fixed centre; no sun, moon or day cycle. The fixed centre is the navigation reference.
* Fog: thick, starting around 40–60 blocks.
* Cold: freezing builds over time unless you are protected; when fully frozen you are thrown out with damage.
* Magic: normal hexes fizzle; Phials and casting items can't be used. A few dream-only "willing" patterns change
  the world (raise terrain, open passages, calm dreamers) and include Wake.
* Danger scales with carried media.
* Corrupted villagers: the dreamers who came back wrong; may be tied to minds taken with Flay Mind. They can be
  fought, avoided or restored.
* Reading the records: the world's scrambled great spell patterns; a record of recent hexes cast at a location.
* Leaving:
  1. Hold sneak and stand still for about 10 s → wake in your bed (spawn point if the bed is gone). No penalty.
  2. Wake pattern (learned in the dream) → wake instantly.
  3. Death → wake with a "came back wrong" curse (patterns sometimes misfire until cured) and lose rare dream
     items. Full cold → thrown out with damage.
  4. Listening station exit → wake at any Akashic Record linked before. Must be hard to find:
     * very rare (about one per 4,000–6,000 blocks) and hidden by fog;
     * located via Echo Fragments in smaller structures, each giving a bearing relative to the galaxy plus a
       rough distance (needs 2–3 fragments to narrow down; valid for the current visit only);
     * using it requires a Focus holding your own true name (Mind's Reflection).
* Item return rule: everything carried comes back (decided while building; see "Feature 7" below).

## Decisions made while building (provisional: review and change freely)

Choices made without the user, so they can be reviewed in one place. Each says where to change it.

### Feature 1 (done, covered by GameTests: `gradlew :fabric:runGametest`)

| Pattern | ID | Signature (start dir) | Notes |
|---|---|---|---|
| Skulker's Purification | `mirabilia:get_entity_sneaking` | `awqaa` (NE) | Stadiometer's `awq` + tail. True if `isShiftKeyDown` or pose is CROUCHING. |
| Leaper's Purification | `mirabilia:get_entity_jumping` | `awqwqw` (NE) | Airborne and rising faster than 0.05 blocks/tick (Hex's tracked player velocity). Not counted: in water/lava, riding, climbing, elytra, creative flight, Levitation. |
| Freeze | `mirabilia:freeze` | `wqwqwqwqw` (E) | Mirror of Thaw. |
| Thaw | `mirabilia:thaw` | `wewewewew` (E) | |

* Names follow Hex's naming style ("X's Purification"). Change in `assets/mirabilia/lang/en_us.json`.
* Signatures checked against all of Hex 0.11.4's patterns, Numerical Reflection and Bookkeeper's Gambit. Not
  checked against other addons (Hexal, MoreIotas, …): a clash there silently overrides one of the two.
* **Freeze/Thaw radius:** max 8 blocks (sphere, block centres). Every block inside is checked against ambit and
  edit permissions individually, so Warding Stones (feature 6) will clip the area automatically.
* **Cost:** `1 dust + radius² / 4 dust` → 1.25 dust at r=1, 5 dust at r=4, 17 dust at r=8. Same for both.
  Change in `FrostArea.cost`.
* **Freeze:** only water *source* blocks become ice (flowing water is left alone). Snow goes on any air block that
  vanilla snow could survive in (so not on ice, and not in caves only if there's no floor). Existing snow gains
  one layer per cast; snow stops at 8 layers and never stacks above a full layer or a snow block (no pillars).
  All changes are decided from the world as it was before the cast.
* **Thaw:** snow layer −1 (1 layer → air), snow block → 7 layers, ice and frosted ice → water (air in the
  Nether). Packed and blue ice are untouched.

### Feature 2: Greater Lens (done, covered by GameTests)

* Item `mirabilia:greater_lens`, a subclass of Hex's `ItemLens`, so it keeps every lens behaviour (head slot,
  either hand, dispenser equipping, scrying sight / HUD overlays). In Hex's creative tab.
* **Zoom:** `+1.0 MULTIPLY_BASE` → grid zoom 2.0 (Scrying Lens: 1.33). Has its own modifier UUID, so wearing a
  Scrying Lens too stacks to 2.33 (attribute max 4.0). Change in `ItemGreaterLens.GRID_ZOOM`.
* **No recipe.** Made only by the great spell *Craft Greater Lens* (`mirabilia:craft/greater_lens`): turns the
  Scrying Lens in the other hand into a Greater Lens; costs 10 Charged Amethyst.
* **Texture:** a 16×16 placeholder (violet glass, gold ring) generated by script. Replace with real art.
* Trinkets face slot (Fabric) and Curios head slot (Forge) via tags; on Fabric we register the Trinket ourselves
  because Hex only scans items that exist when it initializes.
* Drawn exactly like the Scrying Lens when worn: same head-slot model transform (`models/item/greater_lens.json`),
  and our own copies of Hex's face renderers for the Curios slot (`forge/.../interop/CuriosRenderers.kt`) and the
  Trinkets slot (`fabric/.../interop/LensTrinketRenderer.kt`), because Hex registers its renderers for the
  Scrying Lens only. Curios is in the Forge dev runtime for testing; it's not a dependency of the mod.

### Feature 4: Phials (done, covered by GameTests)

Both are great spells and work on the Phial in the caster's other hand:

| Great spell | ID | Args |
|---|---|---|
| Join Phials | `mirabilia:join_phials` | item entity |
| Blood Charge | `mirabilia:blood_charge` | entity, number (health points) |

* **Join Phials**, aimed at a dropped **Phial**: merges, so held capacity and media += the other's; the other is
  used up. Refused (mishap, nothing lost) if the result would pass the cap.
* **Join Phials**, aimed at dropped **raw media** (dust, shards, Charged Amethyst; anything Craft Phial accepts):
  absorbs as many *whole* items of the stack as fit, all at once; their media raises both capacity and charge.
  (This was once the separate ordinary spell Swell Phial; it's merged in so growing a Phial always takes the
  great spell.)
* **Capacity cap:** 2560 dust (4 stacks of Charged Amethyst). Change `PhialHelper.MAX_CAPACITY`. Costs 1 Charged
  Amethyst.
* **Blood Charge:** bleeds the given living entity in range: the caster, or a **peaceful** creature (not a
  player, not an `Enemy`, mob category friendly: animals, villagers, golems, fish…). Monsters and bosses mishap,
  so it can farm media from animals but can't soften up foes. Bleeding a villager counts as hurting it
  (`VILLAGER_HURT` gossip against the caster, angry particles), so golems may turn on repeat offenders. Takes `min(requested, health − 1, room in Phial)` health as overcast damage and inserts
  `health actually lost × Hex's overcast rate` (default 1 dust per health point). Costs no media. Only fills
  existing room.

### Feature 3: lens imbuement (server side covered by GameTests; drawing checked by eye)

The earlier lens enchantments (Lifesight, Aurasight, Intentsight, Scrutiny, Deepsight) were removed in favour of
imbuement, along with their crosshair readout, server-side scan, mixins and enchanting-table hook. The Greater
Lens is no longer enchantable.

* **Lens imbuement.** *Imbue Lens* (`mirabilia:imbue_lens`), **a great spell**: hex → nothing, with a lens
  (Scrying or Greater) in the other hand; costs 3 Charged Amethyst; an empty list clears the lens. An imbued lens glints and its
  tooltip says so.
  * While worn (head, either hand, Trinkets/Curios), the server runs its hex **as the wearer every 10 ticks**,
    with a fresh stack. If the top of the stack is a list of `[position or entity, value]` pairs, each value is
    overlaid **on the wearer's screen** where that position or entity appears (entities followed smoothly), at a
    constant size, over everything, up to 128 blocks. A list value gives one line per item (max 16). Only the
    wearer sees it. Malformed or out-of-ambit pairs are skipped; max 64 labels.
  * **Only what the wearer can see:** the hex's results are all kept and sent; the client skips drawing a label
    whose place (the block it lies in doesn't count) or creature (eyes or body centre) it can't see from the
    wearer's eyes (see-through blocks such as glass, leaves and ice don't block the view; others block only
    where their shape is), checked every frame, so a label shows the moment its target comes into view. (Client-side, so
    a modified client could ignore it.)
  * **Deepsight** (`mirabilia:deepsight`, `lens/DeepsightEnchantment.kt`): lifts that, so labels show through
    walls. Greater Lens only (anvil, via `canEnchant`). Treasure, not tradeable, not discoverable: found only as
    an enchanted book in Dream Shrine chests (weight 1 of 14 in the shrine's second pool).
  * **Media:** 0.1 dust per run (0.2 dust/s) plus whatever the hex spends, from the wearer's inventory. Never
    overcasts.
  * **Mishaps are quiet:** no damage, no dropped items; the message goes to the action bar, as does Reveal. If
    the result can't be shown, the action bar says why.
  * Only one imbued lens runs (head first, then hands, then bauble slots).

### Feature 5: standing spells (done, covered by GameTests)

| Pattern | ID | Signature (E) | Stack |
|---|---|---|---|
| Raise Standing Spell | `mirabilia:standing/raise` | `qwaeawqwww` | hex (list), position, dust → |
| Raise Standing Spell on Entity | `mirabilia:standing/raise_entity` | `qwaeawqwwwqaq` | hex, entity, dust → |
| Vigil Purification | `mirabilia:standing/remaining` | `qwaeawqwwwd` | position \| entity → seconds \| null |
| Feed Standing Spell | `mirabilia:standing/feed` | `qwaeawqwwwew` | position \| entity, dust → |
| Dispel Standing Spell | `mirabilia:standing/dispel` | `qwaeawqwwwaa` | position \| entity → |

Rules (constants in `StandingSpells.kt`):
* **Raise cost:** 3 dust + the funding. The funding is the spell's whole budget (Hexal's wisp: 3 dust to summon).
* **Upkeep:** 0.5 dust/second while active (Hexal Cyclic Wisp: ~0.65). Spells cast by its hex also use the budget.
* **Runs its hex every 10 ticks** (twice a second; a Hexal wisp can run up to 20/s). Each run starts with a fresh
  stack holding the anchor: the block centre (vector) or the entity it rides on. Ravenmind persists between runs.
* **Anchors:** a block, or any entity (the caster, a mob, a dropped item…). The spell acts *as its owner*
  (`castingEntity`) only while riding on the owner; on a block or on anything else it has no caster.
* **Ambit:** 16 blocks around the anchor (Hex's sentinel radius). Edit permissions are the owner's when online.
* **Ends when** the budget runs out, the hex mishaps (including running out mid-hex), it's dispelled, or the
  entity it rides on dies (any living entity, players included) or is destroyed (item despawns, boat breaks…).
  Block-bound spells don't end when their owner dies. A mishap does nothing else: no one is there to hurt.
  Remaining media is lost.
* **Pauses** (no upkeep, no runs) while its block's chunk is unloaded, or while its entity is unloaded (a player
  who logged off, a mob in an unloaded chunk). Entities that change dimension are found again by UUID.
* **Limits:** one spell per block, one per entity, 8 per player in total. Only a player can raise one
  (circles and casting items can't), but anyone in ambit can query, feed or dispel any spell (counterplay).
* "Remaining time" = budget ÷ base upkeep, ignoring what the hex itself spends.
* Stored in the overworld's saved data (`mirabilia_standing_spells.dat`), so entity-bound spells can follow their
  entity between dimensions.
* Not done yet: any visual sign that a block has a standing spell (particles, a marker block, Scrying Lens
  overlay).

### Health and strings (done, covered by GameTests)

| Pattern | ID | Signature (start dir) | Stack |
|---|---|---|---|
| Health Purification | `mirabilia:get_entity_health` | `awqwaq` (NE) | entity → number \| null |
| Max Health Purification | `mirabilia:get_entity_max_health` | `awqwaqw` (NE) | entity → number \| null |
| Mason's Purification | `mirabilia:get_block_solid` | `wqqqqqw` (E) | position → bool (`BlockState.isSolid`) |
| Scribe's Purification | `mirabilia:string/from` | `ddwddw` (E) | any → string |
| Nominal Purification | `mirabilia:string/name` | `ddwddwqq` (E) | entity → string |
| Nominal Purification II | `mirabilia:string/block_name` | `ddwddwqqa` (E) | position → string (block name) |
| Scholar's Distillation | `mirabilia:string/equals` | `ddwddwew` (E) | string, string → bool (ignores case and edge spaces) |
| Copyist's Distillation | `mirabilia:string/equals_strict` | `ddwddwwew` (E) | string, string → bool (exact) |
| Splitting Distillation | `mirabilia:string/split` | `ddwddwqe` (E) | string, separator → list |
| Numeral Purification | `mirabilia:string/parse` | `ddwddweq` (E) | string → number \| null |
| Inscription Purification | `mirabilia:string/sign` | `ddwddwaw` (E) | position → string \| null (sign's front text) |

* New iota type `mirabilia:string`, shown in gold without quotes (so it reads cleanly on lens labels), capped at
  1024 characters. Always registered, even when the patterns are off, so servers and clients agree.
* All string patterns are free.
* **Hex's own arithmetic patterns work on strings** (registered arithmetic `mirabilia:string`, like Hex's list
  arithmetic): Additive Distillation joins (if only one side is a string, the other is written out first),
  Length Purification = length, Selection Distillation = one character (null out of range), Selection
  Exaltation = slice (same index rules as lists, so out of range is a mishap), Retrograde Purification =
  reverse, Locator's Distillation = index of a substring or -1, Maximus/Minimus Distillation (and II) =
  alphabetical order (case-insensitive, case only breaks ties). Numbers turn into plain text ("7", "2.5"),
  not Hex's locale-dependent "7.00"/"7,00".
* Players write their own text on a sign and read it with Inscription Purification.
* Book: names, block solidity and comparisons sit in "Names and Likeness", next to Hex's Types entry; the
  string pages there hide with the config flag, Mason's Purification always shows.
* **Lens labels:** if the value of a pair is a list, the label shows one line per item (max 16), in one box.
* **Config** `config/mirabilia.json`, `"stringPatterns"`: `"auto"` (default: off if MoreIotas is installed),
  `true` or `false`. Read at startup; when off, the patterns aren't registered and the book's Strings entry is
  hidden (Patchouli flag `mirabilia:string_patterns`). Server and client should use the same setting.

### Great spells

`mirabilia:craft/greater_lens`, `mirabilia:join_phials`, `mirabilia:blood_charge` and `mirabilia:imbue_lens` are great spells: listed in
Hex's `per_world_pattern`, `requires_enlightenment` and `can_start_enlighten` tags (files under
`data/hexcasting/tags/action/` for Fabric and `data/hexcasting/tags/hexcasting/action/` for Forge). So their
shape is scrambled per world (players find it on Ancient Scrolls, which Hex's loot already fills from that tag),
only enlightened casters can cast them, and an unenlightened attempt mishaps like Hex's own great spells (held
items knocked loose). The registered signatures are prototypes only. Book: Patterns → Great Spells.

Third-party code adapted into this mod is listed, with its licenses, in [CREDITS.md](CREDITS.md).

### Feature 6: magic suppression (done, covered by GameTests)

Every casting environment gets a range hook when it is created (Hex's `addCreateEventListener` +
`IsVecInRange`), so the rules apply to staffs, circles, lenses, standing spells and other addons' casters alike.
Code: `wards/`. Book: Items → Wards, and Patterns → Spells → Warded Armor.

* **Warding Stone** (block): every position within **8 blocks** (sphere from the block centre) is out of range.
* **Silence Stone** (block): a cast happening within 8 blocks has nothing in range at all, its own caster
  included. "Where the cast happens" = the caster for player casts, otherwise `mishapSprayPos` (impetus,
  standing-spell anchor…). Stack and math patterns still work.
* **Owner exemption:** a stone remembers who placed it and spares that player's casts (and their standing
  spells) by default. The owner toggles this by using the stone with an empty hand (action-bar message).
* **Ward Armor** (`mirabilia:ward_armor`, `qwqqqwqaeaq`, normal spell): nothing → nothing, wards the unwarded
  armor piece in the other hand; 3 Charged Amethyst. Tooltip says "Warded". Wearing **one** warded piece is
  enough; it works on mobs too. The wearer can't be found (entity searches, raycasts) or targeted by anyone else;
  their own casts are unaffected. Aiming at a spot next to them still works.
* **Mixin needed after all:** Hex's range hooks see only positions, and a player's entity iota skips range
  entirely by default (`trueNameHasAmbit`). `MixinCastingEnvironment` hooks `isEntityInRange` and
  `assertEntityInRange`, so warded armor holds, and players standing inside a Warding/Silence area can't be
  reached through their entity iota either.
* Stones: 4.5 hardness, iron pickaxe, blast resistance 1200 (explosions aren't hexes and wouldn't be stopped
  anyway, but this stops them being blown up casually). The Warding Stone gives a little light.
* **Recipes** use Tanako's World materials: Warding = Marble + 4 Charged Amethyst + 4 Amethyst Dust; Silence =
  Echo Shard + 4 Charged Amethyst + 4 Deep Dream Glass.
* Stones are tracked while their chunk is loaded; a stone in an unloaded chunk does nothing (nobody can cast
  there anyway).

### Feature 7: Tanako's World, part 1 (done, covered by GameTests; sky and fog not yet seen in game)

Dimension `mirabilia:tanakos_world`. Code: `dream/` (rules), `client/DreamSky.kt` + `DreamEffects.kt` (looks).
Worldgen JSON is written by `tools/gen_dream_worldgen.py` and `tools/gen_dream_shrine.py` (see `tools/README.md`).
Book: Getting Started → Tanako's World (last entry).

* **Entry:** sleep (long enough that the night would pass) in a bed within **8 blocks** of an Akashic Record.
  The dreamer is moved before the night skips, so sleeping there doesn't skip the night for others either.
* **Arrival:** a random spot on open glass or pebble ground (not atop a pillar) within ±4000 blocks of the
  dream's origin, new each visit.
* **Terrain:** the overworld's noise router, default block Dream Glass, default fluid air, no aquifers, no ore
  veins, sea level moved to the world floor (which also removes the lava sea). Oceans and rivers are dry basins.
  Surface rule: bedrock floor; in Marble Basins, pebbles over marble on the floor; then the glass shades by
  height: Deep (below ~0–16), Dusky (below ~72–96), Dream Glass above. Caves and ravines carve it (blocks added
  to `overworld_carver_replaceables`).
* **Biomes** (only ours, picked by the overworld's climate noise): Marble Basins (oceans + rivers), Glass Expanse
  (dry), Pillar Fields (humid, dense rock pillars). Biome temperature is 0.8: the dream shares the overworld's
  weather, and vanilla settles snow on any cold biome while it rains, even without precipitation (snow left in
  older worlds is melted around dreamers, 32 random columns per tick within 64 blocks). The dream's cold is its
  own mechanic. No natural mob spawns (see Lost Dreamers), no rain, faint
  end-rod motes. Features: rock pillars, marble deposits, vanilla amethyst geodes.
* **Rock pillars** (`worldgen/DreamPillarFeature.kt`, feature `mirabilia:dream_pillar`): each picks its own
  height (8–29), base radius (0.9–3.2), taper, lean (none in 30%, otherwise up to 0.45 blocks per block, kept
  within 12 blocks of the base), surface ripple and banding (deep glass every 3–7 blocks, rare dusky flecks).
  20% are broken stumps with ragged tops, 25% of the rest flare into a capital, and 12% of placements are
  boulders instead. Bases sink 3 blocks into the ground. Pillar Fields: 1–3 per chunk; Glass Expanse: 1 per
  ~12 chunks.
* **Blocks:** Dream Glass, Dusky Dream Glass, Deep Dream Glass (opaque to the renderer for speed; amethyst
  sounds), Marble (mottled, faintly veined), Marble Pebbles; dream-only. Polished Marble (4 marble) and Marble
  Bricks (4 polished), also by stonecutter. Ward stones are made from dream materials. Textures:
  `tools/gen_marble_textures.py`.
* **Lost Dreamers** (`entity/LostDreamer.kt`, `mirabilia:lost_dreamer`): villagers who never woke. A
  `ZombieVillager` subclass: hostile, 24 health, 40-block follow range, no reinforcements, no tools/armor, no
  babies or jockeys, doesn't burn or convert in water. While hunting it sometimes blinks up to 4 blocks away.
  Drawn stretched tall and thin by a per-entity amount, twitching now and then, with a glowing violet crack/eye
  layer over vanilla's zombie villager skin. **Mutations** (`client/DreamerMutation.kt`, picked from the UUID so they're the same
  for everyone): at most one head change (two heads side by side; four arms instead of a head; a second face on
  the back of the head; a second torso stacked on top, carrying a head and arms) and at most one body change (1–2
  extra pairs of arms down the sides; a smaller second self grown out of the back, facing away; arms hanging
  from the hips). About 1 in 6 has none, a third have one of each. Purely visual: the hitbox is unchanged. The
  extra parts are the model's own parts drawn again, so they follow the walk and attack animations. **Not curable**: a golden apple does nothing (curing would make the
  dream a free villager farm). They keep their profession's clothes but never its hat. Drops 0–2 rotten flesh (+looting); on a player kill, 8% (+3% per looting level) for an
  amethyst shard ×1–3, amethyst dust ×2–5 or (rarely) a Charged Amethyst.
* **Lost Dreamer spawning** (`dream/DreamSpawner.kt`): every 2 s per dreamer, 16–32 blocks away on dream ground.
  Chance 3% + 4% per minute dreamed this visit (max 40%, reached at ~9 min); at most 1 nearby (48 blocks), plus
  one per 2.5 minutes, up to 6. The time dreamed is saved with the visit.
* **Dream Shrine:** a small surface structure (11×11 polished marble and brick platform, four banded brick
  pillars, a chest on a dais),
  about one per 24×24 chunks in Glass Expanse and Pillar Fields. Loot: Charged Amethyst, dust, shards, Echo
  Shards, Deep Dream Glass; plus maybe a great spell scroll (Hex's `pattern_scroll`) or an Ancient Cypher.
* **Sky:** no sun/moon/clouds, fixed midnight (`fixed_time` 18000) with a little ambient light. Scattered stars
  and a three-armed logarithmic-spiral galaxy, heart fixed **north, ~35° up**, turning once per two hours of
  play. Fabric: `DimensionRenderingRegistry`; Forge: `DimensionSpecialEffects.renderSky`.
* **Fog:** the Nether's (`isFoggyAt` = true): closes in a few dozen blocks out. Dark violet from the biomes.
* **Magic:** every cast in the dream is silenced (nothing in range, entities included), so only patterns that
  need no reach work. Casting items (cyphers, trinkets, artifacts) won't start. Beds can't be used.
* **Cold:** builds up over **5 minutes** (4 per tick, one less per leather/freeze-proof armor piece, so full
  leather is immune); within 3 blocks of fire, lava, magma or a lit campfire it drops 100 per second. Shown as
  vanilla freezing (overlay and slowdown) but never deals vanilla freeze damage. Full → thrown out, hurt for up
  to 3 hearts (never fatal).
* **Leaving:** crouch without moving for **10 s** → wake beside the bed (else where they were standing; else
  their spawn). **Wake** (`mirabilia:wake`, `eeqeeweeqee`, free, mishaps harmlessly when awake). Cold (above).
  **Death** → normal respawn plus **Came Back Wrong** (20 min effect): each cast has a 20% chance to be silenced.
  Milk cures it.
* **Willing patterns** (dream-only, unaffected by the silence, no media; each adds 100 cold, i.e. 5 s; Will Solid 25): **Will
  Upward** (`mirabilia:will_upward`, `eeqeewaq`: vector → raises dream ground one block in a radius-2 disc) and
  **Will Apart** (`mirabilia:will_apart`, `eeqeeqwe`: vector → hollows a 3×3×3 of dream ground). Reach about 16 blocks
  from the dreamer. Only blocks in `mirabilia:dream_shapeable` (dream glass, marble, pebbles) answer.
* **Dreamer's senses** (dream-only, free, `casting/actions/queryentity/OpDreamSenses.kt`): **Footing**
  (`mirabilia:dream_footing`, `eeqeedw`: → the block the dreamer stands in) and **Bearing** (`dream_bearing`,
  `eeqeewe`: → exact look direction, unit length). The silence still hides the caster from ordinary patterns, so self-targeted spells stay
  dead in the dream; these give the willing patterns what they need instead.
* **Axis Purification** (`mirabilia:snap_to_axis`, `waqaww`, works anywhere): vector → the axis it mostly points
  along, unit length, sign kept; ties go x, then z, then y (so the ground wins); zero stays zero.
* **Will Solid** (`mirabilia:will_solid`, `eeqeeqa`): vector → one block of dream glass (shade by height, as the
  terrain) if the spot is empty; a quarter of the usual chill. Willing reach is measured from the dreamer's
  middle with 1.5 blocks of slack, and the usual chill is now 100 (5 s).
* **Dreamer's Gaze** (`mirabilia:dream_gaze`, `eeqeeww`): nothing → vector, the block the dreamer looks at within
  16 blocks (block position, like Archer's Distillation), or null. Needed because the silence also hides the
  caster from Mind's Reflection, Compass and raycasts, so willing patterns had no way to get a position.
* **Item return rule:** everything carried comes back on every kind of wake (death drops items as usual).
* **Marble castles** ("mind castles", `worldgen/MarbleCastle.kt`, structure type `mirabilia:marble_castle`):
  built in code from a seed (`DreamPiece`: only seed + origin are saved, every chunk rebuilds the layout and
  places its own part; per-block randomness is a position hash, so chunk order doesn't matter). Varies: wall
  size (half 7–13) and height (6–9), round corner towers (radius 2–4, banded with deep glass, crenellated or
  roofed with a dark-glass cone), round or square hollow keep (radius 3–5, 14–27 tall) with floors every 5
  blocks (none within 2 of the roof), windows, a ladder up the back wall and a doorway facing the gate; gate on a random side; 40% are
  ruined (10–35% of the upper stonework gone, more the higher). Foundations reach down to the ground; terrain
  adaptation `beard_thin` from the box bottom (= the floor, at the median ground height of a 3×3 sample). Chests: ground floor (`chests/marble_castle`), keep roof (`chests/marble_castle_keep`,
  can hold Deepsight). One per 36×36-chunk region, Glass Expanse and Pillar Fields.
* **Listening post** (`worldgen/ListeningPost.kt`, `mirabilia:listening_post`): a giant hollow **egg** of Inkstone
  (horizontal radius = circle radius + 5, height 1.1–1.25× that, 2-block shell) standing on a floor levelled to
  the median ground height of a 5×5 sample (inside cleared, dips built up with deep dream glass). The shell has
  a Glyphstone band every 12 blocks and 3.5% glyph specks (its light), an open crown (radius 3), and one arch on
  the south side (5 wide, 5 tall) with a 12-block cutting outwards in case the land buries it. The floor is
  Inkstone flecked (30%) with Deep Dream Glass, inlaid with the Glyphstone circle (radius 40–56): four rings, a
  hexagram, a hexagon, six spokes, twelve lesser circles; the figure turned by a random angle. Six 3×3 Inkstone
  obelisks (8–12 tall, glyph caps) on the middle ring. The inner (black) circle is a **dais** two blocks high,
  with a one-block step and a glowing rim. On it: an **Akashic Record** with two rings of Akashic Bookshelves
  (16) round it, six holding **library hexes** (`worldgen/DreamLibrary.kt`, written straight into the shelves'
  data; each under its own key pattern), and four `chests/listening_post` (great-spell scrolls, lots of media,
  quenched shards, 50% Deepsight). One per 96×96-chunk region (about every 1,500 blocks) in any dream biome;
  still meant to be found with Echo Fragments.
* **Library hexes**: each listening post shelves six, picked from its seed (`DreamLibrary.pick`): 2–4 of ours
  (below, all cast for real by `DreamLibraryTests`) and the rest drawn from Hex Casting's Ancient Cypher hexes
  (Shatter, Kindle, Illuminate, Blink, Radar, Detonate…; taken through its public `AddHexToAncientCypherFunc`,
  since the list is private). Keys are dealt at random from ten patterns (`dwdwd`, `awawa`, `eqeqe`, `qeqeq`,
  `wdwdw`, `wawqaw`, `eweqe`, `qdqdq`, `wawwa`, `qwqwq`), so a key means a different hex in each post. Ours:

  | Hex | For |
  |---|---|
  | Sight of Vitality: every living creature within 32 blocks paired with its health | a lens |
  | Sight of Danger: the same, monsters only | a lens |
  | Wellspring at Gaze: binds the container the caster looks at | any hex's start |
  | Dream Stair: 8 steps rising ahead (heading = Axis Purification of the Bearing with its vertical part removed; Footing + heading×i + (0, i−1, 0), Will Solid ×8) | the dream |
  | Dream Tunnel: 16 blocks long, 3×3, along the Bearing (Footing + body height + Bearing×2i, Will Apart ×8) | the dream |
  | Wellspring Gauge: `[[feet, dust left in the wellspring]]` | a lens (after binding) |
* **New blocks:** Inkstone and Glyphstone (light 7). Glyphstone has four sigil textures (ring + triangle, ring +
  diamond, double ring + hexagon, ring + crescent), and its blockstate picks one of the four, in one of four
  turns, at random by position (16 weighted variants). Pillars and boulders don't grow within 7 blocks of
  anything built (`mirabilia:dream_built` tag: polished marble, marble bricks, inkstone, glyphstone).
* Not built yet: Echo Fragments, the listening post's exit to linked Akashic Records, "calm dreamers" and other willing patterns, reading the records,
  danger scaling with carried media, losing rare dream items on death, HexTweaks scrolls.
* Tests: the GameTest server ignores datapack dimensions, so a test-only mixin (`MixinGameTestServer`) adds ours;
  structure generation is off in test worlds, so the shrine test asks the structure directly.

### Media sources: wellsprings (done, covered by GameTests)

Inspired by Ra's bound mana sources. Code: `source/` (`MediaSource`, `MediaSources`) and
`casting/actions/source/SourceActions.kt`. Book: Patterns → Wellsprings, **locked** until the player picks up
*On the Findings about Media Sources* (`mirabilia:media_sources_notes`; advancement `mirabilia:media_sources`).
The notes are found in castle keeps (30%), listening posts (30%) and Dream Shrines (1 in 12). (Vessel Binding,
which bound an inventory slot, was removed: the caster's inventory is drawn on anyway.)

| Pattern | ID | Signature (E) | Stack |
|---|---|---|---|
| Wellspring Binding | `mirabilia:source/bind` | `wawqwawa` | vector \| entity → |
| Wellspring Release | `mirabilia:source/unbind` | `wawqwaww` | → |
| Wellspring Reflection | `mirabilia:source/media` | `wawqwawe` | → number (dust left) \| null |
| Wellspring Tether | `mirabilia:source/tether` | `wawqwawaq` | vector \| entity \| null → (item in the other hand) |

* Once bound, every media cost in the rest of the hex is paid from the wellspring first (a Hex
  `ExtractMedia.Pre` hook added to every casting environment), the remainder the usual way (inventory, overcast,
  standing-spell budget, impetus…). Binding is free.
* Sources: a **container block** (chest, barrel, hopper… via
  `HopperBlockEntity.getContainerAt`, so double chests work), a **dropped stack**, or a **container entity**
  (chest minecart). Players can't be bound. Media items are drained as Hex drains them (whole items, so some
  waste on part-items). Blocks and entities are range-checked when binding **and every time media is drawn** (the
  casting's own reach: ambit, wards…); out of reach they give nothing and the hex pays the usual way.
* **Tethers** (`source/Tether.kt`): Wellspring Tether stores a container/entity source on a lens, imbued armor
  or tool, or Hex casting item (cypher, trinket, artifact); null removes it. Every hex that item runs starts
  bound to it (the create listener reads it when the image carries no binding: `ItemCastEnv` for ours,
  `PackagedItemCastEnv`'s casting hand for Hex's). The usual per-draw range check applies, so out of reach the
  item pays its usual way. A hex can still rebind. Tooltip shows the tether.
* "The hex" = one `CastingImage`; the binding is stored in its user data (`mirabilia:media_source`), which Hex
  carries along: one lens or standing-spell run, a circle's run, one casting-item use. **Staff:** Hex resets the
  image when the stack is cleared, so the binding lasts until then. Binding as the first pattern on an empty
  stack therefore ends at once; draw it with something on the stack, or inside a list that is then cast.

### Armor and tool imbuement (done, covered by GameTests)

Code: `imbue/` (`Imbuement`, `ArmorImbuement`, `ImbuedCastEnv`), `casting/actions/spells/OpImbueItem.kt`. Book:
Patterns → Great Spells → Armor and Tool Imbuement.

| Pattern | ID | Signature (prototype) | Stack |
|---|---|---|---|
| Imbue Armor (great) | `mirabilia:imbue_armor` | `qeewaqaawea` | entity (dropped amethyst), hex → |
| Imbue Tool (great) | `mirabilia:imbue_tool` | `qeewaqaawed` | entity (dropped amethyst), hex → |
| Recharge Imbued Item | `mirabilia:recharge_imbued` | `wawqwawdd` | entity \| null → |

* Imbuing stores the hex (same NBT as lens imbuement) in the armor piece / damageable tool in the other hand,
  and turns the dropped stack (consumed whole; whatever Craft Phial takes) into the item's own media store:
  the stack's media is both capacity and first filling. 3 Charged Amethyst. An empty hex clears it.
* **Own media**: the hex runs in `ImbuedCastEnv` (quiet like a lens) that pays from the item instead of the
  wearer: no inventory, no overcast. A wellspring the hex itself binds pays first, as in any hex. Each run
  still needs the item's own store to start (armor upkeep, tool not empty). Tool wear counts all the media
  the cast cost, whichever source paid it.
* **Armor**: every 10 ticks, each worn imbued piece runs its hex as the wearer; 0.1 dust upkeep per run;
  nothing shown.
* **Tool**: still a tool; right-click (use) casts the hex once (10-tick cooldown). Wear per cast: 1 durability
  + 1 per 10 dust spent. A dry tool does nothing and doesn't wear.
* **Recharge Imbued Item**: refills up to the capacity, from a dropped stack (whole items, like Recharge Item)
  or, given null, from the hex's bound wellspring (exactly what fits). Costs a shard (5 dust).
* Tooltip: hex size and `media / capacity` dust.

### The sign on the Sun (done; rotation covered by GameTests, drawing not seen in game)

While wearing any lens (head, hands, bauble slot), the overworld sun carries a **caltrop**: four glowing spikes
from its centre toward the corners of a tetrahedron (`lens/SunSign.kt`, drawn by `client/SunSignRenderer.kt`
from a client mixin at the end of `LevelRenderer.renderSky`, using vanilla's sun turns). Pale gold, additive,
spikes about 26 units long at sky distance 100 (the sun is 60 across); fades with rain; hidden in fog/lava/
blindness (vanilla skips the sky there) and behind terrain. Day N = `dayTime / 24000`; its orientation is the
base caltrop turned N × the golden angle (≈137.5°) about the axis (0.3, 1, 0.5), so it holds all day and
neighbouring days always differ. Lore only. The two caltrop advancements wait for the advancements step.

### The Glass Man (done, covered by GameTests; look not seen in game)

Code: `entity/GlassMan.kt`, `dream/GlassManSpawner.kt`, `dream/Possession.kt`, `client/GlassManRenderer.kt`.

* **Creature:** a translucent glass humanoid (player-shaped model, scaled tall and thin); alpha 0.12 standing,
  up to 0.5 moving. Invulnerable to everything but `/kill` and the void; no knockback, no drops, no XP. Speed
  0.27 (between a player's walk and sprint), follow range 128, always targets its dreamer.
* **Teleport:** if more than 6 blocks off and not closing in for 40 ticks, it steps to a spot 8–12 blocks from
  the dreamer (open ground), with shimmer particles at both ends and a chime; 10 s cooldown.
* **Spawning** (dream only): after 3 minutes of a visit, every 10 s a chance rising linearly to 20% at 15 min
  (×3, max 60%, inside a listening post). Announced 3 s ahead by a chime to the dreamer and a shimmer at the
  spot, 32–48 blocks away. One per dreamer (tracked by the spawner; a reloaded one reclaims its place, a
  duplicate removes itself). Gone when the dreamer wakes, dies, logs off or leaves.
* **Holds you:** within 24 blocks, crouching still doesn't wake ("Something holds you in the dream").
* **Touch** (every 2 s): drains 10 dust from the inventory (Phials first), +600 cold (30 s), 1 heart; at 3
  hearts or less it instead throws the dreamer out (`Waking.MASK`) with Came Back Wrong, never killing.
* **Possession:** if it was within 8 blocks when the dreamer woke (any way), it moves into the nearest villager
  within 8 blocks of where they wake whose mind was taken with Flay Mind (`isBrainswept`). Possessed (kept in
  saved data) for good; faint shimmer; right-click opens a trading screen: each day 2–3 Foci with ready-made
  hexes (named), from the library pool (ours with their prices, Hex's cypher hexes at 8), for 4–12 Charged
  Amethyst, each once per day.
* **Ready-made hexes** (`DreamLibrary.ENTRIES`, each test-cast): Sights of Vitality, Danger, Players, Stealth
  (and Names, with strings on); Wellspring at Gaze, Wellspring Gauge, Tether at Gaze; Frost Step, Thaw Around,
  Frost Gaze; Break Gaze, Light at Gaze; Leap, Blink Forward; Dream Stair, Dream Tunnel. Plus Hex Casting's 16
  cypher hexes.

### Media draws danger (done, covered by GameTests)

`dream/DreamDanger.kt`: carried media (every media holder in inventory, armor, off hand and bauble slots, plus
imbued items' stores) gives a factor 1 + dust/400, max ×4 (recounted every 2 s). Lost Dreamers: chance × factor
(max 60%), nearby cap + (⌊factor⌋ − 1), so up to 9. Glass Man: grace 3 min ÷ factor (45 s at ×4), chance ×
factor (max 50%; listening post ×3, max 60%).

### Ambient media (done, covered by GameTests)

`ambient/AmbientMedia.kt` (all numbers at the top): per-chunk level, per dimension, saved data (only non-zero
chunks). Sources: 25% of every cast's cost settles in the chunk it was cast in (an `ExtractMedia.Pre` tap
registered before wellsprings, so it sees the whole cost; creative casts count too); each budding amethyst
random tick adds 0.1 dust (mixin). Every 20 s each chunk keeps 98% and gives 5% of its excess over each of its
four neighbours to it. Effects: ≥ 500 dust sparks round players; > 1,500 dust each cast has a
(level − 1,500)/10,000 chance (max 15%) to misfire (silenced, decided when the cast begins); ≥ 3,000 reserved
for the dream's pull. Pattern **Ambient Reflection** (`mirabilia:ambient_media`, `aawqqwaa`): → dust here. Book:
Patterns → Ambient Media.

### The dream pulls (done, covered by GameTests)

`dream/DreamPull.kt`: at ≥ 3,000 dust ambient, each cast by a player (not circles, not dreamers) has
min(25%, 5% + (level − 3,000)/20,000) to pull them in; the cast is silenced, the pull happens at the end of the tick
(enter with no bed: they wake where they stood). Everything living within 4 blocks comes too: other players as
dreamers in their own right, creatures just carried. The chunk's ambient media is halved. From 2,500 dust an
action-bar warning once a minute.

Waking (any way) brings back every creature within 4 blocks of the dreamer, except Lost Dreamers, the Glass Man and
players, so pets and companions aren't lost. Dying in a dream loses nothing: inventory (main, armor, offhand) and
experience are taken off before drops (`Belongings`), saved in `DreamVisits` and given back on respawn; Curios
(`DropRulesEvent` → ALWAYS_KEEP) and Trinkets (`TrinketDropCallback` → KEEP) keep their slots. Everyone pulled in
arrives together, within a block of the caster's arrival spot.

### Machines of media (done, covered by GameTests)

`machines/`. Each holds its numbers at the top.
- **Media Discharger** (crafted: shards, copper, amethyst block; needs an upright lightning rod on top, and its arcs
  start at the rod's tip): no block entity; a scheduled tick
  every second takes 10% of its chunk's ambient (only when that's ≥ 50 dust: below, no draw and no arc) and arcs into a random living thing within
  8 blocks (players too) for min(8, 1 + taken/20 dust) damage (lightning damage type, but no fire or conversion);
  sparks and a crack. Redstone signal stops it.
- **Ambient Collector** (Flay Mind: farmer, level ≥ 3, onto budding amethyst; 100 dust): 1% of the chunk's ambient a
  second, holds 1,000 dust.
- **Solar Collector** (crafted: glass, charged amethyst, daylight detector, 3 Glyphstone, so only after finding a
  listening post; its book page is locked behind the `mirabilia:listening_post` advancement): 0.1 dust/s × `Sunlight.intensity`
  (sun height; ×0.3 rain, ×0.05 thunder; overworld only), only with nothing above that blocks skylight (glass is
  fine); holds 500 dust.
- Collectors are `MediaStore`s: Wellspring Binding/Tether accept them (`MediaSource.canHoldAt`), comparators read
  fullness, using one shows "Holds X of Y dust". Breaking one loses its media.
- **Crystallizer** (crafted: iron, glass, amethyst block): Amethyst Dust in from top/sides (or by hand), shards out
  the bottom (or empty hand). 4 dust + 2 dust of media from collectors within 8 blocks (solar ones first) → 1
  Amethyst Shard every 5 s; breaking it drops everything inside; `lastSolar` notes a shard made of sunlight alone (for "Crystal of Light").
- **Read the Sun** (`mirabilia:read_the_sun`, `qaqqwqaq`): → `Sunlight.intensity` where the cast happens.
- Book: Items → Machines of Media (to be locked behind an advancement). Textures: `tools/gen_machine_textures.py`.

### Advancements (done, covered by GameTests)

Own tab, generated by `tools/gen_advancements.py` (one table: parent, icon, frame, hidden, criteria). Vanilla
triggers where they exist (placed block, inventory changed incl. Deepsight enchantment, changed dimension, effects
changed, location in structure); the rest have one `minecraft:impossible` criterion `done`, awarded by
`MirabiliaAdvancements.award` from the code that notices the deed. Machine deeds go to players within 16 blocks;
Solar ones to the collector's owner (who placed it). Tallies (solar dust per owner, caltrop days, possessions) in
the `mirabilia_advancement_records` saved data. The caltrop sighting is checked on the server every second
(`SunSighting`: lens worn, overworld, open sky, rain < 0.5, looking within 8° of the sun).

Tree: **Thoughts of the Sun** (root: any of our items, tag `mirabilia:all`, or standing in charged air; unlocks the
Machines of Media entry) → Lightning Rod → High Voltage (hidden), Background Harvest; Stuffy with Magic; Treatise on
Reserves (= `media_sources`, goal, hidden; unlocks Wellsprings) → Supply Chain → Fully Provided; Wider View → See
Through; Inscription → Well Armed; Caltrop (hidden) → Different Every Day (hidden, challenge); Hoarfrost; Silence;
Glass Dream → Fell Through (hidden), Cold Welcome, Came Back Wrong (hidden), Act of Will, Mind Castle, Listening
Station (goal; unlocks the Solar Collector page) → Noon → Crystal of Light → Kind Thoughts (challenge, 1,000 dust),
Someone Is Watching (hidden) → Just in Time (≤ 4 blocks), A New Shell (hidden) → A Deal → Glass Village (challenge, 5).

Not made (features not built): macros, healing, Echo Fragments, the listening-station exit, On Record.

### Macros (done, covered by GameTests)

`macro/`. A player's own pattern standing for a hex, recognised by a Hex special handler (`mirabilia:macro`, the
extension Numerical Reflection uses; no mixin). Hex resolves normal and per-world patterns first, so a macro can
never hide one. Drawn in any hex its owner casts, it runs its hex as Hermes' Gambit would (break boundary after),
for 1 dust a call; circles and standing spells (no caster) can't call macros. Patterns: **Macro Binding**
(`wqwaqawqw`; pattern, list; 1 Charged Amethyst; refuses a pattern Hex already resolves, incl. numbers and
Bookkeeper's Gambit, and new macros past the limit; rebinding replaces), **Macro Release** (`ewdwe`; pattern),
**Macro Reflection** (`eadwdae`; → list of the caster's macro patterns). Limit 4, +1 each for Hex's Enlightenment,
Glass Dream, Listening Station and A Deal (max 8). Stored per player in `mirabilia_macros`. Advancements Short Speech
and Full Vocabulary (goal, 8 macros). Book: Patterns → Macros. (Mock test players are always creative, so the call's
charge is checked through ambient media.)

### Dream spells must be found (done, covered by GameTests)

`dream/DreamLore.kt`. Wake, Will Upward/Apart/Solid, Dreamer's Gaze/Footing/Bearing (not Axis Purification) are
inert until learned: each op calls `DreamLore.require` first and, unlearned, throws `MishapInvalidPattern`, so the
shape means nothing (also inside ready-made hexes: library, Glass Man foci). Not great spells, fixed shapes. Learned
by picking up an Ancient Scroll (`hexcasting:scroll` with `op_id` + `pattern` NBT) of that spell: invisible
advancements `mirabilia:dream_lore/<spell>` (`inventory_changed` with an NBT predicate), which also reveal the
spell's book page. Scrolls (random spell): Lost Dreamers 4% (+1% per Looting, player kills), dream shrines 25%,
castle keeps 1–2, listening posts 2–3. Book: Tanako's World entry locked behind Glass Dream; a new unlocked one-page
entry **Dreams of Glass** says how to get there. Without Wake, a first visit ends by crouching still (not while
the Glass Man is near), the cold, or death.

### Imbuing patterns redrawn

Imbue Lens/Armor/Tool are now double hexagons in the manner of Craft Artifact, larger, with a straight line through
both; their spikes are whole triangles laid over the hexagon (so every junction but the line's ends stays even and
the figure is one stroke): **Lens** a six-pointed star (outer side 3, inner 1, 66 strokes), **Tool** one spike up
and down (outer 4, inner 2, 62), **Armor** two spikes up and down (outer 4, inner 2, 80). Still per-world great
spells: worlds created before keep their old shapes (Hex never rescrambles). **Recharge Imbued Item** (a normal
pattern) is the plain double hexagon (outer 2, inner 1) with a small hexagon on each side and the line through all
three, like battery cells (38 strokes). (Diamonds at the ends were dropped: that is Hexal's wisp look.) None of our
patterns clash with Hexal's.

### Spawn eggs and imbued glyphs

Spawn eggs for the Lost Dreamer and the Glass Man (Architectury eggs, resolved after entity registration). An egg
Glass Man with no dreamer stands inert awake; in the dream it takes the nearest dreamer within 48 blocks who has
none. Imbued items show their whole hex (every pattern, nested lists included, up to 64) as glyphs in the wearer's
Hex pigment (client, `ImbuedGlyphLayer` via a `PlayerRenderer` mixin): a band round each armor piece (extra rows
for long hexes), a strip over the eyes for a lens (head or face slot), a turning ring round a held tool, placed
through vanilla's hand chain and the item model's display transform (first person via an `ItemInHandRenderer`
mixin), square to the blade. Dim once the item's store is spent. Config `imbuedGlyphs`.

Imbued lenses, armor and tools spend their own store even for creative players (it is the item's, not the
player's): an empty one does nothing until recharged.

### Configuration, step 1: feature switches (done, covered by GameTests)

`config/mirabilia.json`, sections "features", "lens", "macros", "client" (each option with a "_name" explanation;
missing keys written back; restart to apply). `config/Features.kt` holds the switches and derived names
(`isOn(name)`: machines need ambient media, the Glass Man needs the dream, `solar_collector_plain` = solar without
the dream, `recharge_imbued` = any imbuing, ...). How a switch takes effect:
- Patterns stay registered but are wrapped in `FeatureGated`: while off they mean nothing when cast (clients and
  servers with different files still agree on the registry; written foci survive a switch-off).
- Book: Patchouli flags `mirabilia:<name>` on entries and pages, set at startup and again on a client from the
  server's switches (packet `mirabilia:features` on join, then the book reloads).
- Recipes and advancements: load condition `mirabilia:feature` (Fabric `fabric:load_conditions`, Forge
  `conditions`). Loot: loot condition `mirabilia:feature`. No advancement hangs from one of another feature.
- Runtime checks in the systems themselves (dream entry, pull, ambient gathering, machines, wellspring hook,
  imbued runs, Glass Man, macros handler, caltrop). Items stay registered; the creative tab leaves them out.
- Tanako's World off: no dream; wellspring notes in overworld chests (15%: dungeon, stronghold library, ancient
  city, mansion, mineshaft); Solar Collector made with obsidian; ward stones with calcite and tinted glass.
- Also: keep items on dream death, carry creatures, dream spells need scrolls, sun caltrop, Deepsight loot,
  Greater Lens zoom, macro starting count and milestones (advancement → how many more), glyph style
  (bands / hoops / off).

Step 2 (done): every gameplay number is in the file too, declared once in `config/Tuning.kt` (sections
ambientMedia, dreamPull, machines, dream, glassMan, imbuing, spells, loot, advancements, client; 134 options in
all). Media amounts are written in dust, times in ticks unless named otherwise. The systems keep their named
constants as getters reading the tuning, so the code reads as before. Not in the file: worldgen shapes, internal
render geometry, slot indices. Dream scroll drop rates live in the loot tables (datapack-editable).

Texts quote the config: translations may hold `#{name}` (with noun forms `#{name|one|other}` or Russian's
`#{name|one|few|many|fraction}`), filled in from `ConfigText.values()` by a client mixin on `ClientLanguage`, so
book pages, advancement descriptions and tooltips all follow the file. A client on a server uses the server's
numbers (sent with its switches on joining). 35 pages and descriptions per language use them.

The Glass Man is drawn with a translucent render type that writes colour but not depth (`GlassRenderType`), so
water behind him no longer vanishes.

### More switches; Thaw warms creatures

Features `wardingStones`, `wardArmor` and `standingSpells` (**off by default**: raised spells wait rather than
vanish while it's off). Thaw now takes `spells.thawFrost` (60) ticks of frost off every creature in its sphere:
a fully frozen one (140) stops taking damage, but powder snow freezes it again in about three seconds. Texts with
a percentage write `%%` (Minecraft formats these strings, so a lone `%` broke them).

Idea for later (in the brief, 3c): an imbuable compass pointing at the position or entity its hex leaves.

### Chat and the mind palace (done, covered by GameTests)

String patterns (only while `stringPatterns` is on), each with its own switch (`chatReading`, `mindPalace`):
- **Echo Purification** (`string/chat`, `ddwddwww`): number → the caster's own chat message that many back (0 the
  latest), or Null. Only the caster's own, so others talking can't shift the numbers. `strings.chatHistory` (32)
  kept per player in memory (`strings/ChatMemory.kt`, from Architectury's chat event; gone on logout/restart).
- **Mind palace** (`strings/MindPalace.kt`, saved data `mirabilia_mind_palaces`): each player's numbered rooms
  (`strings.palaceRooms`, 64) of strings. **Memory's Gambit** (`string/memory_write`, `ddwddwwe`; string | null,
  room; 0.1 dust), **Memory's Purification** (`string/memory_read`, `ddwddwwq`; room → string or Null), **Palace
  Reflection** (`string/palace_size`, `ddwddwqw`; → rooms filled). Players only (`MishapNoMind` otherwise).

### Creative knows the dream's spells

In creative mode (the game mode itself, not `isCreative()`, which GameTest mock players always claim) every dream
spell works without its scroll. Their book pages still unlock only with the scrolls.

### Possession kept on the villager

A possessed villager carries an entity tag (`mirabilia.possessed`) in its own saved data, so it stays possessed
when saved and re-created (pickup mods, moving between worlds) even with a new UUID; the old world-level UUID list
is still read, and marks villagers as it finds them. When the Glass Man follows a dreamer out but finds no
Flay-Minded villager within 8 blocks of where they wake, they're told in chat; both outcomes are logged.

### Cold fix (0.1.1)

The dream's cold took 75 s unprotected instead of 5 minutes: 4 a tick against a limit of 6000. Cold is now counted
in quarter-ticks (`DreamTicker.COLD_PER_TICK` = 4, limit = `dream.maxColdTicks` x 4), and fire's warmth, the
Glass Man's touch and willing are scaled the same, so every configured number means ticks of unprotected cold.

### Leaving the dream is logged; cold warning

The dreamer also gets a chat line saying why they woke (the Glass Man's touch named when he was within 4 blocks of
a cold exit). Every exit from the dream writes a server log line (`Dream.log`): who, how (stillness, pattern, cold, mask,
other, or died with the damage type), minutes dreamed, health, cold and the Glass Man's distance; the on-screen
messages aren't logged. A dreamer is warned once their cold passes `dream.coldWarning` (75%), and again only after
it eases off. (Found while chasing "thrown out by one touch": the Glass Man's touch adds 30 s of cold, so late in
a visit one touch can finish the cold off.)

### Wake command

`/mirabilia wake [players]` (permission level 2): wakes dreamers at once, as Wake would; without players, whoever
runs it. A way out for a stuck player, and handy for testing.

### Sun sign, redrawn

One spike points up the sky (towards the zenith morning and evening, turning over through south around noon),
leaning 25° towards the viewer so the turn of the other three reads; those three turn 12° a day about it (back
round every 10 days, by the 120° symmetry). Drawn smaller than the sun, glowing amethyst over a dimmed square,
spikes shaded and narrowed by depth. (Note for testing: `/time set day` jumps to day 0; use `/time add 24000`.)

### Russian translation

`lang/ru_ru.json` covers every key, book included, in Hex Casting's own Russian terms (media = мысль, hex =
заговор, pattern = руна, mishap = провал, Phial = Сосуд мысли, Focus = талисман, Cypher/Trinket = побрякушка/
штуковина, Akashic Record = Архив Акаши, Flay Mind = Раздирание Разума). Advancement names are the user's own.

## Open questions

* Exact Greater Lens zoom value.
* Sun sign: the rotation formula was chosen (golden angle per day); change it if wanted.
* Persistent-spell costs.
