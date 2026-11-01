package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.common.blocks.akashic.BlockEntityAkashicBookshelf
import at.petrak.hexcasting.common.lib.HexBlocks
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaItems
import com.mirabiliahexica.worldgen.DreamLibrary
import com.mirabiliahexica.worldgen.DreamPiece
import com.mirabiliahexica.worldgen.ListeningPostPiece
import com.mirabiliahexica.worldgen.MarbleCastlePiece
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement

/**
 * The dream's coded structures, built chunk by chunk into the dream as world generation would build them.
 * Each test builds far from the others, at a spot of its own.
 */
class DreamStructureTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 600)
    fun marbleCastlesStandAndDiffer(helper: GameTestHelper) {
        val dream = dream(helper)
        val sizes = (0 until 3).map { i ->
            val origin = ground(dream, 20000 + i * 200, 20000)
            val piece = MarbleCastlePiece(1234L + i * 7919L, origin)
            build(dream, piece)
            val chests = count(dream, piece.boundingBox) { it == Blocks.CHEST }
            check(chests == 2) { "castle $i has $chests chests, expected 2 (ground floor and roof)" }
            checkLoot(dream, piece.boundingBox, MarbleCastlePiece.LOOT, MarbleCastlePiece.KEEP_LOOT)
            check(count(dream, piece.boundingBox) { it == Blocks.LADDER } >= 5) { "castle $i's keep has no ladder" }
            val bricks = count(dream, piece.boundingBox) { it == MirabiliaBlocks.MARBLE_BRICKS.value }
            check(bricks > 300) { "castle $i has only $bricks bricks" }
            piece.boundingBox.xSpan to bricks
        }
        check(sizes.toSet().size == 3) { "the three castles came out alike: $sizes" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 600)
    fun listeningPostIsInscribedAndHoldsTheRecord(helper: GameTestHelper) {
        val dream = dream(helper)
        val origin = ground(dream, 26000, 26000)
        val piece = ListeningPostPiece(42L, origin)
        build(dream, piece)
        val box = piece.boundingBox
        val glyphs = count(dream, box) { it == MirabiliaBlocks.GLYPHSTONE.value }
        check(glyphs > 800) { "the circle has only $glyphs glyphstone" }
        checkLoot(dream, box, ListeningPostPiece.LOOT)
        check(count(dream, box) { it == Blocks.CHEST } == 4) { "the dais should hold four chests" }

        // The library on the dais: the Record two above the floor, among shelves, six of them holding hexes
        check(dream.getBlockState(origin.above(2)).`is`(HexBlocks.AKASHIC_RECORD)) { "no Akashic Record on the dais" }
        val shelves = BlockPos.betweenClosedStream(origin.offset(-1, 2, -1), origin.offset(1, 3, 1))
            .map { it.immutable() }.filter { dream.getBlockState(it).`is`(HexBlocks.AKASHIC_BOOKSHELF) }.toList()
        check(shelves.size == 16) { "expected 16 shelves round the Record, found ${shelves.size}" }
        val filled = shelves.mapNotNull { (dream.getBlockEntity(it) as BlockEntityAkashicBookshelf).pattern }
        check(filled.map { it.anglesSignature() }.toSet() == DreamLibrary.pick(piece.seed).map { it.key.anglesSignature() }.toSet()) {
            "shelves hold keys ${filled.map { it.anglesSignature() }}"
        }

        // The circle lies flat: between the dais and the egg's wall, every mark is on the floor layer
        val markLayers = BlockPos.betweenClosedStream(box).filter {
            val d = Math.hypot((it.x - origin.x).toDouble(), (it.z - origin.z).toDouble())
            d > 18 && d < 38 && it.y < origin.y + 3 && dream.getBlockState(it).`is`(MirabiliaBlocks.GLYPHSTONE.value)
        }.map { it.y }.toList().toSet()
        check(markLayers == setOf(origin.y - 1)) { "the circle isn't flat: glyphstone on layers $markLayers" }

        // The egg: hollow above the floor, shelled over far above, open at the crown
        check(dream.getBlockState(origin.offset(20, 20, 0)).isAir) { "the egg isn't hollow" }
        val shellAbove = (origin.y + 40..box.maxY()).any { dream.getBlockState(BlockPos(origin.x + 10, it, origin.z)).`is`(MirabiliaBlocks.INKSTONE.value) }
        check(shellAbove) { "no shell over the circle" }
        check((origin.y + 3..box.maxY()).all { !dream.getBlockState(BlockPos(origin.x, it + 2, origin.z)).`is`(MirabiliaBlocks.INKSTONE.value) }) {
            "the crown is closed"
        }

        // Six obelisks on the middle ring: inkstone standing well clear of the floor, inside the egg
        val raised = BlockPos.betweenClosedStream(box).filter {
            val d = Math.hypot((it.x - origin.x).toDouble(), (it.z - origin.z).toDouble())
            d in 18.0..38.0 && it.y in origin.y + 4..origin.y + 14 && dream.getBlockState(it).`is`(MirabiliaBlocks.INKSTONE.value)
        }.count()
        check(raised >= 6 * 4) { "obelisks missing: only $raised raised inkstone blocks" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun mediaSourceNotesRevealWellsprings(helper: GameTestHelper) {
        val server = helper.level.server
        val advancement = server.advancements.getAdvancement(Mirabilia.id("media_sources"))
            ?: throw GameTestAssertException("no media_sources advancement")
        val player = helper.makeMockServerPlayerInLevel()
        check(!player.advancements.getOrStartProgress(advancement).isDone) { "already revealed" }
        player.inventory.add(ItemStack(MirabiliaItems.MEDIA_SOURCES_NOTES.value))
        // A real player's inventory is synced every tick, which is what fires the criterion
        player.inventoryMenu.broadcastChanges()
        check(player.advancements.getOrStartProgress(advancement).isDone) { "picking up the notes didn't reveal wellsprings" }
        // And they can turn up in the dream's treasure
        for (table in listOf("marble_castle_keep", "listening_post", "dream_shrine")) {
            val json = javaClass.getResource("/data/mirabilia/loot_tables/chests/$table.json")?.readText().orEmpty()
            check("media_sources_notes" in json) { "$table never holds the notes" }
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun structuresCanStartInTheDream(helper: GameTestHelper) {
        val server = helper.level.server
        val dream = dream(helper)
        val structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE)
        val generator = dream.chunkSource.generator
        for ((structureId, setId) in listOf("marble_castle" to "marble_castles", "listening_post" to "listening_posts")) {
            val structure = structures.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, Mirabilia.id(structureId)))
            val set = dream.chunkSource.generatorState.possibleStructureSets().single { it.`is`(Mirabilia.id(setId)) }
            val placement = set.value().placement() as RandomSpreadStructurePlacement
            // It takes any chunk and gives the candidate of the region that chunk lies in
            val valid = (0 until 8).count { region ->
                val chunk = placement.getPotentialStructureChunk(dream.seed, region * placement.spacing(), placement.spacing())
                val context = Structure.GenerationContext(
                    server.registryAccess(), generator, generator.biomeSource, dream.chunkSource.randomState(),
                    server.structureManager, dream.seed, chunk, dream,
                ) { structure.value().biomes().contains(it) }
                structure.value().findValidGenerationPoint(context).isPresent
            }
            check(valid > 0) { "$structureId wouldn't start in any of 8 candidate spots" }
            // Listening posts are rare: one in a region about 3000 blocks across
            if (structureId == "listening_post") check(placement.spacing() * 16 >= 3000) { "listening posts are only ${placement.spacing() * 16} blocks apart" }
        }
        helper.succeed()
    }

    private fun dream(helper: GameTestHelper): ServerLevel =
        helper.level.server.getLevel(Dream.LEVEL) ?: throw GameTestAssertException("no Tanako's World")

    private fun ground(dream: ServerLevel, x: Int, z: Int): BlockPos {
        dream.getChunk(x shr 4, z shr 4)
        return BlockPos(x, dream.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z)
    }

    /** As world generation does: one call per chunk the piece spans, each limited to that chunk. */
    private fun build(dream: ServerLevel, piece: DreamPiece) {
        val box = piece.boundingBox
        val random = RandomSource.create(piece.seed)
        for (cx in (box.minX() shr 4)..(box.maxX() shr 4)) for (cz in (box.minZ() shr 4)..(box.maxZ() shr 4)) {
            val chunk = ChunkPos(cx, cz)
            dream.getChunk(cx, cz)
            val chunkBox = BoundingBox(chunk.minBlockX, dream.minBuildHeight, chunk.minBlockZ, chunk.maxBlockX, dream.maxBuildHeight - 1, chunk.maxBlockZ)
            piece.postProcess(dream, dream.structureManager(), dream.chunkSource.generator, random, chunkBox, chunk, piece.origin)
        }
    }

    private fun count(dream: ServerLevel, box: BoundingBox, test: (Block) -> Boolean) =
        BlockPos.betweenClosedStream(box).filter { test(dream.getBlockState(it).block) }.count().toInt()

    private fun checkLoot(dream: ServerLevel, box: BoundingBox, vararg expected: net.minecraft.resources.ResourceLocation) {
        val tables = BlockPos.betweenClosedStream(box)
            .filter { dream.getBlockState(it).`is`(Blocks.CHEST) }
            .map { (dream.getBlockEntity(it) as ChestBlockEntity).saveWithoutMetadata().getString("LootTable") }
            .toList().toSet()
        check(tables == expected.map { it.toString() }.toSet()) { "chests hold $tables, expected ${expected.toList()}" }
    }

    private companion object {
        const val BATCH = "mirabilia_dream_structures"
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
