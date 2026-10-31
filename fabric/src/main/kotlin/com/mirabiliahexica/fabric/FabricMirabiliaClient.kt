package com.mirabiliahexica.fabric

import com.mirabiliahexica.MirabiliaClient
import com.mirabiliahexica.client.DreamEffects
import com.mirabiliahexica.client.DreamSky
import com.mirabiliahexica.client.LensLabels
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.fabric.interop.LensTrinketRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.loader.api.FabricLoader

object FabricMirabiliaClient : ClientModInitializer {
    override fun onInitializeClient() {
        MirabiliaClient.initRenderers()
        MirabiliaClient.init()
        WorldRenderEvents.LAST.register { context ->
            LensLabels.project(context.matrixStack(), context.camera(), context.tickDelta())
        }
        DimensionRenderingRegistry.registerDimensionEffects(Dream.LEVEL.location(), DreamEffects())
        DimensionRenderingRegistry.registerSkyRenderer(Dream.LEVEL) { context ->
            DreamSky.render(context.matrixStack(), context.projectionMatrix(), context.tickDelta(), context.world())
        }
        if (FabricLoader.getInstance().isModLoaded("trinkets")) {
            LensTrinketRenderer.init()
        }
    }
}
