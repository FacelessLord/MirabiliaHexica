package com.mirabiliahexica.casting.mishaps

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.pigment.FrozenPigment
import net.minecraft.network.chat.Component
import net.minecraft.world.item.DyeColor

/**
 * A dream pattern cast while awake. Harmless. [reason] picks the message: Wake has nothing to wake from; the
 * dream's other patterns have no dream to look into or shape.
 */
class MishapNotDreaming(private val reason: String = "awake") : Mishap() {
    override fun accentColor(env: CastingEnvironment, errorCtx: Context): FrozenPigment = dyeColor(DyeColor.PURPLE)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {}

    override fun errorMessage(env: CastingEnvironment, errorCtx: Context): Component = error("mirabilia.not_dreaming.$reason")
}
