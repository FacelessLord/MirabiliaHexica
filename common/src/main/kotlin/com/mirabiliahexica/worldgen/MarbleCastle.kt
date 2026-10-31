package com.mirabiliahexica.worldgen

import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaStructurePieces
import com.mirabiliahexica.registry.MirabiliaStructures
import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureType
import java.util.Optional
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A mind castle: marble walls some dreamer long ago built for themselves out of memory. Every one differs:
 * size, wall and tower heights, round or square keep, roofed or crenellated towers, which side the gate faces,
 * and how far it has crumbled. See [MarbleCastlePiece].
 */
class MarbleCastle(settings: StructureSettings) : Structure(settings) {
    override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
        val x = context.chunkPos().middleBlockX
        val z = context.chunkPos().middleBlockZ
        // Stand at the ground's middle height across the footprint: foundations fill in below where it dips,
        // and the terrain is smoothed down to the floor where it rises (beard_thin, from the box's bottom)
        val heights = (-12..12 step 12).flatMap { dx -> (-12..12 step 12).map { dz -> dx to dz } }.map { (dx, dz) ->
            context.chunkGenerator().getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState())
        }.sorted()
        val y = heights[heights.size / 2]
        if (y <= context.heightAccessor().minBuildHeight + 16) return Optional.empty()
        val seed = context.random().nextLong()
        val origin = BlockPos(x, y, z)
        return Optional.of(GenerationStub(origin) { it.addPiece(MarbleCastlePiece(seed, origin)) })
    }

    override fun type(): StructureType<*> = MirabiliaStructures.MARBLE_CASTLE.value

    companion object {
        val CODEC: Codec<MarbleCastle> = simpleCodec(::MarbleCastle)
    }
}

class MarbleCastlePiece : DreamPiece {
    constructor(seed: Long, origin: BlockPos) : super(MirabiliaStructurePieces.MARBLE_CASTLE.value, seed, origin, box(seed, origin))
    constructor(tag: CompoundTag) : super(MirabiliaStructurePieces.MARBLE_CASTLE.value, tag)

    /** Everything about this castle's shape, drawn from its seed. Coordinates are relative to the origin. */
    private class Layout(seed: Long) {
        private val r = Random(seed)
        /** Distance from the centre to the outside of the walls. */
        val half = 7 + r.nextInt(7)
        val wallHeight = 6 + r.nextInt(4)
        val towerRadius = 2 + r.nextInt(3)
        val towerHeight = wallHeight + 4 + r.nextInt(6)
        val roofs = r.nextBoolean()
        val keepRound = r.nextBoolean()
        val keepRadius = 3 + r.nextInt(3)
        val keepHeight = wallHeight + 8 + r.nextInt(11)
        /** Share of the upper stonework that has fallen; most castles stand whole. */
        val ruin = if (r.nextFloat() < 0.4f) 0.1f + r.nextFloat() * 0.25f else 0f
        val gate: Direction = Direction.from2DDataValue(r.nextInt(4))
    }

    override fun postProcess(
        level: WorldGenLevel, structureManager: StructureManager, generator: ChunkGenerator, random: RandomSource,
        box: BoundingBox, chunkPos: ChunkPos, pivot: BlockPos,
    ) {
        val l = Layout(seed)
        courtyard(level, box, l)
        walls(level, box, l)
        for (sx in listOf(-1, 1)) for (sz in listOf(-1, 1)) tower(level, box, l, sx * l.half, sz * l.half)
        keep(level, box, l, random)
    }

    private fun at(dx: Int, dy: Int, dz: Int) = BlockPos(origin.x + dx, origin.y + dy, origin.z + dz)

    /** Stonework, unless this castle has crumbled there: the higher up, the likelier it's gone. */
    private fun stone(level: WorldGenLevel, box: BoundingBox, l: Layout, pos: BlockPos, state: BlockState, top: Int) {
        val height = (pos.y - origin.y).toFloat() / top
        val fallen = l.ruin > 0 && hash(pos.x, pos.y, pos.z) < l.ruin * (0.3f + 1.4f * height)
        set(level, box, pos, if (fallen) AIR else state)
    }

    private fun courtyard(level: WorldGenLevel, box: BoundingBox, l: Layout) {
        val inner = l.half - 2
        for (dx in -inner..inner) for (dz in -inner..inner) {
            val floor = at(dx, 0, dz)
            set(level, box, floor, if (hash(floor.x, 0, floor.z, 1) < 0.15f) PEBBLES else POLISHED)
            foundation(level, box, floor, MARBLE)
            for (dy in 1..l.wallHeight + 2) set(level, box, at(dx, dy, dz), AIR)
        }
    }

    private fun walls(level: WorldGenLevel, box: BoundingBox, l: Layout) {
        val h = l.half
        for (dx in -h..h) for (dz in -h..h) {
            val edge = maxOf(abs(dx), abs(dz))
            if (edge < h - 1) continue
            // Gate: three wide and four tall through the middle of one side
            val along = if (l.gate.axis == Direction.Axis.Z) dx else dz
            val onGateSide = (l.gate.stepX != 0 && dx == l.gate.stepX * edge && abs(dx) >= h - 1) ||
                (l.gate.stepZ != 0 && dz == l.gate.stepZ * edge && abs(dz) >= h - 1)
            val gate = onGateSide && abs(along) <= 1
            set(level, box, at(dx, 0, dz), POLISHED)
            foundation(level, box, at(dx, 0, dz), MARBLE)
            for (dy in 1 until l.wallHeight) {
                if (gate && dy <= 4) set(level, box, at(dx, dy, dz), AIR)
                else stone(level, box, l, at(dx, dy, dz), if (dy == l.wallHeight - 1) POLISHED else BRICKS, l.towerHeight)
            }
            // Battlements on the outer edge
            if (edge == h && (dx + dz) % 2 == 0) stone(level, box, l, at(dx, l.wallHeight, dz), POLISHED, l.towerHeight)
        }
    }

    private fun tower(level: WorldGenLevel, box: BoundingBox, l: Layout, cx: Int, cz: Int) {
        val r = l.towerRadius
        for (dx in -r - 1..r + 1) for (dz in -r - 1..r + 1) {
            val d = sqrt((dx * dx + dz * dz).toFloat())
            if (d > r + 0.5f) continue
            val x = cx + dx
            val z = cz + dz
            set(level, box, at(x, 0, z), BRICKS)
            foundation(level, box, at(x, 0, z), MARBLE)
            for (dy in 1 until l.towerHeight) {
                val band = dy % 4 == 3 && d > r - 0.5f
                stone(level, box, l, at(x, dy, z), if (band) DEEP_GLASS else BRICKS, l.towerHeight)
            }
            if (!l.roofs && d > r - 0.6f && (dx + dz) % 2 == 0) stone(level, box, l, at(x, l.towerHeight, z), POLISHED, l.towerHeight)
        }
        if (l.roofs) {
            // A cone of dark glass
            for (k in 0..r + 1) {
                val radius = r + 1 - k + 0.5f
                for (dx in -r - 1..r + 1) for (dz in -r - 1..r + 1) {
                    if (dx * dx + dz * dz <= radius * radius) stone(level, box, l, at(cx + dx, l.towerHeight + k, cz + dz), DEEP_GLASS, l.towerHeight + r)
                }
            }
        }
    }

    private fun keep(level: WorldGenLevel, box: BoundingBox, l: Layout, random: RandomSource) {
        val r = l.keepRadius
        val top = l.keepHeight
        // The ladder runs up the inside of the wall facing away from the gate
        val back = l.gate.opposite
        val ladderX = back.stepX * (r - 1)
        val ladderZ = back.stepZ * (r - 1)
        fun shell(dx: Int, dz: Int): Boolean? = if (l.keepRound) {
            val d = sqrt((dx * dx + dz * dz).toFloat())
            if (d > r + 0.5f) null else d > r - 0.5f
        } else {
            if (abs(dx) > r || abs(dz) > r) null else abs(dx) == r || abs(dz) == r
        }
        for (dx in -r..r) for (dz in -r..r) {
            val isShell = shell(dx, dz) ?: continue
            set(level, box, at(dx, 0, dz), POLISHED)
            foundation(level, box, at(dx, 0, dz), MARBLE)
            val ladder = dx == ladderX && dz == ladderZ
            for (dy in 1..top) {
                val pos = at(dx, dy, dz)
                when {
                    isShell -> {
                        val mid = dx == 0 || dz == 0
                        val floorLevel = dy % 5
                        // The doorway faces the gate: the middle of that side, two blocks tall
                        val door = dy <= 2 && dx == l.gate.stepX * r && dz == l.gate.stepZ * r
                        when {
                            door -> set(level, box, pos, AIR)
                            mid && (floorLevel == 2 || floorLevel == 3) -> stone(level, box, l, pos, WINDOW, top)
                            else -> stone(level, box, l, pos, if (dy == top) POLISHED else BRICKS, top)
                        }
                    }
                    ladder -> set(level, box, pos, LADDER.setValue(LadderBlock.FACING, l.gate))
                    // Floors every five blocks, but none just under the roof, where it would double it
                    dy == top || (dy % 5 == 0 && top - dy >= 3) -> set(level, box, pos, POLISHED)
                    else -> set(level, box, pos, AIR)
                }
            }
            // Battlements round the roof, and a hole for the ladder to come out of
            if (isShell && (dx + dz) % 2 == 0) stone(level, box, l, at(dx, top + 1, dz), POLISHED, top + 1)
            if (ladder) set(level, box, at(dx, top + 1, dz), AIR)
        }
        // One chest on the ground floor, a better one on the roof
        val side = l.gate.clockWise
        chest(level, box, at(side.stepX * (r - 1), 1, side.stepZ * (r - 1)), side.opposite, LOOT, random)
        chest(level, box, at(-side.stepX * (r - 1), top + 1, -side.stepZ * (r - 1)), side, KEEP_LOOT, random)
    }

    companion object {
        val LOOT = Mirabilia.id("chests/marble_castle")
        val KEEP_LOOT = Mirabilia.id("chests/marble_castle_keep")

        private val AIR = Blocks.AIR.defaultBlockState()
        private val LADDER = Blocks.LADDER.defaultBlockState()
        private val MARBLE get() = MirabiliaBlocks.MARBLE.value.defaultBlockState()
        private val POLISHED get() = MirabiliaBlocks.POLISHED_MARBLE.value.defaultBlockState()
        private val BRICKS get() = MirabiliaBlocks.MARBLE_BRICKS.value.defaultBlockState()
        private val PEBBLES get() = MirabiliaBlocks.MARBLE_PEBBLES.value.defaultBlockState()
        private val DEEP_GLASS get() = MirabiliaBlocks.DEEP_DREAM_GLASS.value.defaultBlockState()
        private val WINDOW get() = MirabiliaBlocks.DUSKY_DREAM_GLASS.value.defaultBlockState()

        private fun box(seed: Long, origin: BlockPos): BoundingBox {
            val l = Layout(seed)
            val reach = l.half + l.towerRadius + 2
            val height = maxOf(l.towerHeight + l.towerRadius + 3, l.keepHeight + 3)
            // The box starts at the floor, so the terrain is levelled to it (foundations still reach below)
            return BoundingBox(origin.x - reach, origin.y, origin.z - reach, origin.x + reach, origin.y + height, origin.z + reach)
        }
    }
}
