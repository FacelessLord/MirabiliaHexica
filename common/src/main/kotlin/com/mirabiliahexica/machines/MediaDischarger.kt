package com.mirabiliahexica.machines

import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.ambient.AmbientMedia
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LightningRodBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * The Media Discharger: with an upright lightning rod on top, every second (unless powered by redstone), when its
 * chunk holds at least [MIN_AMBIENT] of ambient media, it takes [SHARE] of it and lets it go from the rod's tip as
 * an arc into a random living thing within [RANGE] blocks, players included: [damage] grows with what it took. Arcs are only sparks and a crack; they set nothing alight and change no creature.
 */
object MediaDischarger {
    val SHARE get() = Tuning.Machines.dischargerShare
    val RANGE get() = Tuning.Machines.dischargerRange
    val MAX_DAMAGE get() = Tuning.Machines.dischargerMaxDamage
    /** Below this much ambient media in its chunk it rests: no draw, no arc. */
    val MIN_AMBIENT get() = Tuning.Machines.dischargerMinAmbient

    /** 1 + a point per so many dust taken, up to [MAX_DAMAGE]. */
    fun damage(taken: Long) = minOf(MAX_DAMAGE, 1f + (taken.toDouble() / (Tuning.Machines.dischargerDustPerDamage * MediaConstants.DUST_UNIT)).toFloat())

    fun second(level: ServerLevel, pos: BlockPos) {
        if (!Features.isOn("media_discharger") || level.hasNeighborSignal(pos) || !hasRod(level, pos)) return
        val here = AmbientMedia.get(level, pos)
        if (here < MIN_AMBIENT) return
        val taken = (here * SHARE).toLong()
        AmbientMedia.set(level, ChunkPos(pos), here - taken)
        // The tip of the rod on top
        val from = Vec3.atBottomCenterOf(pos.above()).add(0.0, 0.95, 0.0)
        val targets = level.getEntitiesOfClass(LivingEntity::class.java, AABB(pos).inflate(RANGE)) {
            it.isAlive && !it.isSpectator && it.distanceToSqr(from) <= RANGE * RANGE
        }
        val target = if (targets.isEmpty()) null else targets[level.random.nextInt(targets.size)]
        val to = target?.let { it.position().add(0.0, it.bbHeight / 2.0, 0.0) }
            ?: from.add(level.random.nextDouble() * 4 - 2, -0.6, level.random.nextDouble() * 4 - 2)
        arc(level, from, to)
        target?.hurt(level.damageSources().lightningBolt(), damage(taken))
        if (target != null && !target.isAlive) MirabiliaAdvancements.awardNear(level, pos, "high_voltage")
    }

    /** Whether an upright lightning rod stands on top: it only discharges through one. */
    fun hasRod(level: ServerLevel, pos: BlockPos): Boolean {
        val above = level.getBlockState(pos.above())
        return above.`is`(Blocks.LIGHTNING_ROD) && above.getValue(LightningRodBlock.FACING) == Direction.UP
    }

    private fun arc(level: ServerLevel, from: Vec3, to: Vec3) {
        val steps = (from.distanceTo(to) * 4).toInt().coerceAtLeast(2)
        for (i in 0..steps) {
            val p = from.lerp(to, i / steps.toDouble())
            val jitter = if (i == 0 || i == steps) 0.0 else 0.15
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, jitter, jitter, jitter, 0.0)
        }
        level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.4f, 1.8f)
    }
}
