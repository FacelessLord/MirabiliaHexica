package com.mirabiliahexica.worldgen

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.common.items.magic.ItemAncientCypher
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexActions
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes
import at.petrak.hexcasting.common.loot.AddHexToAncientCypherFunc
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.RegistrarEntry
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/**
 * The hexes shelved in the listening post's Akashic library: ready-made spells some earlier dreamer left behind,
 * each under its own key pattern (drawn on the shelf's spine). Read one with Akasha's Distillation and the key,
 * then cast it with Hermes' Gambit, imbue it into a lens, or raise it as a standing spell.
 *
 * Each listening post shelves its own six ([pick]): a few of ours ([ENTRIES], every one run by the GameTests)
 * and the rest from the hexes Hex Casting puts in Ancient Cyphers (Shatter, Blink, Radar…), keys shuffled.
 * Possessed villagers sell the same hexes, written into Foci.
 */
object DreamLibrary {
    /** One of our hexes. [price] is what a possessed villager asks for it, in Charged Amethyst. */
    class Entry(val name: String, val key: HexPattern, val hex: List<Iota>, val price: Int = 6)

    /** What goes on one shelf: a key, and the hex already in the shelf's stored form. */
    class Shelf(val name: String, val key: HexPattern, val iotaTag: CompoundTag)

    /** Keys handed out to a listening post's shelves, six at random. */
    private val KEYS = listOf("dwdwd", "awawa", "eqeqe", "qeqeq", "wdwdw", "wawqaw", "eweqe", "qdqdq", "wawwa", "qwqwq")

    const val SHELVED = 6

    /** The six hexes of the listening post with [seed]: two to four of ours, the rest Hex Casting's own. */
    fun pick(seed: Long): List<Shelf> {
        val random = Random(seed)
        val ours = ENTRIES.shuffled(random).take(2 + random.nextInt(3))
            .map { Shelf(it.name, it.key, IotaType.serialize(asList(it))) }
        val theirs = cypherHexes(seed, SHELVED - ours.size)
        val keys = KEYS.shuffled(random).map(::key)
        return (ours + theirs).shuffled(random).mapIndexed { i, shelf -> Shelf(shelf.name, keys[i], shelf.iotaTag) }
    }

    /**
     * [count] different hexes from those Hex Casting writes into Ancient Cyphers. Its list is private, so they're
     * drawn the way its loot does it, by filling a cypher, and read back out of the cypher's data.
     */
    /** Hex Casting's cypher hexes, by name, as [Shelf]s (for the library and for possessed villagers' wares). */
    fun cypherHexes(seed: Long, count: Int): List<Shelf> {
        val found = LinkedHashMap<String, ListTag>()
        // One source for all the draws: fresh ones from neighbouring seeds tend to make the same first pick
        val random = RandomSource.create(seed)
        var attempt = 0
        while (found.size < count && attempt++ < 200) {
            val cypher = AddHexToAncientCypherFunc.doStatic(ItemStack(HexItems.ANCIENT_CYPHER), random)
            val tag = cypher.tag ?: continue
            found.putIfAbsent(tag.getString(ItemAncientCypher.TAG_HEX_NAME), tag.getList(ItemAncientCypher.TAG_PATTERNS, Tag.TAG_COMPOUND.toInt()))
        }
        return found.map { (name, patterns) ->
            // A list iota, in the form Hex stores one: its type and its items
            val list = IotaType.serialize(ListIota(listOf())).apply { put(HexIotaTypes.KEY_DATA, patterns) }
            Shelf(name, key(KEYS[0]), list)
        }
    }

    val ENTRIES: List<Entry> by lazy {
        listOf(
            // For a lens: every living creature within 32 blocks, labelled with its health
            Entry("sight_of_vitality", key("dwdwd"), sightOf(HexActions.`ZONE_ENTITY$LIVING`), price = 8),
            // For a lens: every monster within 32 blocks, labelled with its health
            Entry("sight_of_danger", key("awawa"), sightOf(HexActions.`ZONE_ENTITY$MONSTER`), price = 8),
            // Binds the container the caster is looking at as the hex's wellspring
            Entry("wellspring_at_gaze", key("eqeqe"), listOf(
                hex(HexActions.GET_CASTER), hex(HexActions.`ENTITY_POS$EYE`),
                hex(HexActions.GET_CASTER), hex(HexActions.ENTITY_LOOK),
                hex(HexActions.RAYCAST),
                ours(MirabiliaActions.SOURCE_BIND),
            ), price = 5),
            // In the dream: a stair of eight steps rising ahead, in the direction the dreamer faces
            Entry("dream_stair", key("qeqeq"), (1..8).flatMap { step ->
                // footing + heading × step + (0, step - 1, 0), made solid
                listOf(ours(MirabiliaActions.DREAM_FOOTING)) + heading() + listOf(
                    hex(HexActions.ESCAPE), DoubleIota(step.toDouble()), hex(HexActions.MUL_DOT), hex(HexActions.ADD),
                    hex(HexActions.ESCAPE), Vec3Iota(Vec3(0.0, step - 1.0, 0.0)), hex(HexActions.ADD),
                    ours(MirabiliaActions.WILL_SOLID),
                )
            }, price = 7),
            // In the dream: a tunnel sixteen blocks long, three across, straight along the dreamer's gaze
            Entry("dream_tunnel", key("wdwdw"), (1..8).flatMap { step ->
                // footing + (0, 1, 0) + bearing × 2·step, hollowed
                listOf(
                    ours(MirabiliaActions.DREAM_FOOTING),
                    hex(HexActions.ESCAPE), Vec3Iota(Vec3(0.5, 1.5, 0.5)), hex(HexActions.ADD),
                    ours(MirabiliaActions.DREAM_BEARING),
                    hex(HexActions.ESCAPE), DoubleIota(2.0 * step), hex(HexActions.MUL_DOT), hex(HexActions.ADD),
                    ours(MirabiliaActions.WILL_APART),
                )
            }, price = 7),
            // For a lens: the media left in the hex's wellspring, shown at the caster's feet (bind one first)
            Entry("wellspring_gauge", key("wawqaw"), listOf(
                hex(HexActions.GET_CASTER), hex(HexActions.`ENTITY_POS$FOOT`),
                ours(MirabiliaActions.SOURCE_MEDIA),
                hex(HexActions.ESCAPE), DoubleIota(2.0), hex(HexActions.LAST_N_LIST),
                hex(HexActions.ESCAPE), DoubleIota(1.0), hex(HexActions.LAST_N_LIST),
            ), price = 4),
            // For a lens: every player within 32 blocks, labelled with their health
            Entry("sight_of_players", key("dwdwd"), sightOf(HexActions.`ZONE_ENTITY$PLAYER`), price = 9),
            // For a lens: every player within 32 blocks, labelled with whether they're sneaking
            Entry("sight_of_stealth", key("dwdwd"), sightOf(HexActions.`ZONE_ENTITY$PLAYER`, MirabiliaActions.SNEAKING), price = 10),
            // Ice under the caster's feet, two blocks round
            Entry("frost_step", key("dwdwd"), atFeet() + listOf(hex(HexActions.ESCAPE), DoubleIota(2.0), ours(MirabiliaActions.FREEZE)), price = 4),
            // Thaws everything within four blocks of the caster
            Entry("thaw_around", key("dwdwd"), atFeet() + listOf(hex(HexActions.ESCAPE), DoubleIota(4.0), ours(MirabiliaActions.THAW)), price = 4),
            // Freezes the place the caster is looking at, three blocks round
            Entry("frost_gaze", key("dwdwd"), gazeBlock() + listOf(hex(HexActions.ESCAPE), DoubleIota(3.0), ours(MirabiliaActions.FREEZE)), price = 5),
            // Tethers the item in the other hand to the container the caster is looking at
            Entry("tether_at_gaze", key("dwdwd"), gazeBlock() + listOf(ours(MirabiliaActions.SOURCE_TETHER)), price = 6),
            // Breaks the block the caster is looking at
            Entry("break_gaze", key("dwdwd"), gazeBlock() + listOf(hex(HexActions.BREAK_BLOCK)), price = 5),
            // A light on the face of the block the caster is looking at
            Entry("light_gaze", key("dwdwd"), gazeBlock() + listOf(
                hex(HexActions.GET_CASTER), hex(HexActions.`ENTITY_POS$EYE`), hex(HexActions.GET_CASTER), hex(HexActions.ENTITY_LOOK),
                hex(HexActions.RAYCAST_AXIS), hex(HexActions.ADD), hex(HexActions.CONJURE_LIGHT),
            ), price = 4),
            // A jump forward and up, along the caster's gaze
            Entry("leap", key("dwdwd"), listOf(
                hex(HexActions.GET_CASTER),
                hex(HexActions.GET_CASTER), hex(HexActions.ENTITY_LOOK), hex(HexActions.ESCAPE), DoubleIota(1.5), hex(HexActions.MUL_DOT),
                hex(HexActions.ESCAPE), Vec3Iota(Vec3(0.0, 0.6, 0.0)), hex(HexActions.ADD),
                hex(HexActions.ADD_MOTION),
            ), price = 6),
            // Six blocks straight ahead, in an instant
            Entry("blink_forward", key("dwdwd"), listOf(
                hex(HexActions.GET_CASTER), hex(HexActions.ESCAPE), DoubleIota(6.0), hex(HexActions.BLINK),
            ), price = 9),
        ) + listOfNotNull(MirabiliaActions.STRING_NAME?.let { name ->
            // For a lens: every creature within 32 blocks, labelled with its name (only with our strings on)
            Entry("sight_of_names", key("dwdwd"), sightOf(HexActions.`ZONE_ENTITY$LIVING`, name), price = 9)
        })
    }

    /** The caster's feet. */
    private fun atFeet() = listOf(hex(HexActions.GET_CASTER), hex(HexActions.`ENTITY_POS$FOOT`))

    /** The block the caster is looking at (Archer's Distillation from their eyes). */
    private fun gazeBlock() = listOf(
        hex(HexActions.GET_CASTER), hex(HexActions.`ENTITY_POS$EYE`), hex(HexActions.GET_CASTER), hex(HexActions.ENTITY_LOOK),
        hex(HexActions.RAYCAST),
    )

    /**
     * The way the dreamer faces along the ground, as an axis: their bearing, flattened (bearing minus its upward
     * part, `b − (b·up)·up`), then snapped with Axis Purification.
     */
    private fun heading(): List<Iota> = listOf(
        ours(MirabiliaActions.DREAM_BEARING),
        ours(MirabiliaActions.DREAM_BEARING), hex(HexActions.ESCAPE), Vec3Iota(Vec3(0.0, 1.0, 0.0)), hex(HexActions.MUL_DOT),
        hex(HexActions.ESCAPE), Vec3Iota(Vec3(0.0, -1.0, 0.0)), hex(HexActions.MUL_DOT),
        hex(HexActions.ADD),
        ours(MirabiliaActions.SNAP_TO_AXIS),
    )

    /**
     * `[[creature, what], …]` for every creature the zone pattern finds within 32 blocks of the caster, where
     * `what` is what [query] says of it (its health unless told otherwise): Thoth's Gambit maps "duplicate it,
     * ask, pair the two" over the zone's list.
     */
    private fun sightOf(zone: ActionRegistryEntry, query: RegistrarEntry<ActionRegistryEntry> = MirabiliaActions.HEALTH): List<Iota> = listOf(
        hex(HexActions.GET_CASTER), hex(HexActions.`ENTITY_POS$FOOT`),
        hex(HexActions.ESCAPE), DoubleIota(32.0), hex(zone),
        // The code block, pushed whole (inside Introspection, Consideration would eat the number's escape)
        hex(HexActions.ESCAPE), ListIota(listOf(
            hex(HexActions.DUPLICATE), ours(query),
            hex(HexActions.ESCAPE), DoubleIota(2.0), hex(HexActions.LAST_N_LIST),
        )),
        hex(HexActions.SWAP), hex(HexActions.FOR_EACH),
    )

    private fun key(signature: String) = HexPattern.fromAngles(signature, HexDir.EAST)

    private fun hex(entry: ActionRegistryEntry) = PatternIota(entry.prototype)

    private fun ours(entry: RegistrarEntry<ActionRegistryEntry>) = PatternIota(entry.value.prototype)

    fun asList(entry: Entry) = ListIota(entry.hex)
}
