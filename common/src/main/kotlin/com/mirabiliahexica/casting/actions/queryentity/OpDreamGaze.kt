package com.mirabiliahexica.casting.actions.queryentity

import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import com.mirabiliahexica.casting.actions.spells.OpWill
import com.mirabiliahexica.casting.mishaps.MishapNotDreaming
import com.mirabiliahexica.dream.Dream
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import com.mirabiliahexica.dream.DreamLore

/**
 * Dreamer's Gaze: nothing → vector. The block the dreaming caster is looking at, within the willing patterns'
 * reach, or null. The dream's silence hides even the caster from ordinary patterns (Mind's Reflection, Archer's
 * Distillation…), so this is how a dreamer finds a place to will. Like Archer's Distillation, it gives the
 * block's position, not the hit point.
 */
object OpDreamGaze : ConstMediaAction {
    override val argc = 0

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        DreamLore.require(env, this)
        val caster = env.castingEntity as? ServerPlayer
        if (caster == null || !Dream.isDreaming(caster)) throw MishapNotDreaming("gaze")
        val hit = caster.pick(OpWill.REACH, 0f, false)
        return if (hit.type == HitResult.Type.BLOCK) (hit as BlockHitResult).blockPos.asActionResult else listOf(NullIota())
    }
}
