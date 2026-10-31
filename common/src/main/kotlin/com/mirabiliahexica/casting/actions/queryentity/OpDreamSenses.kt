package com.mirabiliahexica.casting.actions.queryentity

import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import com.mirabiliahexica.casting.mishaps.MishapNotDreaming
import com.mirabiliahexica.dream.Dream
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.dream.DreamLore

/**
 * A dreamer's sense of themselves. The dream's silence hides the caster from ordinary patterns (Mind's
 * Reflection, Compass Purification…), so these hand the willing patterns what they need directly. Free, and
 * only in the dream.
 */
abstract class OpDreamSense : ConstMediaAction {
    override val argc = 0

    protected abstract fun sense(dreamer: ServerPlayer): Vec3

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        DreamLore.require(env, this)
        val caster = env.castingEntity as? ServerPlayer
        if (caster == null || !Dream.isDreaming(caster)) throw MishapNotDreaming("gaze")
        return listOf(Vec3Iota(sense(caster)))
    }
}

/** Dreamer's Footing: nothing → vector. The block the dreamer stands in (its corner, as Hex gives blocks). */
object OpDreamFooting : OpDreamSense() {
    override fun sense(dreamer: ServerPlayer): Vec3 = Vec3.atLowerCornerOf(dreamer.blockPosition())
}

/** Dreamer's Bearing: nothing → vector. Exactly where the dreamer looks, one block long. */
object OpDreamBearing : OpDreamSense() {
    override fun sense(dreamer: ServerPlayer): Vec3 = dreamer.lookAngle
}
