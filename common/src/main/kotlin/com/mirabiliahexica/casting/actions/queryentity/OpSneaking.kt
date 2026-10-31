package com.mirabiliahexica.casting.actions.queryentity

import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose

/** Skulker's Purification: entity → whether it is sneaking (holding sneak, or in the crouching pose). */
object OpSneaking : ConstMediaAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val entity = args.getEntity(0, argc)
        env.assertEntityInRange(entity)
        return isSneaking(entity).asActionResult
    }

    // isShiftKeyDown also covers players sneaking while flying, where the pose doesn't change
    fun isSneaking(entity: Entity) = entity.isShiftKeyDown || entity.pose == Pose.CROUCHING
}
