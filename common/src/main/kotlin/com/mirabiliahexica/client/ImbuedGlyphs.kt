package com.mirabiliahexica.client

import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.pigment.ColorProvider
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.mirabiliahexica.MirabiliaConfig
import com.mirabiliahexica.imbue.ImbuedPatterns
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.lens.LensImbuement
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.WeakHashMap
import kotlin.math.ceil
import com.mirabiliahexica.config.Tuning

/**
 * Draws an imbued item's whole hex ([ImbuedPatterns]) as faintly glowing glyphs in its wearer's pigment: as a band
 * round worn armor, a strip over the eyes for a lens ([ImbuedGlyphLayer]), and a slowly turning ring round a held
 * tool. Bright while the item has media, a dim outline once it's spent.
 */
object ImbuedGlyphs {
    private val LIT get() = Tuning.Client.glyphBrightness
    private val SPENT get() = Tuning.Client.glyphSpent
    private const val MAX_ROWS = 4

    /** Each pattern's points, centred on the origin and scaled to fit a unit square; cached per hex. */
    private val shapes = WeakHashMap<CompoundTag, List<List<Vec2>>>()

    /** What [stack]'s glyphs are drawn with. */
    class Ink(val glyphs: List<List<Vec2>>, val colours: ColorProvider, val alpha: Float, val time: Float)

    /** The ink for [stack] on [player], or null if it has no glyphs to show. */
    fun ink(stack: ItemStack, player: Player, partialTick: Float): Ink? {
        if (!MirabiliaConfig.imbuedGlyphs || !Imbuement.isImbued(stack)) return null
        val hex = stack.tag?.getCompound(LensImbuement.TAG_HEX) ?: return null
        val glyphs = shapes.getOrPut(hex) { ImbuedPatterns.of(stack).map(::normalised) }
        if (glyphs.isEmpty()) return null
        val colours = IXplatAbstractions.INSTANCE.getPigment(player).colorProvider
        return Ink(glyphs, colours, if (Imbuement.media(stack) > 0) LIT else SPENT, player.tickCount + partialTick)
    }

    private fun normalised(pattern: HexPattern): List<Vec2> {
        val points = pattern.toLines(1f, Vec2.ZERO)
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val size = maxOf(maxX - minX, maxY - minY, 1e-3f)
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        return points.map { Vec2((it.x - cx) / size, (it.y - cy) / size) }
    }

    /** How many rows of how big glyphs fit [count] glyphs best in a strip [length] long and [height] high. */
    private fun layout(count: Int, length: Float, height: Float): Pair<Int, Float> =
        (1..MAX_ROWS).map { rows -> rows to minOf(height / rows, length / ceil(count / rows.toFloat())) }.maxBy { it.second }

    /** The glyphs in rows across a [width] × [height] strip centred on the origin of [pose]'s X–Y plane, at depth [z]. */
    fun drawStrip(pose: PoseStack, buffers: MultiBufferSource, ink: Ink, width: Float, height: Float, z: Float, line: Float) {
        val buffer = buffers.getBuffer(RenderType.lightning())
        val matrix = pose.last().pose()
        val (rows, cell) = layout(ink.glyphs.size, width, height)
        val perRow = ceil(ink.glyphs.size / rows.toFloat()).toInt()
        ink.glyphs.forEachIndexed { i, points ->
            val row = i / perRow
            val inRow = minOf(perRow, ink.glyphs.size - row * perRow)
            val x = (i % perRow - (inRow - 1) / 2f) * cell
            val y = (row - (rows - 1) / 2f) * cell
            glyph(buffer, matrix, ink, points, x, y, cell * 0.85f, z, line)
        }
    }

    /**
     * The glyphs as a band round a box (a body part): [halfX] by [halfZ] across, centred on the origin of [pose],
     * [height] high, starting at the front (-z) and going round. Each glyph lies flat on its face.
     */
    fun drawBand(pose: PoseStack, buffers: MultiBufferSource, ink: Ink, halfX: Float, halfZ: Float, height: Float, line: Float) {
        val buffer = buffers.getBuffer(RenderType.lightning())
        // Faces in order round the box: front, right, back, left; each turned so its local x runs along the band
        val lengths = floatArrayOf(2 * halfX, 2 * halfZ, 2 * halfX, 2 * halfZ)
        val depths = floatArrayOf(halfZ, halfX, halfZ, halfX)
        val turns = floatArrayOf(0f, -90f, 180f, 90f)
        val (rows, cell) = layout(ink.glyphs.size, lengths.sum(), height)
        val perRow = ceil(ink.glyphs.size / rows.toFloat()).toInt()
        val step = lengths.sum() / perRow
        ink.glyphs.forEachIndexed { i, points ->
            val row = i / perRow
            var along = (i % perRow + 0.5f) * step
            var face = 0
            while (face < 3 && along > lengths[face]) along -= lengths[face++]
            // Kept whole on its face rather than folded round a corner
            val half = cell * 0.425f
            val u = (along - lengths[face] / 2).coerceIn(-lengths[face] / 2 + half, lengths[face] / 2 - half)
            pose.pushPose()
            pose.mulPose(Axis.YP.rotationDegrees(turns[face]))
            glyph(buffer, pose.last().pose(), ink, points, u, (row - (rows - 1) / 2f) * cell, cell * 0.85f, -depths[face], line)
            pose.popPose()
        }
    }

    /**
     * A ring of the glyphs round a held item, square to its length, turning slowly. [pose] is where the item is about
     * to be drawn for [context]; this applies the item model's own display transform first, so the ring follows the
     * item exactly.
     */
    fun drawHeld(pose: PoseStack, buffers: MultiBufferSource, ink: Ink, stack: ItemStack, holder: LivingEntity, context: ItemDisplayContext, leftHand: Boolean) {
        val minecraft = Minecraft.getInstance()
        val model = minecraft.itemRenderer.getModel(stack, holder.level(), holder, 0)
        pose.pushPose()
        model.transforms.getTransform(context).apply(leftHand, pose)
        // Item space: the item fills the unit cube. A tool runs corner to corner across the X–Y plane; anything else
        // (armor carried in hand) stands upright in it
        pose.translate(-0.5f, -0.5f, -0.5f)
        if (Imbuement.canImbueArmor(stack)) {
            pose.translate(0.5f, 0.5f, 0.5f)
            pose.mulPose(Axis.ZP.rotationDegrees(90f))   // local x now runs up the item
        } else {
            pose.translate(0.55f, 0.55f, 0.5f)
            pose.mulPose(Axis.ZP.rotationDegrees(45f))   // local x now runs up the blade
        }
        pose.mulPose(Axis.YP.rotationDegrees(90f))   // the ring's plane is square to it
        ring(pose, buffers, ink, RING_RADIUS, RING_GLYPH, RING_LINE)
        pose.popPose()
    }

    /**
     * A hoop of the glyphs round a body part, level and turning slowly: the held-tool ring tried on worn armor.
     * [pose] is at the hoop's centre.
     */
    fun drawHoop(pose: PoseStack, buffers: MultiBufferSource, ink: Ink, radius: Float, glyph: Float, line: Float) {
        pose.pushPose()
        pose.mulPose(Axis.XP.rotationDegrees(90f))   // the ring's plane is level
        ring(pose, buffers, ink, radius, glyph, line)
        pose.popPose()
    }

    /** The turning ring in [pose]'s X–Y plane, the glyphs standing round it facing outwards. */
    private fun ring(pose: PoseStack, buffers: MultiBufferSource, ink: Ink, radius: Float, glyphSize: Float, line: Float) {
        pose.pushPose()
        pose.mulPose(Axis.ZP.rotationDegrees(ink.time * 1.5f))
        val buffer = buffers.getBuffer(RenderType.lightning())
        val matrix = pose.last().pose()
        val sides = 40
        for (i in 0 until sides) {
            val a0 = i * Mth.TWO_PI / sides
            val a1 = (i + 1) * Mth.TWO_PI / sides
            segment(buffer, matrix, ink, Mth.cos(a0) * radius, Mth.sin(a0) * radius, Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0f, line * 0.6f)
        }
        val size = minOf(glyphSize, Mth.TWO_PI * radius / ink.glyphs.size * 0.85f)
        ink.glyphs.forEachIndexed { i, points ->
            pose.pushPose()
            pose.mulPose(Axis.ZP.rotation(i * Mth.TWO_PI / ink.glyphs.size))
            pose.translate(0f, -radius, 0f)
            pose.mulPose(Axis.XP.rotationDegrees(90f))
            glyph(buffer, pose.last().pose(), ink, points, 0f, 0f, size, 0f, line)
            pose.popPose()
        }
        pose.popPose()
    }

    private const val RING_RADIUS = 0.3f
    private const val RING_GLYPH = 0.16f
    private const val RING_LINE = 0.018f

    private fun glyph(buffer: VertexConsumer, matrix: Matrix4f, ink: Ink, points: List<Vec2>, ox: Float, oy: Float, scale: Float, z: Float, line: Float) {
        for (k in 0 until points.size - 1) {
            val a = points[k]; val b = points[k + 1]
            segment(buffer, matrix, ink, ox + a.x * scale, oy + a.y * scale, ox + b.x * scale, oy + b.y * scale, z, line)
        }
    }

    /** A stroke from (x0, y0) to (x1, y1) as a thin quad, seen from both sides, coloured along its length. */
    private fun segment(buffer: VertexConsumer, matrix: Matrix4f, ink: Ink, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, width: Float) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = Mth.sqrt(dx * dx + dy * dy)
        if (len < 1e-6f) return
        val nx = -dy / len * width / 2; val ny = dx / len * width / 2
        // The pigment varies along the stroke, as on a staff's patterns
        val c0 = ink.colours.getColor(ink.time, Vec3(x0 * 8.0, y0 * 8.0, 0.0))
        val c1 = ink.colours.getColor(ink.time, Vec3(x1 * 8.0, y1 * 8.0, 0.0))
        val a = (ink.alpha * 255).toInt()
        fun v(x: Float, y: Float, c: Int) = buffer.vertex(matrix, x, y, z).color((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF, a).endVertex()
        v(x0 - nx, y0 - ny, c0); v(x0 + nx, y0 + ny, c0); v(x1 + nx, y1 + ny, c1); v(x1 - nx, y1 - ny, c1)
        v(x1 - nx, y1 - ny, c1); v(x1 + nx, y1 + ny, c1); v(x0 + nx, y0 + ny, c0); v(x0 - nx, y0 - ny, c0)
    }
}
