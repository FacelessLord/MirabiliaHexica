package com.mirabiliahexica.wards

import com.mirabiliahexica.registry.MirabiliaBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/** Holds a ward stone's owner, and tells [Wards] while it is loaded. */
class WardStoneBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(MirabiliaBlockEntities.WARD_STONE.value, pos, state) {
    var owner: UUID? = null
        set(value) {
            field = value
            setChanged()
        }

    /** Whether the owner's own casts pass through. */
    var ownerExempt = true
        set(value) {
            field = value
            setChanged()
        }

    val kind get() = (blockState.block as WardStoneBlock).kind

    fun spares(caster: UUID?) = ownerExempt && caster != null && caster == owner

    override fun clearRemoved() {
        super.clearRemoved()
        level?.let { if (!it.isClientSide) Wards.add(it, this) }
    }

    override fun setRemoved() {
        super.setRemoved()
        level?.let { Wards.remove(it, this) }
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        owner = if (tag.hasUUID(OWNER)) tag.getUUID(OWNER) else null
        ownerExempt = !tag.contains(EXEMPT) || tag.getBoolean(EXEMPT)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        owner?.let { tag.putUUID(OWNER, it) }
        tag.putBoolean(EXEMPT, ownerExempt)
    }

    private companion object {
        const val OWNER = "owner"
        const val EXEMPT = "owner_exempt"
    }
}
