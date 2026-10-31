package com.mirabiliahexica.client

import com.mirabiliahexica.lens.LensSight
import com.mirabiliahexica.lens.SunSign
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.world.level.Level
import org.joml.Vector3f
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * Draws [SunSign]'s caltrop over the sun, for a player wearing a lens: the lens dims the middle of the sun's face
 * behind it, and the caltrop glows there in amethyst, smaller than the sun. Spikes pointing towards the viewer are
 * brighter and broader than those pointing away, so the day's turn shows. Called right after vanilla draws the sky
 * (`MixinLevelRenderer`), so it sits in the sky behind the terrain, turned exactly as the sun is. Fades with the
 * rain as the sun does.
 */
object SunSignRenderer {
    /** Vanilla's sun sits 100 units out; its bright face is about 30 across. */
    private const val DISTANCE = 100f
    /** Half the side of the dimmed square behind the caltrop. */
    private val DISC get() = Tuning.Client.caltropBacking
    private val SPIKE get() = Tuning.Client.caltropSize

    /** Half the width of a spike at its root; it tapers to a point. */
    private const val ROOT = 1.1f

    fun render(poseStack: PoseStack, partialTick: Float) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val player = minecraft.player ?: return
        if (!Features.sunCaltrop || level.dimension() != Level.OVERWORLD || LensSight.lenses(player).isEmpty()) return
        val alpha = (1f - level.getRainLevel(partialTick)) * Tuning.Client.caltropBrightness
        if (alpha <= 0f) return

        poseStack.pushPose()
        // The same turns vanilla gives the sun
        poseStack.mulPose(Axis.YP.rotationDegrees(-90f))
        poseStack.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360f))
        val matrix = poseStack.last().pose()
        val builder = Tesselator.getInstance().builder

        RenderSystem.enableBlend()
        RenderSystem.disableCull()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)

        // The dimmed sun: a dark square over the middle of its face
        RenderSystem.defaultBlendFunc()
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR)

        builder.vertex(matrix, 1f * DISC, DISTANCE, -1f * DISC).color(0.06f, 0.02f, 0.10f, 0.8f * alpha).endVertex()
        builder.vertex(matrix, 1f * DISC, DISTANCE, 1f * DISC).color(0.06f, 0.02f, 0.10f, 0.8f * alpha).endVertex()
        builder.vertex(matrix, -1f * DISC, DISTANCE, 1f * DISC).color(0.06f, 0.02f, 0.10f, 0.8f * alpha).endVertex()
        builder.vertex(matrix, -1f * DISC, DISTANCE, -1f * DISC).color(0.06f, 0.02f, 0.10f, 0.8f * alpha).endVertex()

        BufferUploader.drawWithShader(builder.end())

        // The caltrop, glowing on it: tapered spikes, foreshortened and shaded by depth
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE)
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        val centre = Vector3f(0f, DISTANCE, 0f)
        val up = SunSign.up(level.getSunAngle(partialTick))
        for (spike in SunSign.spikes(SunSign.day(level.dayTime), up)) {
            // Its width lies across the view (the viewer looks along +y here)
            val side = Vector3f(spike).cross(0f, 1f, 0f)
                .let { if (it.lengthSquared() < 1e-4f) Vector3f(1f, 0f, 0f) else it.normalize() }
                .mul(ROOT)
            // The viewer looks along +y, so a spike with -y comes towards them: brighter and broader
            val near = (1f - spike.y) / 2f
            side.mul(0.6f + 0.6f * near)
            val glow = alpha * (0.35f + 0.65f * near)
            val tip = Vector3f(spike).mul(SPIKE).add(centre)
            val a = Vector3f(centre).sub(side)
            val b = Vector3f(centre).add(side)
            builder.vertex(matrix, a.x, a.y, a.z).color(0.80f, 0.62f, 1f, glow).endVertex()
            builder.vertex(matrix, b.x, b.y, b.z).color(0.80f, 0.62f, 1f, glow).endVertex()
            builder.vertex(matrix, tip.x, tip.y, tip.z).color(1f, 0.92f, 1f, glow).endVertex()
        }
        BufferUploader.drawWithShader(builder.end())

        RenderSystem.enableCull()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
        poseStack.popPose()
    }
}
