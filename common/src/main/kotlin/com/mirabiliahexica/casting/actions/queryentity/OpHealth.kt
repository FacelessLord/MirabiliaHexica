package com.mirabiliahexica.casting.actions.queryentity

import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import net.minecraft.world.entity.LivingEntity

/**
 * Health Purification (`max = false`): entity → its current health in health points (2 per heart).
 * Max Health Purification (`max = true`): entity → its maximum health.
 * Both give null for things without health, like items and boats.
 */
class OpHealth(private val max: Boolean) : ConstMediaAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val entity = args.getEntity(0, argc)
        env.assertEntityInRange(entity)
        if (entity !is LivingEntity) return listOf(NullIota())
        return (if (max) entity.maxHealth else entity.health).toDouble().asActionResult
    }
}
