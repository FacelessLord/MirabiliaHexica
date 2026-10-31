package com.mirabiliahexica.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import com.mirabiliahexica.casting.mishaps.MishapNotDreaming
import com.mirabiliahexica.dream.Dream
import net.minecraft.server.level.ServerPlayer
import com.mirabiliahexica.dream.DreamLore

/** Wake: nothing → nothing. The dreaming caster wakes at once. Free, and one of the few patterns that work there. */
object OpWake : SpellAction {
    override val argc = 0

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        DreamLore.require(env, this)
        val caster = env.castingEntity as? ServerPlayer
        if (caster == null || !Dream.isDreaming(caster)) throw MishapNotDreaming()
        return SpellAction.Result(Spell(caster), 0, listOf(ParticleSpray.burst(caster.position(), 1.0)))
    }

    private data class Spell(val caster: ServerPlayer) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            if (Dream.isDreaming(caster)) Dream.wake(caster, Dream.Waking.PATTERN)
        }
    }
}
