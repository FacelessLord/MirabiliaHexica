package com.mirabiliahexica.registry

import com.mirabiliahexica.config.FeatureCondition
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType

object MirabiliaLootConditions : MirabiliaRegistrar<LootItemConditionType>(Registries.LOOT_CONDITION_TYPE, { BuiltInRegistries.LOOT_CONDITION_TYPE }) {
    /** `mirabilia:feature`: loot that only drops while a feature is on. */
    val FEATURE = register("feature") { FeatureCondition.TYPE }
}
