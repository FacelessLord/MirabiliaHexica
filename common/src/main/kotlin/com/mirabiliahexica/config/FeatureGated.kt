package com.mirabiliahexica.config

import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.OperationResult
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidPattern

/**
 * A pattern belonging to a [Features] switch. It stays registered either way, so clients and servers with different
 * settings always agree on what patterns exist, and foci written before a switch-off still hold; while its feature
 * is off it simply means nothing when cast.
 */
class FeatureGated(val feature: String, val action: Action, private val pattern: HexPattern) : Action {
    override fun operate(env: CastingEnvironment, image: CastingImage, continuation: SpellContinuation): OperationResult {
        if (!Features.isOn(feature)) throw MishapInvalidPattern(pattern)
        return action.operate(env, image, continuation)
    }
}
