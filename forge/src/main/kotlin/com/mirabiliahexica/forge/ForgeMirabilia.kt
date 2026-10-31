package com.mirabiliahexica.forge

import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.MirabiliaClient
import com.mirabiliahexica.client.LensLabels
import com.mirabiliahexica.forge.interop.CuriosInterop
import com.mirabiliahexica.forge.interop.CuriosRenderers
import dev.architectury.platform.forge.EventBuses
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(Mirabilia.MODID)
class ForgeMirabilia {
    init {
        MOD_BUS.apply {
            EventBuses.registerModEventBus(Mirabilia.MODID, this)
            addListener { _: FMLClientSetupEvent -> initClient() }
        }
        if (FMLEnvironment.dist.isClient) {
            ForgeDreamEffects.register()
            MirabiliaClient.initRenderers()
        }
        Mirabilia.init()
        ForgeFeatureCondition.init()
        if (ModList.get().isLoaded("curios")) CuriosInterop.init()
    }

    private fun initClient() {
        MirabiliaClient.init()
        FORGE_BUS.addListener { event: RenderLevelStageEvent ->
            if (event.stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                LensLabels.project(event.poseStack, event.camera, event.partialTick)
            }
        }
        if (ModList.get().isLoaded("curios")) CuriosRenderers.init()
    }
}
