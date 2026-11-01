package com.mirabiliahexica.entity

import com.mirabiliahexica.dream.DreamTicker
import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.DreamVisits
import com.mirabiliahexica.dream.GlassManSpawner
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * The Glass Man: something of the dream that follows one dreamer ([owner]) and will not stop. It cannot be killed
 * (only `/kill` removes it), it never loses track of its dreamer, and when it can't find a way to them it steps
 * through space instead, with a visible, audible cue, never right beside them and not more than once every
 * [TELEPORT_COOLDOWN] ticks. It walks a little faster than a dreamer walks, slower than they sprint.
 *
 * Its touch ([touch]) doesn't wound so much as take: media from the dreamer's pockets, warmth, a little health;
 * and if they are already low, it "wears them like a mask" and throws them out of the dream, come back wrong.
 * It holds a dreamer in the dream while near ([com.mirabiliahexica.dream.DreamTicker]), and it never leaves it:
 * when its dreamer is gone, so is it ([com.mirabiliahexica.dream.GlassManSpawner]).
 */
class GlassMan(type: EntityType<out GlassMan>, level: Level) : Monster(type, level) {
    var owner: UUID? = null

    private var teleportCooldown = 0
    private var stuckTicks = 0
    private var lastDistance = Double.MAX_VALUE

    init {
        setPersistenceRequired()
        xpReward = 0
    }

    override fun registerGoals() {
        goalSelector.addGoal(1, object : MeleeAttackGoal(this, 1.0, true) {
            override fun getAttackInterval() = adjustedTickDelay(TOUCH_INTERVAL)
        })
    }

    override fun isInvulnerableTo(source: DamageSource) =
        !source.`is`(DamageTypes.GENERIC_KILL) && !source.`is`(DamageTypes.FELL_OUT_OF_WORLD)

    override fun isPushable() = false

    override fun canChangeDimensions() = false

    override fun removeWhenFarAway(distance: Double) = false

    override fun getAmbientSound(): SoundEvent = SoundEvents.AMETHYST_BLOCK_RESONATE

    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.AMETHYST_BLOCK_HIT

    override fun getDeathSound(): SoundEvent = SoundEvents.GLASS_BREAK

    override fun customServerAiStep() {
        super.customServerAiStep()
        val level = level() as ServerLevel
        if (!Features.isOn("glass_man")) {
            discard()
            return
        }
        // One with nobody to follow (from a spawn egg) stands inert, unless it can take a dreamer
        if (owner == null && !adopt(level)) {
            target = null
            navigation.stop()
            return
        }
        val dreamer = owner?.let { level.getPlayerByUUID(it) } as? ServerPlayer
        if (dreamer == null || !dreamer.isAlive || !Dream.isDreaming(dreamer) || dreamer.level() != level ||
            !GlassManSpawner.claim(this, dreamer.uuid)
        ) {
            discard()
            return
        }
        target = dreamer
        if (teleportCooldown > 0) teleportCooldown--
        // Stuck: far off and not getting closer, so it steps through space instead
        val distance = distanceTo(dreamer).toDouble()
        stuckTicks = if (distance > 6 && (navigation.isDone || distance > lastDistance - 0.01)) stuckTicks + 1 else 0
        lastDistance = distance
        if (stuckTicks > STUCK_TICKS && teleportCooldown == 0) stepTowards(dreamer)
    }

    /** In the dream, takes the nearest dreamer within [ADOPT_RANGE] who has no Glass Man yet. */
    private fun adopt(level: ServerLevel): Boolean {
        if (level.dimension() != Dream.LEVEL) return false
        val dreamer = level.players()
            .filter { it.isAlive && !it.isSpectator && distanceTo(it) <= ADOPT_RANGE && GlassManSpawner.find(it) == null }
            .minByOrNull { distanceTo(it) } ?: return false
        owner = dreamer.uuid
        return true
    }

    override fun doHurtTarget(target: Entity): Boolean {
        if (target !is ServerPlayer || target.uuid != owner) return false
        swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        touch(target)
        return true
    }

    /** Takes from [dreamer]: media (Phials first), warmth, a little health; or, if they're low, them. */
    fun touch(dreamer: ServerPlayer) {
        drainMedia(dreamer, TOUCH_MEDIA)
        DreamVisits.get(dreamer.server)[dreamer.uuid]?.let { it.cold += TOUCH_COLD }
        level().playSound(null, dreamer.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1f, 0.6f)
        if (dreamer.health <= MASK_HEALTH) {
            Dream.wake(dreamer, Dream.Waking.MASK)
            Dream.cameBackWrong(dreamer)
        } else {
            dreamer.hurt(damageSources().mobAttack(this), TOUCH_DAMAGE)
        }
    }

    /** Lands [TELEPORT_MIN]–[TELEPORT_MAX] blocks from [dreamer], on open ground, with a cue at both ends. */
    private fun stepTowards(dreamer: ServerPlayer) {
        val level = level() as ServerLevel
        repeat(12) {
            val angle = random.nextFloat() * Mth.TWO_PI
            val distance = TELEPORT_MIN + random.nextDouble() * (TELEPORT_MAX - TELEPORT_MIN)
            val tx = Mth.floor(dreamer.x + cos(angle) * distance)
            val tz = Mth.floor(dreamer.z + sin(angle) * distance)
            if (!level.hasChunk(tx shr 4, tz shr 4)) return@repeat
            val ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz)
            if (!level.getBlockState(BlockPos(tx, ty, tz)).isAir || !level.getBlockState(BlockPos(tx, ty + 1, tz)).isAir) return@repeat
            shimmer(level, x, y, z)
            moveTo(tx + 0.5, ty.toDouble(), tz + 0.5, yRot, xRot)
            shimmer(level, x, y, z)
            level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5f, 0.5f)
            teleportCooldown = TELEPORT_COOLDOWN
            stuckTicks = 0
            navigation.stop()
            return
        }
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        owner?.let { tag.putUUID("owner", it) }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        owner = if (tag.hasUUID("owner")) tag.getUUID("owner") else null
    }

    companion object {
        val ADOPT_RANGE get() = Tuning.GlassMan.adoptRange
        val TOUCH_INTERVAL get() = Tuning.GlassMan.touchInterval
        val TOUCH_MEDIA get() = Tuning.GlassMan.touchMedia
        /** Thirty seconds of unprotected cold. */
        /** The touch's ticks of cold, in the dream's quarter-tick units. */
        val TOUCH_COLD get() = Tuning.GlassMan.touchCold * DreamTicker.COLD_PER_TICK
        val TOUCH_DAMAGE get() = Tuning.GlassMan.touchDamage
        /** Three hearts: below this a touch takes the dreamer out of the dream instead. */
        val MASK_HEALTH get() = Tuning.GlassMan.maskHealth
        val STUCK_TICKS get() = Tuning.GlassMan.stuckTicks
        val TELEPORT_COOLDOWN get() = Tuning.GlassMan.teleportCooldown
        val TELEPORT_MIN get() = Tuning.GlassMan.teleportMin
        val TELEPORT_MAX get() = Tuning.GlassMan.teleportMax

        fun createAttributes(): AttributeSupplier.Builder = Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 100.0)
            // A walking player is about 0.1 × 4.3; this is a little faster than walking, slower than sprinting
            .add(Attributes.MOVEMENT_SPEED, 0.27)
            .add(Attributes.FOLLOW_RANGE, 128.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 0.0)

        fun shimmer(level: ServerLevel, x: Double, y: Double, z: Double) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 1, z, 24, 0.3, 0.8, 0.3, 0.02)
        }

        /** Takes up to [amount] media from [player]'s inventory, Phials first. */
        fun drainMedia(player: ServerPlayer, amount: Long) {
            val stacks = (player.inventory.items + player.inventory.offhand).filter { !it.isEmpty }
                .sortedBy { if (it.`is`(HexItems.BATTERY)) 0 else 1 }
            var remaining = amount
            for (stack in stacks) {
                if (remaining <= 0) break
                val holder = HexAPI.instance().findMediaHolder(stack) ?: continue
                if (!holder.canProvide()) continue
                remaining -= holder.withdrawMedia(remaining, false)
            }
        }
    }
}
