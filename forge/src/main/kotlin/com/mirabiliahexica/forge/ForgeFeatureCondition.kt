package com.mirabiliahexica.forge

import com.google.gson.JsonObject
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.config.Features
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraftforge.common.crafting.CraftingHelper
import net.minecraftforge.common.crafting.conditions.ICondition
import net.minecraftforge.common.crafting.conditions.IConditionSerializer

/** `{"type": "mirabilia:feature", "feature": "<name>"}` in "conditions": loads only while it's on. */
class ForgeFeatureCondition(private val feature: String) : ICondition {
    override fun getID(): ResourceLocation = ID

    override fun test(context: ICondition.IContext) = Features.isOn(feature)

    object Serializer : IConditionSerializer<ForgeFeatureCondition> {
        override fun write(json: JsonObject, value: ForgeFeatureCondition) = json.addProperty("feature", value.feature)
        override fun read(json: JsonObject) = ForgeFeatureCondition(GsonHelper.getAsString(json, "feature"))
        override fun getID(): ResourceLocation = ID
    }

    companion object {
        val ID = Mirabilia.id("feature")

        fun init() {
            CraftingHelper.register(Serializer)
        }
    }
}
