package com.mirabiliahexica.machines

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * A machine that holds media (Ambient and Solar Collectors): up to [capacity], in Hex's units. Hexes may bind it
 * as a wellspring, and Crystallizers nearby draw on it.
 */
abstract class MediaStore(type: BlockEntityType<*>, pos: BlockPos, state: BlockState, val capacity: Long) :
    BlockEntity(type, pos, state) {
    var media = 0L
        private set

    /** Who placed it, for the advancements its work earns. */
    var owner: UUID? = null
        set(value) {
            field = value
            setChanged()
        }

    val space get() = capacity - media

    /** Adds up to [amount]; returns how much fitted. */
    fun fill(amount: Long): Long {
        val added = amount.coerceIn(0, space)
        if (added > 0) {
            media += added
            changed()
        }
        return added
    }

    /** Takes up to [amount] (everything for -1); returns how much was taken. */
    fun withdraw(amount: Long, simulate: Boolean = false): Long {
        val taken = if (amount < 0) media else minOf(amount, media)
        if (!simulate && taken > 0) {
            media -= taken
            changed()
        }
        return taken
    }

    /** For comparators: 0 when empty, 15 when full. */
    fun signal() = if (media <= 0) 0 else 1 + (14 * media / capacity).toInt()

    private fun changed() {
        setChanged()
        // Comparators watch the block, not the block entity
        level?.updateNeighbourForOutputSignal(blockPos, blockState.block)
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        media = tag.getLong(MEDIA).coerceIn(0, capacity)
        owner = if (tag.hasUUID(OWNER)) tag.getUUID(OWNER) else null
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putLong(MEDIA, media)
        owner?.let { tag.putUUID(OWNER, it) }
    }

    private companion object {
        const val MEDIA = "media"
        const val OWNER = "owner"
    }
}
