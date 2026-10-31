package com.mirabiliahexica.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getPositiveDoubleUnderInclusive
import at.petrak.hexcasting.api.casting.getVec3
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.advancement.MirabiliaAdvancements

/**
 * Freeze: position, radius → nothing.
 *
 * Inside the sphere, still water becomes ice, and every dry surface gains one layer of snow. Casting again
 * adds another layer, up to a full 8-layer block; snow never stacks above that.
 */
object OpFreeze : SpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val center = args.getVec3(0, argc)
        val radius = args.getPositiveDoubleUnderInclusive(1, FrostArea.MAX_RADIUS, argc)
        env.assertVecInRange(center)

        return SpellAction.Result(
            Spell(center, radius),
            FrostArea.cost(radius),
            listOf(ParticleSpray.cloud(center, radius)),
        )
    }

    private data class Spell(val center: Vec3, val radius: Double) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            val world = env.world
            val player = env.castingEntity as? ServerPlayer

            // Decide every change from the world as it was before the cast, so one cast adds exactly one layer
            val changes = FrostArea.positions(env, center, radius).mapNotNull { pos ->
                frozenState(env, pos)?.let { pos to it }
            }

            var changed = 0
            for ((pos, newState) in changes) {
                val stack = ItemStack(if (newState.`is`(Blocks.ICE)) Items.ICE else Items.SNOW)
                if (!IXplatAbstractions.INSTANCE.isPlacingAllowed(world, pos, stack, player)) continue
                world.setBlockAndUpdate(pos, newState)
                changed++
                if (player != null && newState.`is`(Blocks.SNOW) && newState.getValue(SnowLayerBlock.LAYERS) == SnowLayerBlock.MAX_HEIGHT) {
                    MirabiliaAdvancements.award(player, "hoarfrost")
                }
            }

            if (changed > 0) {
                world.playSound(null, center.x, center.y, center.z,
                    SoundEvents.POWDER_SNOW_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f)
            }
        }

        private fun frozenState(env: CastingEnvironment, pos: BlockPos): BlockState? {
            val world = env.world
            val state = world.getBlockState(pos)

            if (state.`is`(Blocks.WATER) && world.getFluidState(pos).`is`(Fluids.WATER) && world.getFluidState(pos).isSource) {
                return Blocks.ICE.defaultBlockState()
            }

            if (state.`is`(Blocks.SNOW)) {
                val layers = state.getValue(SnowLayerBlock.LAYERS)
                return if (layers < SnowLayerBlock.MAX_HEIGHT) state.setValue(SnowLayerBlock.LAYERS, layers + 1) else null
            }

            if (state.isAir) {
                val below = world.getBlockState(pos.below())
                // Full snow stacks stop here instead of growing into pillars
                if (below.`is`(Blocks.SNOW) || below.`is`(Blocks.SNOW_BLOCK)) return null
                val snow = Blocks.SNOW.defaultBlockState()
                if (snow.canSurvive(world, pos)) return snow
            }

            return null
        }
    }
}
