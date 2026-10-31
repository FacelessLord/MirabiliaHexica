package com.mirabiliahexica.forge.interop

import com.mirabiliahexica.registry.MirabiliaItems
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import top.theillusivec4.curios.api.SlotContext
import top.theillusivec4.curios.api.client.CuriosRendererRegistry
import top.theillusivec4.curios.api.client.ICurioRenderer

/** Client only, and only when Curios is installed: never touch this class otherwise. */
object CuriosRenderers {
    fun init() {
        CuriosRendererRegistry.register(MirabiliaItems.GREATER_LENS.value) {
            LensCurioRenderer(HumanoidModel(Minecraft.getInstance().entityModels.bakeLayer(ModelLayers.PLAYER)))
        }
    }
}

/**
 * Draws a lens over the wearer's eye when it sits in a Curios slot, with the same placement as Hex's own
 * Scrying Lens renderer (`LensCurioRenderer` in Hex, itself adapted from Create's goggles). Hex registers its
 * renderer for the Scrying Lens only.
 */
class LensCurioRenderer(private val model: HumanoidModel<LivingEntity>) : ICurioRenderer {
    override fun <T : LivingEntity, M : EntityModel<T>> render(
        stack: ItemStack, slotContext: SlotContext, matrixStack: PoseStack, renderLayerParent: RenderLayerParent<T, M>,
        renderTypeBuffer: MultiBufferSource, light: Int, limbSwing: Float, limbSwingAmount: Float, partialTicks: Float,
        ageInTicks: Float, netHeadYaw: Float, headPitch: Float,
    ) {
        val entity = slotContext.entity()
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks)
        ICurioRenderer.followHeadRotations(entity, model.head)

        // Move and rotate with the head
        matrixStack.pushPose()
        matrixStack.translate(model.head.x / 16.0, model.head.y / 16.0, model.head.z / 16.0)
        matrixStack.mulPose(Axis.YP.rotation(model.head.yRot))
        matrixStack.mulPose(Axis.XP.rotation(model.head.xRot))

        // Then onto the face, sized like the Scrying Lens
        matrixStack.translate(0.0, -0.25, 0.0)
        matrixStack.mulPose(Axis.ZP.rotationDegrees(180.0f))
        matrixStack.scale(0.625f, 0.625f, 0.625f)

        val minecraft = Minecraft.getInstance()
        minecraft.itemRenderer.renderStatic(stack, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY,
            matrixStack, renderTypeBuffer, minecraft.level, 0)
        matrixStack.popPose()
    }
}
