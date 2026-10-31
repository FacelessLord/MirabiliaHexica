package com.mirabiliahexica.client

import at.petrak.hexcasting.api.misc.DiscoveryHandlers
import com.mirabiliahexica.lens.LensSight
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.MirabiliaConfig

/**
 * Imbued glyphs ([ImbuedGlyphs]) on a player: a band round each imbued armor piece, a strip over the eyes for an
 * imbued lens (on the head or in a face slot), and a ring round an imbued tool in hand. Model units are blocks, y
 * down; the front of the body faces -z. Sizes are the armor's own (each piece sits a pixel or so out from the body).
 */
class ImbuedGlyphLayer(parent: RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) :
    RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(parent) {

    override fun render(
        pose: PoseStack, buffers: MultiBufferSource, light: Int, player: AbstractClientPlayer,
        limbSwing: Float, limbSwingAmount: Float, partialTick: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float,
    ) {
        if (player.isInvisible) return
        val model = parentModel

        // Head: a lens worn there writes over the eyes; a helmet round the brow
        val head = player.getItemBySlot(EquipmentSlot.HEAD)
        ImbuedGlyphs.ink(head, player, partialTick)?.let { ink ->
            if (LensSight.isLens(head)) eyes(pose, buffers, model.head, ink, -4.6f)
            else band(pose, buffers, model.head, ink, y = -6.2f, halfX = 5.15f, halfZ = 5.15f, height = 2.6f)
        }
        // Lenses in bauble slots (Curios, Trinkets)
        for (stack in DiscoveryHandlers.collectExtraEquipments(player)) {
            if (!LensSight.isLens(stack)) continue
            ImbuedGlyphs.ink(stack, player, partialTick)?.let { eyes(pose, buffers, model.head, it, -4.7f) }
        }
        ImbuedGlyphs.ink(player.getItemBySlot(EquipmentSlot.CHEST), player, partialTick)?.let {
            band(pose, buffers, model.body, it, y = 5f, halfX = 5.15f, halfZ = 3.15f, height = 6f)
        }
        // Each leg wears the whole band
        ImbuedGlyphs.ink(player.getItemBySlot(EquipmentSlot.LEGS), player, partialTick)?.let { ink ->
            for (leg in listOf(model.rightLeg, model.leftLeg)) band(pose, buffers, leg, ink, y = 4f, halfX = 2.65f, halfZ = 2.65f, height = 4.5f)
        }
        ImbuedGlyphs.ink(player.getItemBySlot(EquipmentSlot.FEET), player, partialTick)?.let { ink ->
            for (leg in listOf(model.rightLeg, model.leftLeg)) band(pose, buffers, leg, ink, y = 9.6f, halfX = 3.15f, halfZ = 3.15f, height = 2.4f)
        }
        // Held tools, placed exactly as vanilla places the item in hand
        for (hand in InteractionHand.values()) {
            val stack = player.getItemInHand(hand)
            if (LensSight.isLens(stack)) continue
            val ink = ImbuedGlyphs.ink(stack, player, partialTick) ?: continue
            val arm = if (hand == InteractionHand.MAIN_HAND) player.mainArm else player.mainArm.opposite
            val left = arm == HumanoidArm.LEFT
            pose.pushPose()
            model.translateToHand(arm, pose)
            pose.mulPose(Axis.XP.rotationDegrees(-90f))
            pose.mulPose(Axis.YP.rotationDegrees(180f))
            pose.translate((if (left) -1f else 1f) / 16f, 0.125f, -0.625f)
            val context = if (left) ItemDisplayContext.THIRD_PERSON_LEFT_HAND else ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
            ImbuedGlyphs.drawHeld(pose, buffers, ink, stack, player, context, left)
            pose.popPose()
        }
    }

    private fun band(pose: PoseStack, buffers: MultiBufferSource, part: ModelPart, ink: ImbuedGlyphs.Ink, y: Float, halfX: Float, halfZ: Float, height: Float) {
        pose.pushPose()
        part.translateAndRotate(pose)
        pose.translate(0f, px(y), 0f)
        if (MirabiliaConfig.glyphStyle == MirabiliaConfig.GlyphStyle.HOOPS) {
            // The held-tool ring as a hoop round the piece, just clear of its corners
            val radius = Mth.sqrt(halfX * halfX + halfZ * halfZ) + 0.8f
            ImbuedGlyphs.drawHoop(pose, buffers, ink, px(radius), px(height * 0.8f), px(0.3f))
        } else {
            ImbuedGlyphs.drawBand(pose, buffers, ink, px(halfX), px(halfZ), px(height), px(0.22f))
        }
        pose.popPose()
    }

    private fun eyes(pose: PoseStack, buffers: MultiBufferSource, head: ModelPart, ink: ImbuedGlyphs.Ink, z: Float) {
        pose.pushPose()
        head.translateAndRotate(pose)
        pose.translate(0f, px(-4.5f), 0f)
        ImbuedGlyphs.drawStrip(pose, buffers, ink, px(7f), px(2f), px(z), px(0.16f))
        pose.popPose()
    }

    private fun px(pixels: Float) = pixels / 16f

    companion object {
        /** For your own first-person hand (`MixinItemInHandRenderer`), where the item is about to be drawn. */
        fun renderFirstPerson(pose: PoseStack, buffers: MultiBufferSource, player: AbstractClientPlayer, stack: ItemStack, hand: InteractionHand, partialTick: Float) {
            if (LensSight.isLens(stack)) return
            val ink = ImbuedGlyphs.ink(stack, player, partialTick) ?: return
            val arm = if (hand == InteractionHand.MAIN_HAND) player.mainArm else player.mainArm.opposite
            val left = arm == HumanoidArm.LEFT
            val context = if (left) ItemDisplayContext.FIRST_PERSON_LEFT_HAND else ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            ImbuedGlyphs.drawHeld(pose, buffers, ink, stack, player, context, left)
        }
    }
}
