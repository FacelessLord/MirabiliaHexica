package com.mirabiliahexica.machines

import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.registry.MirabiliaBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import java.util.Locale
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

private fun dust(media: Long) = String.format(Locale.ROOT, "%.1f", media.toDouble() / MediaConstants.DUST_UNIT)

/** An Ambient or Solar Collector. Using it tells how full it is; comparators read the same. */
class CollectorBlock(private val solar: Boolean, properties: Properties) : BaseEntityBlock(properties) {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        if (solar) SolarCollectorBlockEntity(pos, state) else AmbientCollectorBlockEntity(pos, state)

    // BaseEntityBlock defaults to invisible
    @Deprecated("Deprecated in Java")
    override fun getRenderShape(state: BlockState) = RenderShape.MODEL

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (placer is Player) (level.getBlockEntity(pos) as? MediaStore)?.owner = placer.uuid
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide) return null
        return BlockEntityTicker { l, _, _, be ->
            if (l.gameTime % 20 != 0L) return@BlockEntityTicker
            when (be) {
                is AmbientCollectorBlockEntity -> be.second(l as ServerLevel)
                is SolarCollectorBlockEntity -> be.second(l as ServerLevel)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun use(state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        val store = level.getBlockEntity(pos) as? MediaStore ?: return InteractionResult.PASS
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("mirabilia.machine.holds", dust(store.media), dust(store.capacity)), true)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    @Deprecated("Deprecated in Java")
    override fun hasAnalogOutputSignal(state: BlockState) = true

    @Deprecated("Deprecated in Java")
    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos) = (level.getBlockEntity(pos) as? MediaStore)?.signal() ?: 0
}

/** The Media Discharger (see [MediaDischarger]). It needs no block entity: the ambient media is the chunk's. */
class DischargerBlock(properties: Properties) : Block(properties) {
    @Deprecated("Deprecated in Java")
    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, old: BlockState, moved: Boolean) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 20)
    }

    @Deprecated("Deprecated in Java")
    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        MediaDischarger.second(level, pos)
        level.scheduleTick(pos, this, 20)
    }
}

/** The Crystallizer (see [CrystallizerBlockEntity]). Use it with dust to put dust in, empty-handed to take shards. */
class CrystallizerBlock(properties: Properties) : BaseEntityBlock(properties) {
    override fun newBlockEntity(pos: BlockPos, state: BlockState) = CrystallizerBlockEntity(pos, state)

    @Deprecated("Deprecated in Java")
    override fun getRenderShape(state: BlockState) = RenderShape.MODEL

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if (level.isClientSide) null
        else createTickerHelper(type, MirabiliaBlockEntities.CRYSTALLIZER.value) { l, _, _, be -> be.tick(l as ServerLevel) }

    @Deprecated("Deprecated in Java")
    override fun use(state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        val machine = level.getBlockEntity(pos) as? CrystallizerBlockEntity ?: return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        val held = player.getItemInHand(hand)
        when {
            held.`is`(HexItems.AMETHYST_DUST) -> machine.insert(held)
            held.isEmpty && machine.shards > 0 -> player.inventory.placeItemBackInInventory(machine.takeShards())
            else -> {
                val media = machine.stores(level as ServerLevel).sumOf { it.media }
                player.displayClientMessage(Component.translatable("mirabilia.machine.crystallizer", machine.dust, machine.shards, dust(media)), true)
            }
        }
        return InteractionResult.CONSUME
    }

    @Deprecated("Deprecated in Java")
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block)) (level.getBlockEntity(pos) as? CrystallizerBlockEntity)?.let { Containers.dropContents(level, pos, it) }
        @Suppress("DEPRECATION")
        super.onRemove(state, level, pos, newState, moved)
    }

    @Deprecated("Deprecated in Java")
    override fun hasAnalogOutputSignal(state: BlockState) = true

    @Deprecated("Deprecated in Java")
    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos) =
        AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos))
}
