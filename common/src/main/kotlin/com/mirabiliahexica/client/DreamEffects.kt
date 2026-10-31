package com.mirabiliahexica.client

import net.minecraft.client.renderer.DimensionSpecialEffects
import net.minecraft.world.phys.Vec3

/**
 * How Tanako's World looks: no clouds, no sunrise, the sky drawn by [DreamSky], and thick fog everywhere (the
 * same fog the Nether uses, closing in a few dozen blocks away). Each loader hooks the sky renderer in itself.
 */
open class DreamEffects : DimensionSpecialEffects(Float.NaN, true, SkyType.NONE, false, false) {
    override fun getBrightnessDependentFogColor(fogColor: Vec3, brightness: Float): Vec3 = fogColor.scale(0.6 + 0.4 * brightness)

    override fun isFoggyAt(x: Int, y: Int) = true

    override fun getSunriseColor(timeOfDay: Float, partialTicks: Float): FloatArray? = null
}
