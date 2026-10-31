package com.mirabiliahexica.client

import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.entity.GlassMan
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.HumanoidMobRenderer
import net.minecraft.resources.ResourceLocation

/**
 * The Glass Man: a tall, thin figure of clear glass. Standing still it all but vanishes (a faint refraction);
 * moving, it shows a little more. Drawn translucent, with a body built like a player's.
 */
class GlassManRenderer(context: EntityRendererProvider.Context) :
    HumanoidMobRenderer<GlassMan, GlassManRenderer.GlassModel>(context, GlassModel(context.bakeLayer(ModelLayers.PLAYER)), 0f) {

    override fun getTextureLocation(entity: GlassMan): ResourceLocation = TEXTURE

    override fun getRenderType(entity: GlassMan, bodyVisible: Boolean, translucent: Boolean, glowing: Boolean): RenderType =
        // Colour only, no depth, so water behind him still shows through
        GLASS

    override fun render(entity: GlassMan, yaw: Float, partialTick: Float, poseStack: PoseStack, buffer: MultiBufferSource, light: Int) {
        val moving = minOf(1f, entity.walkAnimation.speed(partialTick) * 2f)
        model.alpha = STILL_ALPHA + (MOVING_ALPHA - STILL_ALPHA) * moving
        super.render(entity, yaw, partialTick, poseStack, buffer, light)
    }

    override fun scale(entity: GlassMan, poseStack: PoseStack, partialTick: Float) {
        poseStack.scale(0.9f, 1.12f, 0.9f)
    }

    /** A humanoid body whose every part is drawn at [alpha]. */
    class GlassModel(root: ModelPart) : HumanoidModel<GlassMan>(root) {
        var alpha = 1f

        override fun renderToBuffer(
            poseStack: PoseStack, consumer: VertexConsumer, light: Int, overlay: Int, r: Float, g: Float, b: Float, a: Float,
        ) = super.renderToBuffer(poseStack, consumer, light, overlay, r, g, b, a * alpha)
    }

    private companion object {
        val TEXTURE = Mirabilia.id("textures/entity/glass_man.png")
        private val GLASS: RenderType by lazy { GlassRenderType.glass(TEXTURE) }
        const val STILL_ALPHA = 0.12f
        const val MOVING_ALPHA = 0.5f
    }
}
