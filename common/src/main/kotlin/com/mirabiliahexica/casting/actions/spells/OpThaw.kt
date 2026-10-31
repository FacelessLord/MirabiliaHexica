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
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.config.Tuning
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB

/**
 * Thaw: position, radius → nothing. The reverse of Freeze.
 *
 * Inside the sphere, every snow layer loses one layer (a snow block counts as 8 layers), and ice (including
 * frosted ice) turns back into water, or vanishes in dimensions too hot for water. Every creature in it loses
 * [Tuning.Spells.thawFrost] ticks of frost: enough to stop a fully frozen one taking freeze damage, not enough to
 * keep it safe for long in powder snow, which freezes it again a tick at a time.
 */
object OpThaw : SpellAction {
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

            val changes = FrostArea.positions(env, center, radius).mapNotNull { pos ->
                thawedState(env, pos)?.let { pos to it }
            }

            var changed = 0
            for ((pos, newState) in changes) {
                val oldState = world.getBlockState(pos)
                if (!IXplatAbstractions.INSTANCE.isBreakingAllowed(world, pos, oldState, player)) continue
                // Placing water schedules its own fluid tick, so meltwater flows like vanilla melting ice
                world.setBlockAndUpdate(pos, newState)
                changed++
            }

            // Creatures within reach lose some of their frost
            val warmed = world.getEntitiesOfClass(LivingEntity::class.java, AABB(center, center).inflate(radius)) {
                it.ticksFrozen > 0 && it.position().distanceTo(center) <= radius && env.isEntityInRange(it)
            }
            for (entity in warmed) entity.ticksFrozen = maxOf(0, entity.ticksFrozen - Tuning.Spells.thawFrost)

            if (changed > 0 || warmed.isNotEmpty()) {
                world.playSound(null, center.x, center.y, center.z,
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f)
            }
        }

        private fun thawedState(env: CastingEnvironment, pos: BlockPos): BlockState? {
            val world = env.world
            val state = world.getBlockState(pos)

            if (state.`is`(Blocks.SNOW)) {
                val layers = state.getValue(SnowLayerBlock.LAYERS)
                return if (layers > 1) state.setValue(SnowLayerBlock.LAYERS, layers - 1) else Blocks.AIR.defaultBlockState()
            }

            if (state.`is`(Blocks.SNOW_BLOCK)) {
                return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, SnowLayerBlock.MAX_HEIGHT - 1)
            }

            if (state.`is`(Blocks.ICE) || state.`is`(Blocks.FROSTED_ICE)) {
                return if (world.dimensionType().ultraWarm()) Blocks.AIR.defaultBlockState() else Blocks.WATER.defaultBlockState()
            }

            return null
        }
    }
}
