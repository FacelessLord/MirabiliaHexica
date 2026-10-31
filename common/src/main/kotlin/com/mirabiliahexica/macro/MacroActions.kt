package com.mirabiliahexica.macro

import at.petrak.hexcasting.api.casting.PatternShapeMatch
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getList
import at.petrak.hexcasting.api.casting.getPattern
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.common.casting.PatternRegistryManifest
import com.mirabiliahexica.registry.MirabiliaSpecialHandlers
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.DyeColor
import com.mirabiliahexica.config.Tuning

/** The player whose macros a cast works on; macros belong to people, so circles and the like can't. */
private fun owner(env: CastingEnvironment) = env.castingEntity as? ServerPlayer ?: throw MishapMacro("no_owner")

/**
 * Macro Binding: pattern, list → nothing. Binds the pattern, for the caster alone, to the hex in the list; drawn
 * again it runs that hex. Rebinding a macro replaces its hex. Refuses a pattern that already means something
 * (any registered pattern, a per-world great spell, a number, Bookkeeper's Gambit) and a new macro past the limit.
 */
object OpBindMacro : SpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val pattern = args.getPattern(0, argc)
        val hex = ListIota(args.getList(1, argc))
        val player = owner(env)
        val match = PatternRegistryManifest.matchPattern(pattern, env)
        val ours = match is PatternShapeMatch.Special && match.key == MirabiliaSpecialHandlers.MACRO.key
        if (match !is PatternShapeMatch.Nothing && !ours) throw MishapMacro("taken")
        if (!ours && Macros.of(player).size >= Macros.limit(player)) throw MishapMacro("full", Macros.limit(player))
        return SpellAction.Result(Spell(player, pattern, hex), COST, listOf())
    }

    private data class Spell(val player: ServerPlayer, val pattern: HexPattern, val hex: ListIota) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = Macros.bind(player, pattern, hex)
    }

    val COST get() = Tuning.Spells.macroBindCost
}

/** Macro Release: pattern → nothing. Forgets the caster's macro for that pattern, if there is one. */
object OpReleaseMacro : SpellAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val pattern = args.getPattern(0, argc)
        val player = owner(env)
        return SpellAction.Result(Spell(player, pattern), 0, listOf())
    }

    private data class Spell(val player: ServerPlayer, val pattern: HexPattern) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            Macros.release(player, pattern)
        }
    }
}

/** Macro Reflection: nothing → list. The patterns of the caster's macros, as they were bound. */
object OpMacroList : ConstMediaAction {
    override val argc = 0

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> =
        listOf(ListIota(Macros.of(owner(env)).values.map(::PatternIota)))
}

/** A macro pattern refused: [reason] is `no_owner`, `taken` or `full` (with the limit). Harmless. */
class MishapMacro(private val reason: String, private vararg val args: Any) : Mishap() {
    override fun accentColor(env: CastingEnvironment, errorCtx: Context): FrozenPigment = dyeColor(DyeColor.LIGHT_BLUE)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {}

    override fun errorMessage(env: CastingEnvironment, errorCtx: Context): Component = error("mirabilia.macro.$reason", *args)
}
