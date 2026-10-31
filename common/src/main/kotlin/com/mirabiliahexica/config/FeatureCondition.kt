package com.mirabiliahexica.config

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import net.minecraft.util.GsonHelper
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType

/**
 * `{"condition": "mirabilia:feature", "feature": "<name>"}` in a loot table: passes while that [Features] name is on.
 * (Recipes and advancements use each loader's own load conditions with the same name.)
 */
class FeatureCondition(private val feature: String) : LootItemCondition {
    override fun getType(): LootItemConditionType = TYPE

    override fun test(context: LootContext) = Features.isOn(feature)

    object Serializer : net.minecraft.world.level.storage.loot.Serializer<FeatureCondition> {
        override fun serialize(json: JsonObject, value: FeatureCondition, context: JsonSerializationContext) {
            json.addProperty("feature", value.feature)
        }

        override fun deserialize(json: JsonObject, context: JsonDeserializationContext) =
            FeatureCondition(GsonHelper.getAsString(json, "feature"))
    }

    companion object {
        val TYPE = LootItemConditionType(Serializer)
    }
}
