package com.mirabiliahexica.fabric.interop

import com.mirabiliahexica.registry.MirabiliaItems
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import dev.emi.trinkets.api.SlotReference
import dev.emi.trinkets.api.client.TrinketRenderer
import dev.emi.trinkets.api.client.TrinketRendererRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/**
 * Draws a lens over the wearer's eye when it sits in a Trinkets slot, with the same placement as Hex's own
 * Scrying Lens renderer (which Hex registers for the Scrying Lens only). Client only.
 */
object LensTrinketRenderer : TrinketRenderer {
    fun init() {
        TrinketRendererRegistry.registerRenderer(MirabiliaItems.GREATER_LENS.value, this)
    }

    @Suppress("UNCHECKED_CAST")
    override fun render(
        stack: ItemStack, slotReference: SlotReference, contextModel: EntityModel<out LivingEntity>,
        matrices: PoseStack, vertexConsumers: MultiBufferSource, light: Int, entity: LivingEntity,
        limbAngle: Float, limbDistance: Float, tickDelta: Float, animationProgress: Float, headYaw: Float, headPitch: Float,
    ) {
        if (contextModel !is PlayerModel<*> || entity !is AbstractClientPlayer) return
        val playerModel = contextModel as PlayerModel<AbstractClientPlayer>

        // Move and rotate with the head
        matrices.pushPose()
        // Trinkets declares these with different generics (fine in Java), so each gets its own cast
        TrinketRenderer.followBodyRotations(entity, contextModel as HumanoidModel<LivingEntity>)
        TrinketRenderer.translateToFace(matrices, playerModel, entity, headYaw, headPitch)

        // Then onto the face, sized like the Scrying Lens
        matrices.translate(0.0, 0.0, 0.3)
        matrices.mulPose(Axis.ZP.rotationDegrees(180.0f))
        matrices.scale(0.625f, 0.625f, 0.625f)

        val minecraft = Minecraft.getInstance()
        minecraft.itemRenderer.renderStatic(stack, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY,
            matrices, vertexConsumers, minecraft.level, 0)
        matrices.popPose()
    }
}
