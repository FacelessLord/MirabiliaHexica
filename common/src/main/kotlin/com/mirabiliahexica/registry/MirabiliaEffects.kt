package com.mirabiliahexica.registry

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory

object MirabiliaEffects : MirabiliaRegistrar<MobEffect>(Registries.MOB_EFFECT, { BuiltInRegistries.MOB_EFFECT }) {
    /** Left on whoever dies in Tanako's World; see [com.mirabiliahexica.dream.DreamRules]. */
    val CAME_BACK_WRONG = register<MobEffect>("came_back_wrong") { SimpleEffect(MobEffectCategory.HARMFUL, 0x3b2a5a) }

    // MobEffect's constructor is protected
    private class SimpleEffect(category: MobEffectCategory, color: Int) : MobEffect(category, color)
}
