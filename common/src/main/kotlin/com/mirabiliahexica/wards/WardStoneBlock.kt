package com.mirabiliahexica.wards

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * A Warding Stone or a Silence Stone (see [Wards] for what each does). It remembers who placed it; that owner is
 * spared by default and can toggle this by using the stone with an empty hand.
 */
class WardStoneBlock(val kind: Wards.Kind, properties: Properties) : BaseEntityBlock(properties) {
    override fun newBlockEntity(pos: BlockPos, state: BlockState) = WardStoneBlockEntity(pos, state)

    // BaseEntityBlock defaults to invisible
    @Deprecated("Deprecated in Java")
    override fun getRenderShape(state: BlockState) = RenderShape.MODEL

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (placer is Player) (level.getBlockEntity(pos) as? WardStoneBlockEntity)?.owner = placer.uuid
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult,
    ): InteractionResult {
        val stone = level.getBlockEntity(pos) as? WardStoneBlockEntity ?: return InteractionResult.PASS
        if (!player.getItemInHand(hand).isEmpty || stone.owner != player.uuid) return InteractionResult.PASS
        if (!level.isClientSide) {
            stone.ownerExempt = !stone.ownerExempt
            player.displayClientMessage(Component.translatable("mirabilia.ward.owner_exempt.${stone.ownerExempt}"), true)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if (random.nextInt(3) != 0) return
        val x = pos.x + random.nextDouble()
        val z = pos.z + random.nextDouble()
        when (kind) {
            Wards.Kind.WARDING -> level.addParticle(ParticleTypes.ENCHANT, x, pos.y + 1.6, z, 0.0, -0.6, 0.0)
            Wards.Kind.SILENCE -> level.addParticle(ParticleTypes.SCULK_SOUL, x, pos.y + 1.05, z, 0.0, 0.02, 0.0)
        }
    }
}
