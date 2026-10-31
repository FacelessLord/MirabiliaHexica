package com.mirabiliahexica.casting.actions.spells

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.misc.MediaConstants
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import com.mirabiliahexica.config.Tuning

/** Shared area rules for Freeze and Thaw. */
object FrostArea {
    val MAX_RADIUS get() = Tuning.Spells.frostMaxRadius

    /** One dust, plus a quarter dust per square block of radius: 1.25 dust at radius 1, 17 dust at radius 8. */
    fun cost(radius: Double): Long = Tuning.Spells.frostBaseCost + (Tuning.Spells.frostCostPerSquare * radius * radius).toLong()

    /**
     * Every block whose centre is within [radius] of [center] that the caster may edit. Positions are checked
     * one by one against ambit too, so a sphere poking past the edge of ambit (or into a warded area) is clipped.
     */
    fun positions(env: CastingEnvironment, center: Vec3, radius: Double): List<BlockPos> {
        val origin = BlockPos.containing(center)
        val reach = ceil(radius).toInt()
        val radiusSq = radius * radius
        val result = mutableListOf<BlockPos>()
        for (pos in BlockPos.betweenClosed(origin.offset(-reach, -reach, -reach), origin.offset(reach, reach, reach))) {
            val blockCenter = Vec3.atCenterOf(pos)
            if (blockCenter.distanceToSqr(center) > radiusSq) continue
            // canEditBlockAt includes the ambit check (and any addon range hooks)
            if (!env.isVecInWorld(blockCenter) || !env.canEditBlockAt(pos)) continue
            result.add(pos.immutable())
        }
        return result
    }
}
