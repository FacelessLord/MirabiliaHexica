package com.mirabiliahexica.fabric

import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.fabric.interop.TrinketsInterop
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader

object FabricMirabilia : ModInitializer {
    override fun onInitialize() {
        Mirabilia.init()
        FabricFeatureCondition.init()
        if (FabricLoader.getInstance().isModLoaded("trinkets")) {
            TrinketsInterop.init()
        }
    }
}
