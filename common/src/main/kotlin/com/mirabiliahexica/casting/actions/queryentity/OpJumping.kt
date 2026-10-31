package com.mirabiliahexica.casting.actions.queryentity

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * Leaper's Purification: entity → whether it is in the rising part of a jump.
 *
 * The server never sees the jump key, so this is a heuristic: airborne and moving upward under its own
 * power. Rising for other reasons (swimming, climbing, creative flight, elytra, Levitation) doesn't count.
 */
object OpJumping : ConstMediaAction {
    override val argc = 1

    /** Blocks per tick. A vanilla jump starts at 0.42 and is still above this for about 5 ticks. */
    private const val MIN_UPWARD_SPEED = 0.05

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val entity = args.getEntity(0, argc)
        env.assertEntityInRange(entity)
        return isJumping(entity).asActionResult
    }

    fun isJumping(entity: Entity): Boolean {
        if (entity.onGround() || entity.isInWater || entity.isInLava || entity.isPassenger) return false
        if (entity is LivingEntity) {
            if (entity.onClimbable() || entity.isFallFlying || entity.hasEffect(MobEffects.LEVITATION)) return false
        }
        if (entity is Player && entity.abilities.flying) return false

        // Players' server-side delta movement is unreliable; Hex tracks their real motion separately
        val velocity = HexAPI.instance().getEntityVelocitySpecial(entity)
        return velocity.y > MIN_UPWARD_SPEED
    }
}
