package com.mirabiliahexica.registry

import com.mirabiliahexica.worldgen.DreamPillarFeature
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.feature.Feature

object MirabiliaFeatures : MirabiliaRegistrar<Feature<*>>(Registries.FEATURE, { BuiltInRegistries.FEATURE }) {
    val DREAM_PILLAR = register("dream_pillar") { DreamPillarFeature() }
}
