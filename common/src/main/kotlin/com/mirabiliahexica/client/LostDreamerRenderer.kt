package com.mirabiliahexica.client

import com.mirabiliahexica.Mirabilia
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.ZombieVillagerModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.monster.ZombieVillager
import kotlin.math.sin

/**
 * A zombie villager drawn wrong: each one stretched tall and thin by its own amount, twitching now and then,
 * with violet cracks and eyes glowing through (an additive layer over vanilla's zombie villager skin), and a
 * body that came back with parts missing, doubled or in the wrong place ([DreamerMutation]). They keep their
 * profession's clothes but have lost its hat.
 *
 * The extra parts are the model's own head, body and arms drawn again with different offsets and angles, so
 * they move with the creature's walk and attacks.
 */
class LostDreamerRenderer(context: EntityRendererProvider.Context) : ZombieVillagerRenderer(context) {
    init {
        model = HatlessModel(context.bakeLayer(ModelLayers.ZOMBIE_VILLAGER))
        addLayer(MutationLayer(this))
        addLayer(GlowLayer(this))
    }

    override fun render(
        entity: ZombieVillager, entityYaw: Float, partialTicks: Float, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int,
    ) {
        // A dreamer whose head is all hands has no head to draw
        val headless = DreamerMutation.HEAD_OF_HANDS in DreamerMutation.of(entity.uuid) ||
            DreamerMutation.TWIN_HEADS in DreamerMutation.of(entity.uuid)
        model.head.visible = !headless
        try {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight)
        } finally {
            model.head.visible = true
        }
    }

    override fun scale(entity: ZombieVillager, poseStack: PoseStack, partialTick: Float) {
        super.scale(entity, poseStack, partialTick)
        val seed = entity.uuid.leastSignificantBits
        val tall = 1.05f + ((seed and 0xff) / 255f) * 0.3f
        val thin = 1f - (((seed shr 8) and 0xff) / 255f) * 0.18f
        poseStack.scale(thin, tall, thin)
        // A sharp jerk of the body for a few ticks every so often
        val ticks = entity.tickCount + partialTick
        if (((entity.tickCount + seed.toInt()) / 6) % 13 == 0) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(sin(ticks * 2.3f) * 9f))
        }
    }

    /** Draws the mutations' extra parts, each with the skin and the glowing cracks. */
    private class MutationLayer(parent: RenderLayerParent<ZombieVillager, ZombieVillagerModel<ZombieVillager>>) :
        RenderLayer<ZombieVillager, ZombieVillagerModel<ZombieVillager>>(parent) {
        override fun render(
            poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, entity: ZombieVillager,
            limbSwing: Float, limbSwingAmount: Float, partialTick: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float,
        ) {
            val mutations = DreamerMutation.of(entity.uuid)
            if (mutations.isEmpty()) return
            val m = parentModel
            val draw = Drawer(poseStack, buffer, packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0f),
                getTextureLocation(entity), glow(ageInTicks, entity.id))
            // Arms sway a little out of step with the real ones
            val sway = sin(ageInTicks * 0.1f + entity.id) * 0.15f

            for (mutation in mutations) when (mutation) {
                DreamerMutation.TWIN_HEADS -> for (side in SIDES) {
                    draw(m.head, x = side * 3.2f, y = 0.5f, zRot = side * 0.28f)
                }
                DreamerMutation.HEAD_OF_HANDS -> for ((dx, dz) in NECK_ARMS) {
                    // Pointing up out of the neck, splayed like fingers
                    draw(m.rightArm, absolute = true, x = dx, y = 1f, z = dz,
                        xRot = dz * 0.12f + sway, zRot = Mth.PI + dx * 0.16f)
                }
                DreamerMutation.BACK_FACE -> {
                    draw(m.head, yRot = Mth.PI, z = 0.5f)
                }
                DreamerMutation.TALL_STACK -> {
                    draw(m.body, y = -11f)
                    draw(m.head, y = -11f, zRot = sway * 0.6f)
                    draw(m.rightArm, y = -11f, zRot = 0.5f + sway)
                    draw(m.leftArm, y = -11f, zRot = -0.5f - sway)
                }
                DreamerMutation.EXTRA_ARMS -> {
                    val pairs = if (DreamerMutation.twoExtraPairs(entity.uuid)) 2 else 1
                    for (i in 1..pairs) {
                        draw(m.rightArm, y = 3.5f * i, xRot = 0.35f * i + sway, zRot = 0.3f * i)
                        draw(m.leftArm, y = 3.5f * i, xRot = 0.35f * i - sway, zRot = -0.3f * i)
                    }
                }
                DreamerMutation.CONJOINED -> {
                    // A second, smaller self grown out of the back, facing away
                    poseStack.pushPose()
                    poseStack.translate(0.0, 0.05, 0.28)
                    poseStack.mulPose(Axis.YP.rotation(Mth.PI))
                    poseStack.scale(0.8f, 0.8f, 0.8f)
                    draw(m.body)
                    draw(m.head, xRot = 0.3f)
                    draw(m.rightArm, xRot = -0.4f - sway)
                    draw(m.leftArm, xRot = -0.4f + sway)
                    poseStack.popPose()
                }
                DreamerMutation.ARM_LEGS -> {
                    draw(m.rightArm, y = 9.5f, x = -1.5f, xRot = sway, zRot = 0.15f)
                    draw(m.leftArm, y = 9.5f, x = 1.5f, xRot = -sway, zRot = -0.15f)
                }
            }
        }
    }

    /** Draws a model part again, moved and turned, with both the skin and the glow. */
    private class Drawer(
        val poseStack: PoseStack, val buffer: MultiBufferSource, val light: Int, val overlay: Int,
        val texture: ResourceLocation, val glow: Float,
    ) {
        /**
         * Offsets (in model pixels) and rotations add to the part's current pose, from its walk and attack
         * animation. With [absolute], the position is set rather than added and the rotation replaces the pose.
         */
        operator fun invoke(
            part: ModelPart, x: Float = 0f, y: Float = 0f, z: Float = 0f,
            xRot: Float = 0f, yRot: Float = 0f, zRot: Float = 0f, absolute: Boolean = false,
        ) {
            val saved = part.storePose()
            val wasVisible = part.visible
            if (absolute) {
                part.setPos(x, y, z)
                part.setRotation(xRot, yRot, zRot)
            } else {
                part.setPos(part.x + x, part.y + y, part.z + z)
                part.setRotation(part.xRot + xRot, part.yRot + yRot, part.zRot + zRot)
            }
            part.visible = true
            part.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay)
            part.render(poseStack, buffer.getBuffer(RenderType.eyes(GLOW)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1f)
            part.loadPose(saved)
            part.visible = wasVisible
        }
    }

    private class GlowLayer(parent: RenderLayerParent<ZombieVillager, ZombieVillagerModel<ZombieVillager>>) :
        RenderLayer<ZombieVillager, ZombieVillagerModel<ZombieVillager>>(parent) {
        override fun render(
            poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, entity: ZombieVillager,
            limbSwing: Float, limbSwingAmount: Float, partialTick: Float, ageInTicks: Float, netHeadYaw: Float, headPitch: Float,
        ) {
            val pulse = glow(ageInTicks, entity.id)
            val consumer = buffer.getBuffer(RenderType.eyes(GLOW))
            parentModel.renderToBuffer(poseStack, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse, 1f)
        }
    }

    /**
     * Never draws the villager's hat (the outer head layer, where professions put their hats), while the rest
     * of the profession's outfit stays. Vanilla turns it back on with [hatVisible], so that keeps it off.
     */
    private class HatlessModel(root: ModelPart) : ZombieVillagerModel<ZombieVillager>(root) {
        init {
            hat.visible = false
        }

        override fun hatVisible(visible: Boolean) {
            super.hatVisible(visible)
            hat.visible = false
        }
    }

    private companion object {
        val GLOW = Mirabilia.id("textures/entity/lost_dreamer_glow.png")
        const val FULL_BRIGHT = 0xF000F0
        val SIDES = listOf(-1f, 1f)
        /** Where the four neck arms root, in model pixels around the neck. */
        val NECK_ARMS = listOf(-2.5f to -1.2f, 2.5f to -1.2f, -1f to 1.4f, 1f to 1.4f)

        fun glow(ageInTicks: Float, id: Int) = 0.55f + 0.45f * sin(ageInTicks * 0.12f + id)
    }
}
