package com.mirabiliahexica.machines

import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.registry.MirabiliaBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * The Crystallizer: [DUST_IN] Amethyst Dust and [MEDIA_COST] of media from collectors within [RANGE] blocks make an
 * Amethyst Shard, one every [TIME] ticks. Dust goes in from the top and sides (hoppers, or by hand); shards come
 * out the bottom. It draws on Solar Collectors before any other, and remembers whether a shard was made of sunlight
 * alone ([lastSolar]).
 */
class CrystallizerBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(MirabiliaBlockEntities.CRYSTALLIZER.value, pos, state), WorldlyContainer {
    private val items = NonNullList.withSize(2, ItemStack.EMPTY)
    private var progress = 0

    /** Whether the last shard's media all came from Solar Collectors. */
    var lastSolar = false
        private set

    fun tick(level: ServerLevel) {
        if (!Features.isOn("crystallizer")) return
        if (!canMake()) {
            progress = 0
            return
        }
        if (++progress < TIME) return
        progress = 0
        val stores = stores(level)
        if (stores.sumOf { it.media } < MEDIA_COST) return
        var remaining = MEDIA_COST
        var solar = true
        for (store in stores) {
            if (remaining <= 0) break
            val taken = store.withdraw(remaining)
            if (taken > 0 && store !is SolarCollectorBlockEntity) solar = false
            remaining -= taken
        }
        items[INPUT].shrink(DUST_IN)
        if (items[OUTPUT].isEmpty) items[OUTPUT] = ItemStack(Items.AMETHYST_SHARD) else items[OUTPUT].grow(1)
        lastSolar = solar
        setChanged()
        if (solar) MirabiliaAdvancements.awardNear(level, blockPos, "crystal_of_light")
        level.playSound(null, blockPos, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f)
    }

    private fun canMake() = items[INPUT].count >= DUST_IN && items[OUTPUT].count < items[OUTPUT].maxStackSize

    /** Collectors in reach, Solar ones first. */
    fun stores(level: ServerLevel): List<MediaStore> =
        BlockPos.betweenClosed(blockPos.offset(-RANGE, -RANGE, -RANGE), blockPos.offset(RANGE, RANGE, RANGE))
            .mapNotNull { level.getBlockEntity(it) as? MediaStore }
            .sortedBy { if (it is SolarCollectorBlockEntity) 0 else 1 }

    /** Puts dust from [stack] in; returns whether any went. */
    fun insert(stack: ItemStack): Boolean {
        if (!canPlaceItem(INPUT, stack)) return false
        val input = items[INPUT]
        val moved = minOf(stack.count, stack.maxStackSize - input.count)
        if (moved <= 0) return false
        if (input.isEmpty) items[INPUT] = stack.split(moved) else { input.grow(moved); stack.shrink(moved) }
        setChanged()
        return true
    }

    fun takeShards(): ItemStack = removeItemNoUpdate(OUTPUT).also { setChanged() }

    val dust get() = items[INPUT].count
    val shards get() = items[OUTPUT].count

    override fun getContainerSize() = items.size
    override fun isEmpty() = items.all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = items[slot]
    override fun removeItem(slot: Int, amount: Int): ItemStack = ContainerHelper.removeItem(items, slot, amount).also { setChanged() }
    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)
    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        setChanged()
    }
    override fun stillValid(player: Player) = false
    override fun clearContent() = items.clear()
    override fun canPlaceItem(slot: Int, stack: ItemStack) = slot == INPUT && stack.`is`(HexItems.AMETHYST_DUST)
    override fun getSlotsForFace(side: Direction) = if (side == Direction.DOWN) intArrayOf(OUTPUT) else intArrayOf(INPUT)
    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: Direction?) = side != Direction.DOWN && canPlaceItem(slot, stack)
    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, side: Direction) = side == Direction.DOWN && slot == OUTPUT

    override fun load(tag: CompoundTag) {
        super.load(tag)
        items.clear()
        ContainerHelper.loadAllItems(tag, items)
        progress = tag.getInt(PROGRESS)
        lastSolar = tag.getBoolean(LAST_SOLAR)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
        tag.putInt(PROGRESS, progress)
        tag.putBoolean(LAST_SOLAR, lastSolar)
    }

    companion object {
        const val INPUT = 0
        const val OUTPUT = 1
        val DUST_IN get() = Tuning.Machines.crystallizerDust
        val MEDIA_COST get() = Tuning.Machines.crystallizerMedia
        val TIME get() = Tuning.Machines.crystallizerTicks
        val RANGE get() = Tuning.Machines.crystallizerRange
        private const val PROGRESS = "progress"
        private const val LAST_SOLAR = "last_solar"
    }
}
