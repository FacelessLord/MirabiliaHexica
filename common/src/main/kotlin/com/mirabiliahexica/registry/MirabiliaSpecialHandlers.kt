package com.mirabiliahexica.registry

import at.petrak.hexcasting.api.casting.castables.SpecialHandler
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.mirabiliahexica.macro.Macros

/** Patterns recognised by shape rather than a fixed signature: the players' own macros. */
object MirabiliaSpecialHandlers : MirabiliaRegistrar<SpecialHandler.Factory<*>>(
    HexRegistries.SPECIAL_HANDLER,
    { IXplatAbstractions.INSTANCE.specialHandlerRegistry },
) {
    val MACRO = register("macro") { Macros.Factory() }
}
