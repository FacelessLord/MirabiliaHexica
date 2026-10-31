package com.mirabiliahexica.casting.actions.strings

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getIntBetween
import at.petrak.hexcasting.api.casting.getPositiveInt
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.pigment.FrozenPigment
import com.mirabiliahexica.casting.iota.StringIota
import com.mirabiliahexica.config.Tuning
import com.mirabiliahexica.strings.ChatMemory
import com.mirabiliahexica.strings.MindPalace
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.DyeColor

/** The caster, who owns the chat and the mind palace these patterns read; circles and the like have neither. */
private fun mind(env: CastingEnvironment) = env.castingEntity as? ServerPlayer ?: throw MishapNoMind()

/** Echo Purification: number → string. The caster's own chat message that many back (0: the latest), or Null. */
object OpChatEcho : ConstMediaAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val back = args.getPositiveInt(0, argc)
        return listOf(ChatMemory.message(mind(env), back)?.let(::StringIota) ?: NullIota())
    }
}

/** Memory's Purification: number → string. What the caster keeps in that room of their mind palace, or Null. */
object OpMemoryRead : ConstMediaAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val room = args.getIntBetween(0, 0, MindPalace.rooms - 1, argc)
        return listOf(MindPalace.read(mind(env), room)?.let(::StringIota) ?: NullIota())
    }
}

/** Memory's Gambit: string | null, number → nothing. Keeps the string in that room; Null empties it. */
object OpMemoryWrite : SpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val text = when (val iota = args[0]) {
            is StringIota -> iota.string
            is NullIota -> null
            else -> throw MishapInvalidIota.of(iota, 1, "mirabilia.string_or_null")
        }
        val room = args.getIntBetween(1, 0, MindPalace.rooms - 1, argc)
        val player = mind(env)
        return SpellAction.Result(Spell(player, room, text), Tuning.Strings.palaceWriteCost, listOf(ParticleSpray.cloud(player.eyePosition, 0.5)))
    }

    private data class Spell(val player: ServerPlayer, val room: Int, val text: String?) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = MindPalace.write(player, room, text)
    }
}

/** Palace Reflection: nothing → number. How many rooms of the caster's mind palace hold something. */
object OpPalaceSize : ConstMediaAction {
    override val argc = 0

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> =
        listOf(DoubleIota(MindPalace.size(mind(env)).toDouble()))
}

/** A chat or mind-palace pattern cast by something without a mind of its own (a circle...). Harmless. */
class MishapNoMind : Mishap() {
    override fun accentColor(env: CastingEnvironment, errorCtx: Context): FrozenPigment = dyeColor(DyeColor.LIGHT_BLUE)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {}

    override fun errorMessage(env: CastingEnvironment, errorCtx: Context): Component = error("mirabilia.no_mind")
}
