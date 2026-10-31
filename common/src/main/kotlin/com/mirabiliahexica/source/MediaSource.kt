package com.mirabiliahexica.source

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import com.mirabiliahexica.machines.MediaStore
import net.minecraft.world.Container
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Something a hex draws its media from before anything else (see [MediaSources]): a container block, or an
 * entity that holds media items (a dropped stack, a chest minecart…). (Media in the caster's own inventory needs
 * no binding: hexes draw on it anyway.)
 *
 * Sources are found again by where they are (position, UUID) every time media is drawn, since a hex
 * may outlive the objects it saw: the staff's hex lasts across many casts, a standing spell's for minutes.
 * Blocks and entities must also still be within the casting's reach then (ambit, wards…); a source out of
 * reach gives nothing, and the hex pays the usual way.
 */
sealed interface MediaSource {
    /** The stacks to draw from, in order, and a callback to run after any of them changed. */
    fun stacks(env: CastingEnvironment): Pair<List<ItemStack>, () -> Unit>?

    fun save(): CompoundTag

    /** Draws up to [cost]; returns how much was taken (possibly more than asked, as Hex wastes part-items). */
    fun withdraw(env: CastingEnvironment, cost: Long, simulate: Boolean): Long {
        val (stacks, changed) = stacks(env) ?: return 0
        var remaining = cost
        for (stack in stacks) {
            if (remaining <= 0) break
            val holder = HexAPI.instance().findMediaHolder(stack) ?: continue
            if (!holder.canProvide()) continue
            remaining -= holder.withdrawMedia(remaining, simulate)
        }
        if (!simulate && remaining != cost) changed()
        return cost - remaining
    }

    /** All the media it could give right now. */
    fun available(env: CastingEnvironment): Long {
        val (stacks, _) = stacks(env) ?: return 0
        return stacks.sumOf { stack ->
            val holder = HexAPI.instance().findMediaHolder(stack)
            if (holder != null && holder.canProvide()) holder.withdrawMedia(-1, true) else 0L
        }
    }

    /**
     * A container block (chest, barrel, hopper…) or a machine that holds media (a collector) in the cast's world,
     * while loaded and within reach.
     */
    data class Block(val pos: BlockPos) : MediaSource {
        private fun reachable(env: CastingEnvironment) = env.world.isLoaded(pos) && env.isVecInAmbit(Vec3.atCenterOf(pos))

        private fun store(env: CastingEnvironment) = if (reachable(env)) env.world.getBlockEntity(pos) as? MediaStore else null

        override fun stacks(env: CastingEnvironment): Pair<List<ItemStack>, () -> Unit>? {
            if (!reachable(env)) return null
            val container = HopperBlockEntity.getContainerAt(env.world, pos) ?: return null
            return container.contents() to container::setChanged
        }

        override fun withdraw(env: CastingEnvironment, cost: Long, simulate: Boolean): Long =
            store(env)?.withdraw(cost, simulate) ?: super.withdraw(env, cost, simulate)

        override fun available(env: CastingEnvironment): Long = store(env)?.media ?: super.available(env)

        override fun save() = CompoundTag().apply {
            putString(TYPE, "block")
            put("pos", NbtUtils.writeBlockPos(pos))
        }
    }

    /** A dropped stack, or an entity with an inventory (chest minecart…), in the cast's world, within reach. */
    data class Entity(val uuid: UUID) : MediaSource {
        override fun stacks(env: CastingEnvironment): Pair<List<ItemStack>, () -> Unit>? {
            val entity = env.world.getEntity(uuid) ?: return null
            if (!env.isEntityInRange(entity)) return null
            return when (entity) {
                is ItemEntity -> {
                    val stack = entity.item
                    listOf(stack) to {
                        if (stack.isEmpty) entity.discard() else entity.item = stack.copy()
                    }
                }
                is Container -> entity.contents() to entity::setChanged
                else -> null
            }
        }

        override fun save() = CompoundTag().apply {
            putString(TYPE, "entity")
            putUUID("uuid", uuid)
        }
    }

    companion object {
        private const val TYPE = "type"

        fun load(tag: CompoundTag): MediaSource? = when (tag.getString(TYPE)) {
            "block" -> Block(NbtUtils.readBlockPos(tag.getCompound("pos")))
            "entity" -> if (tag.hasUUID("uuid")) Entity(tag.getUUID("uuid")) else null
            else -> null
        }

        /** Whether the block at [pos] could serve as a source at all: a container or a media-holding machine. */
        fun canHoldAt(level: Level, pos: BlockPos) =
            level.getBlockEntity(pos) is MediaStore || HopperBlockEntity.getContainerAt(level, pos) != null

        /** Whether this entity could serve as a source at all (players can't: their media is their own). */
        fun canHold(entity: net.minecraft.world.entity.Entity) = entity is ItemEntity || (entity is Container && entity !is net.minecraft.world.entity.player.Player)

        private fun Container.contents() = (0 until containerSize).map(::getItem)
    }
}
