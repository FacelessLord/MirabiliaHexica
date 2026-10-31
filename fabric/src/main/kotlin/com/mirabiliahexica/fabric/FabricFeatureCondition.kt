package com.mirabiliahexica.fabric

import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.config.Features
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions
import net.minecraft.util.GsonHelper

/** `{"condition": "mirabilia:feature", "feature": "<name>"}` in "fabric:load_conditions": loads only while it's on. */
object FabricFeatureCondition {
    fun init() {
        ResourceConditions.register(Mirabilia.id("feature")) { json -> Features.isOn(GsonHelper.getAsString(json, "feature")) }
    }
}
