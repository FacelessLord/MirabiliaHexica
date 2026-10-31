package com.mirabiliahexica.registry

import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.common.lib.hex.HexArithmetics
import com.mirabiliahexica.MirabiliaConfig
import com.mirabiliahexica.casting.arithmetic.StringArithmetic

/** Extensions to Hex's arithmetic patterns. Strings follow the `stringPatterns` config option, like the string patterns. */
object MirabiliaArithmetics : MirabiliaRegistrar<Arithmetic>(HexRegistries.ARITHMETIC, { HexArithmetics.REGISTRY }) {
    val STRING = if (MirabiliaConfig.stringPatterns) register("string") { StringArithmetic } else null
}
