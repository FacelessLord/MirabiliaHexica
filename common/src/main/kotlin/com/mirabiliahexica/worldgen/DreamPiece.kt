package com.mirabiliahexica.worldgen

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType

/**
 * A procedurally built structure of the dream, drawn one chunk at a time. Its whole shape follows from [seed]
 * and [origin], which are all that's saved: every chunk it touches rebuilds the same layout and places only
 * its own part (inside the box it's handed). Randomness while placing comes from [hash], never from a running
 * random source, so the result doesn't depend on which chunk is generated first.
 */
abstract class DreamPiece : StructurePiece {
    val seed: Long
    val origin: BlockPos

    protected constructor(type: StructurePieceType, seed: Long, origin: BlockPos, box: BoundingBox) : super(type, 0, box) {
        this.seed = seed
        this.origin = origin
    }

    protected constructor(type: StructurePieceType, tag: CompoundTag) : super(type, tag) {
        seed = tag.getLong("seed")
        origin = NbtUtils.readBlockPos(tag.getCompound("origin"))
    }

    override fun addAdditionalSaveData(context: StructurePieceSerializationContext, tag: CompoundTag) {
        tag.putLong("seed", seed)
        tag.put("origin", NbtUtils.writeBlockPos(origin))
    }

    /** A stable pseudo-random number in [0, 1) for this structure and position (and [salt]). */
    protected fun hash(x: Int, y: Int, z: Int, salt: Int = 0): Float {
        var h = seed xor (x * 3129871L) xor (z * 116129781L) xor (y * 0x9E3779B97F4A7C15uL.toLong()) xor (salt * 0x632BE59BD9B4E019L)
        h = (h xor (h ushr 33)) * -0xae502812aa7333L
        h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
        h = h xor (h ushr 33)
        return ((h ushr 40).toFloat() / (1L shl 24).toFloat())
    }

    protected fun set(level: WorldGenLevel, box: BoundingBox, pos: BlockPos, state: BlockState) {
        if (box.isInside(pos)) level.setBlock(pos, state, 2)
    }

    /**
     * Fills from just below [pos] down to solid ground (at most [depth] blocks), so nothing floats. This may reach
     * below the piece's own box: the box passed to postProcess is the whole chunk column.
     */
    protected fun foundation(level: WorldGenLevel, box: BoundingBox, pos: BlockPos, state: BlockState, depth: Int = 24) {
        val p = pos.mutable().move(Direction.DOWN)
        repeat(depth) {
            if (!box.isInside(p)) return
            val current = level.getBlockState(p)
            if (!current.isAir && current.fluidState.isEmpty && !current.canBeReplaced()) return
            level.setBlock(p, state, 2)
            p.move(Direction.DOWN)
        }
    }

    protected fun chest(level: WorldGenLevel, box: BoundingBox, pos: BlockPos, facing: Direction, loot: ResourceLocation, random: RandomSource) {
        if (!box.isInside(pos)) return
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2)
        RandomizableContainerBlockEntity.setLootTable(level, random, pos, loot)
    }
}
