package com.mirabiliahexica.client

import com.mirabiliahexica.lens.LensVisibility
import com.mirabiliahexica.network.MirabiliaNetworking.Label
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f

/**
 * Labels produced by the worn imbued lens (`LensImbuement` on the server), overlaid on the screen where their
 * position or entity appears: constant size, drawn over everything. The server sends a fresh list whenever it
 * changes, and an empty one when the lens is taken off or a run fails. Labels whose place or creature the
 * wearer can't see are skipped frame by frame, unless the lens has Deepsight ([LensVisibility]).
 *
 * Two steps each frame: [project] runs inside the world render (where the camera matrices are current) and turns
 * each anchor into a screen point; [render] runs with the HUD and draws the text there.
 */
object LensLabels {
    private var labels: List<Label> = emptyList()
    private var throughWalls = false

    private data class OnScreen(val x: Float, val y: Float, val lines: List<Component>, val distance: Double)

    private var onScreen: List<OnScreen> = emptyList()

    /** Labels farther than this aren't drawn. */
    private const val MAX_DISTANCE = 128.0
    /** Entity labels sit this far above the entity's head. */
    private const val ABOVE_ENTITY = 0.5

    fun replace(newLabels: List<Label>, seesThroughWalls: Boolean) {
        labels = newLabels
        throughWalls = seesThroughWalls
    }

    fun clear() {
        labels = emptyList()
        onScreen = emptyList()
    }

    /** From the loader's world-render hook: [poseStack] holds the camera rotation for this frame. */
    fun project(poseStack: PoseStack, camera: Camera, partialTick: Float) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        val level = minecraft.level
        if (labels.isEmpty() || player == null || level == null) {
            onScreen = emptyList()
            return
        }

        val toClip = Matrix4f(RenderSystem.getProjectionMatrix()).mul(poseStack.last().pose())
        val window = minecraft.window
        val cam = camera.position
        onScreen = labels.mapNotNull { label ->
            val pos = when (val anchor = label.anchor) {
                is Label.Anchor.Fixed -> anchor.pos.takeIf { throughWalls || LensVisibility.canSee(player, it) }
                is Label.Anchor.Following -> level.getEntity(anchor.entityId)
                    ?.takeIf { throughWalls || LensVisibility.canSee(player, it) }
                    ?.let { it.getPosition(partialTick).add(0.0, it.bbHeight + ABOVE_ENTITY, 0.0) }
            } ?: return@mapNotNull null
            val distance = pos.distanceTo(cam)
            if (distance > MAX_DISTANCE) return@mapNotNull null
            screenPoint(toClip, pos.subtract(cam), window.guiScaledWidth, window.guiScaledHeight)
                ?.let { (x, y) -> OnScreen(x, y, label.lines, distance) }
        }
        // Draw far labels first so near ones end up on top
            .sortedByDescending { it.distance }
    }

    /** Projects a camera-relative point to GUI coordinates; null if it's behind the camera. */
    private fun screenPoint(toClip: Matrix4f, relative: Vec3, width: Int, height: Int): Pair<Float, Float>? {
        val clip = Vector4f(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat(), 1f).mul(toClip)
        if (clip.w <= 0.05f) return null
        val ndcX = clip.x / clip.w
        val ndcY = clip.y / clip.w
        return Pair((ndcX * 0.5f + 0.5f) * width, (0.5f - ndcY * 0.5f) * height)
    }

    /** From the HUD hook. */
    fun render(graphics: GuiGraphics) {
        val minecraft = Minecraft.getInstance()
        if (onScreen.isEmpty() || minecraft.options.hideGui) return
        val font = minecraft.font
        val background = (minecraft.options.getBackgroundOpacity(0.4f) * 255).toInt() shl 24
        for (label in onScreen) {
            if (label.lines.isEmpty()) continue
            // One box for all lines, centred on the point; each line centred in the box
            val width = label.lines.maxOf(font::width)
            val height = label.lines.size * font.lineHeight
            val left = (label.x - width / 2f).toInt()
            var top = (label.y - height / 2f).toInt()
            graphics.fill(left - 2, top - 2, left + width + 2, top + height + 1, background)
            for (line in label.lines) {
                graphics.drawString(font, line, (label.x - font.width(line) / 2f).toInt(), top, 0xFFFFFF, false)
                top += font.lineHeight
            }
        }
    }
}
