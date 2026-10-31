package com.mirabiliahexica.worldgen

import at.petrak.hexcasting.common.blocks.akashic.BlockAkashicBookshelf
import at.petrak.hexcasting.common.blocks.akashic.BlockEntityAkashicBookshelf
import at.petrak.hexcasting.common.lib.HexBlocks
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaStructurePieces
import com.mirabiliahexica.registry.MirabiliaStructures
import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureType
import java.util.Optional
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The listening post: the rarest thing in the dream. A vast egg of inkstone, some 100–120 blocks across, sits
 * half sunk in the land, lit from within by bands and specks of glyphstone, open to the sky at its crown and
 * entered by one arch on its south side. Inside, the ground is levelled into a dark floor of inkstone and deep
 * dream glass, inlaid with a magic circle in glowing glyphstone (rings, a hexagram, a hexagon, spokes and twelve
 * lesser circles). Six obelisks stand on its middle ring. Its black heart is raised into a dais, and on it an
 * Akashic Record stands among shelves of hexes left by earlier dreamers ([DreamLibrary]), with chests of the
 * dream's best treasure around it. See [ListeningPostPiece].
 */
class ListeningPost(settings: StructureSettings) : Structure(settings) {
    override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
        val x = context.chunkPos().middleBlockX
        val z = context.chunkPos().middleBlockZ
        // The floor is dug (or built up) to the ground's middle height across the circle
        val heights = (-36..36 step 18).flatMap { dx -> (-36..36 step 18).map { dz -> dx to dz } }.map { (dx, dz) ->
            context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState())
        }.sorted()
        val y = heights[heights.size / 2]
        if (y <= context.heightAccessor().minBuildHeight + 16) return Optional.empty()
        val seed = context.random().nextLong()
        val origin = BlockPos(x, y, z)
        return Optional.of(GenerationStub(origin) { it.addPiece(ListeningPostPiece(seed, origin)) })
    }

    override fun type(): StructureType<*> = MirabiliaStructures.LISTENING_POST.value

    companion object {
        val CODEC: Codec<ListeningPost> = simpleCodec(::ListeningPost)
    }
}

class ListeningPostPiece : DreamPiece {
    constructor(seed: Long, origin: BlockPos) : super(MirabiliaStructurePieces.LISTENING_POST.value, seed, origin, box(seed, origin))
    constructor(tag: CompoundTag) : super(MirabiliaStructurePieces.LISTENING_POST.value, tag)

    private class Layout(seed: Long) {
        private val r = Random(seed)
        val radius = 40 + r.nextInt(17)
        val obeliskHeight = 8 + r.nextInt(5)
        /** The egg: its horizontal radius, and its height above the floor (taller than wide). */
        val domeRadius = radius + 5f
        val domeHeight = domeRadius * (1.1f + r.nextFloat() * 0.15f)
        /** The whole figure is turned by this much, so no two circles line up the same way. */
        val turn = r.nextFloat() * Mth.TWO_PI
    }

    /** One stroke of the figure: a line segment, in blocks from the centre. */
    private data class Segment(val ax: Float, val az: Float, val bx: Float, val bz: Float)

    override fun postProcess(
        level: WorldGenLevel, structureManager: StructureManager, generator: ChunkGenerator, random: RandomSource,
        box: BoundingBox, chunkPos: ChunkPos, pivot: BlockPos,
    ) {
        val l = Layout(seed)
        val strokes = strokes(l)
        val reach = Mth.ceil(l.domeRadius) + 1
        for (x in maxOf(box.minX(), origin.x - reach)..minOf(box.maxX(), origin.x + reach)) {
            for (z in maxOf(box.minZ(), origin.z - reach)..minOf(box.maxZ(), origin.z + reach)) {
                val dx = (x - origin.x).toFloat()
                val dz = (z - origin.z).toFloat()
                val d = hypot(dx, dz)
                if (d > l.domeRadius) continue
                dome(level, box, l, x, z, dx, dz, d)
                floor(level, box, l, strokes, x, z, dx, dz, d)
            }
        }
        approach(level, box, l)
        for (k in 0 until 6) {
            val angle = l.turn + k * Mth.TWO_PI / 6
            obelisk(level, box, l, origin.x + Mth.floor(cos(angle) * l.radius * MIDDLE), origin.z + Mth.floor(sin(angle) * l.radius * MIDDLE))
        }
        library(level, box, random)
    }

    /** One column of the egg: hollow inside (whatever ground was there is gone), a two-block shell around it. */
    private fun dome(level: WorldGenLevel, box: BoundingBox, l: Layout, x: Int, z: Int, dx: Float, dz: Float, d: Float) {
        val shell = SHELL / l.domeRadius
        for (dy in 0..Mth.ceil(l.domeHeight)) {
            val pos = BlockPos(x, origin.y + dy, z)
            val e = sqrt((d / l.domeRadius) * (d / l.domeRadius) + (dy / l.domeHeight) * (dy / l.domeHeight))
            if (e > 1f) break
            val inside = e <= 1f - shell
            // The arch on the south side, five wide and five tall, and the open crown
            val arch = dz > 0 && d > l.domeRadius - 4 && (abs(dx) <= 2 && dy <= 3 || abs(dx) <= 1 && dy == 4)
            val crown = d < CROWN
            set(level, box, pos, when {
                inside || arch || crown -> AIR
                dy % BAND == BAND / 2 -> GLYPH
                hash(pos.x, pos.y, pos.z, 3) < 0.035f -> GLYPH
                else -> INK
            })
        }
    }

    /** The levelled floor, inlaid with the circle, and the raised dais in the middle. */
    private fun floor(level: WorldGenLevel, box: BoundingBox, l: Layout, strokes: List<Segment>, x: Int, z: Int, dx: Float, dz: Float, d: Float) {
        val surface = BlockPos(x, origin.y - 1, z)
        // Where the ground dipped, build it back up so the floor is whole
        if (level.getBlockState(surface).isAir) set(level, box, surface, FLOOR)
        foundation(level, box, surface, FLOOR)
        val dais = l.radius * INNER
        set(level, box, surface, when {
            inFigure(l, strokes, dx, dz, d) -> GLYPH
            d < dais -> INK
            hash(x, origin.y, z, 2) < 0.3f -> DEEP_GLASS
            else -> INK
        })
        // The dais: two blocks above the floor, with a one-block step round it and a glowing rim on top
        when {
            d < dais - 1.8f -> {
                set(level, box, surface.above(1), INK)
                set(level, box, surface.above(2), if (d > dais - 2.8f) GLYPH else INK)
            }
            d < dais - 0.8f -> set(level, box, surface.above(1), INK)
        }
    }

    /** A short cutting from the arch outwards, in case the land outside has buried it. */
    private fun approach(level: WorldGenLevel, box: BoundingBox, l: Layout) {
        val start = Mth.floor(l.domeRadius) - 3
        for (dz in start..start + APPROACH) for (dx in -2..2) {
            val path = BlockPos(origin.x + dx, origin.y - 1, origin.z + dz)
            if (hypot(dx.toFloat(), dz.toFloat()) <= l.domeRadius - 2) continue
            set(level, box, path, INK)
            foundation(level, box, path, FLOOR)
            for (dy in 0..4) if (abs(dx) <= 1 || dy <= 3) set(level, box, path.above(dy + 1), AIR)
        }
    }

    private fun strokes(l: Layout): List<Segment> {
        fun point(radius: Float, angle: Float) = cos(angle) * radius to sin(angle) * radius
        fun polygon(radius: Float, corners: Int, start: Float, step: Int = 1) = (0 until corners).map { k ->
            val (ax, az) = point(radius, start + k * step * Mth.TWO_PI / corners)
            val (bx, bz) = point(radius, start + (k + 1) * step * Mth.TWO_PI / corners)
            Segment(ax, az, bx, bz)
        }
        val band = l.radius - 3f
        val middle = l.radius * MIDDLE
        val inner = l.radius * INNER
        // A hexagram on the outer band, a hexagon on the middle ring, and spokes from the inner ring outwards
        return polygon(band, 3, l.turn + Mth.HALF_PI) + polygon(band, 3, l.turn - Mth.HALF_PI) +
            polygon(middle, 6, l.turn) +
            (0 until 6).map { k ->
                val angle = l.turn + (k + 0.5f) * Mth.TWO_PI / 6
                val (ax, az) = point(inner, angle)
                val (bx, bz) = point(middle, angle)
                Segment(ax, az, bx, bz)
            }
    }

    private fun inFigure(l: Layout, strokes: List<Segment>, dx: Float, dz: Float, d: Float): Boolean {
        val rings = listOf(l.radius.toFloat(), l.radius - 3f, l.radius * MIDDLE, l.radius * INNER)
        if (rings.any { abs(d - it) < 0.7f }) return true
        if (strokes.any { distanceTo(it, dx, dz) < 0.6f }) return true
        // Twelve lesser circles between the middle ring and the outer band
        val between = (l.radius * MIDDLE + l.radius - 3f) / 2
        val small = minOf(4f, (l.radius - 3f - l.radius * MIDDLE) / 2 - 1.5f)
        return (0 until 12).any { k ->
            val angle = l.turn + (k + 0.5f) * Mth.TWO_PI / 12
            abs(hypot(dx - cos(angle) * between, dz - sin(angle) * between) - small) < 0.6f
        }
    }

    private fun distanceTo(s: Segment, x: Float, z: Float): Float {
        val vx = s.bx - s.ax
        val vz = s.bz - s.az
        val t = (((x - s.ax) * vx + (z - s.az) * vz) / (vx * vx + vz * vz)).coerceIn(0f, 1f)
        return hypot(x - (s.ax + t * vx), z - (s.az + t * vz))
    }

    /** A plain black pillar crowned with glyphstone, standing on the floor. */
    private fun obelisk(level: WorldGenLevel, box: BoundingBox, l: Layout, cx: Int, cz: Int) {
        val base = origin.y
        for (dx in -1..1) for (dz in -1..1) {
            val foot = BlockPos(cx + dx, base, cz + dz)
            foundation(level, box, foot, INK)
            val height = if (dx == 0 && dz == 0) l.obeliskHeight else l.obeliskHeight - 2
            for (dy in 0 until height) set(level, box, foot.above(dy), INK)
        }
        set(level, box, BlockPos(cx, base + l.obeliskHeight, cz), GLYPH)
        set(level, box, BlockPos(cx, base + l.obeliskHeight + 1, cz), GLYPH)
    }

    /**
     * On the dais: the Akashic Record with two rings of shelves round it (six holding this post's
     * [DreamLibrary] hexes, the rest empty), a glyphstone above, and a chest at each quarter.
     */
    private fun library(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
        val top = origin.y + 2 // standing on the dais
        set(level, box, BlockPos(origin.x, top, origin.z), HexBlocks.AKASHIC_RECORD.defaultBlockState())
        set(level, box, BlockPos(origin.x, top + 1, origin.z), GLYPH)
        val ring = listOf(-1 to -1, 0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0)
        val books = DreamLibrary.pick(seed)
        var next = 0
        for (layer in 0..1) for ((dx, dz) in ring) {
            val pos = BlockPos(origin.x + dx, top + layer, origin.z + dz)
            // Facing outwards, so the spines (and their key patterns) can be read from around the dais
            val facing = if (abs(dx) >= abs(dz)) (if (dx > 0) Direction.EAST else Direction.WEST)
                else (if (dz > 0) Direction.SOUTH else Direction.NORTH)
            // The hexes go on the sides (not the corners), lower ring first
            val entry = if (dx == 0 || dz == 0) books.getOrNull(next) else null
            if (entry != null) next++
            shelf(level, box, pos, facing, entry)
        }
        for (dir in Direction.Plane.HORIZONTAL) {
            chest(level, box, BlockPos(origin.x + dir.stepX * 4, top, origin.z + dir.stepZ * 4), dir.opposite, LOOT, random)
        }
    }

    private fun shelf(level: WorldGenLevel, box: BoundingBox, pos: BlockPos, facing: Direction, entry: DreamLibrary.Shelf?) {
        if (!box.isInside(pos)) return
        val state = HexBlocks.AKASHIC_BOOKSHELF.defaultBlockState()
            .setValue(BlockAkashicBookshelf.FACING, facing)
            .setValue(BlockAkashicBookshelf.HAS_BOOKS, entry != null)
        level.setBlock(pos, state, 2)
        if (entry == null) return
        // Written straight into the shelf's data: during world generation the shelf has no world to update
        val shelf = level.getBlockEntity(pos) as? BlockEntityAkashicBookshelf ?: return
        shelf.load(CompoundTag().apply {
            put(BlockEntityAkashicBookshelf.TAG_PATTERN, entry.key.serializeToNBT())
            put(BlockEntityAkashicBookshelf.TAG_IOTA, entry.iotaTag)
        })
        shelf.setChanged()
    }

    companion object {
        val LOOT = Mirabilia.id("chests/listening_post")
        /** Ring radii, as shares of the outer radius. */
        private const val MIDDLE = 0.62f
        private const val INNER = 0.3f
        /** The egg's shell thickness, its crown opening's radius, and how often a glowing band rings it. */
        private const val SHELL = 2f
        private const val CROWN = 3f
        private const val BAND = 12
        /** How far the cutting runs out from the arch. */
        private const val APPROACH = 12

        private val AIR = Blocks.AIR.defaultBlockState()
        private val INK get() = MirabiliaBlocks.INKSTONE.value.defaultBlockState()
        private val GLYPH get() = MirabiliaBlocks.GLYPHSTONE.value.defaultBlockState()
        private val DEEP_GLASS get() = MirabiliaBlocks.DEEP_DREAM_GLASS.value.defaultBlockState()
        /** What fills in under the floor where the ground dipped. */
        private val FLOOR get() = MirabiliaBlocks.DEEP_DREAM_GLASS.value.defaultBlockState()

        private fun box(seed: Long, origin: BlockPos): BoundingBox {
            val l = Layout(seed)
            val reach = Mth.ceil(l.domeRadius) + APPROACH + 2
            return BoundingBox(origin.x - reach, origin.y - 40, origin.z - reach, origin.x + reach, origin.y + Mth.ceil(l.domeHeight) + 2, origin.z + reach)
        }
    }
}
