package com.mirabiliahexica.casting.actions.math

import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getVec3
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sign

/**
 * Axis Purification: vector → vector. The axis the vector mostly points along, one block long, keeping its sign:
 * (0.3, −0.2, −0.9) becomes (0, 0, −1). A tie goes to x, then z, before y, so a diagonal gaze along the ground
 * stays on the ground. The zero vector stays zero. Plain arithmetic, so it works anywhere, the dream included.
 */
object OpSnapToAxis : ConstMediaAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val v = args.getVec3(0, argc)
        val ax = abs(v.x)
        val ay = abs(v.y)
        val az = abs(v.z)
        val snapped = when {
            ax == 0.0 && ay == 0.0 && az == 0.0 -> Vec3.ZERO
            ax >= az && ax >= ay -> Vec3(sign(v.x), 0.0, 0.0)
            az >= ay -> Vec3(0.0, 0.0, sign(v.z))
            else -> Vec3(0.0, sign(v.y), 0.0)
        }
        return listOf(Vec3Iota(snapped))
    }
}
