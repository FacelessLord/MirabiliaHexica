package com.mirabiliahexica.forge

import com.mirabiliahexica.client.DreamEffects
import com.mirabiliahexica.client.DreamSky
import com.mirabiliahexica.dream.Dream
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent
import org.joml.Matrix4f
import thedarkcolour.kotlinforforge.forge.MOD_BUS

/** Forge draws a dimension's custom sky through its effects object. Client only. */
class ForgeDreamEffects : DreamEffects() {
    override fun renderSky(
        level: ClientLevel, ticks: Int, partialTick: Float, poseStack: PoseStack, camera: Camera,
        projectionMatrix: Matrix4f, isFoggy: Boolean, setupFog: Runnable,
    ): Boolean {
        DreamSky.render(poseStack, projectionMatrix, partialTick, level)
        return true
    }

    companion object {
        /** Must be hooked up before client setup: the event fires early in loading. */
        fun register() {
            MOD_BUS.addListener { event: RegisterDimensionSpecialEffectsEvent ->
                event.register(Dream.LEVEL.location(), ForgeDreamEffects())
            }
        }
    }
}
