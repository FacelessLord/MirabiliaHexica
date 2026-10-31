package com.mirabiliahexica.client

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.util.Mth
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The sky of Tanako's World: no sun or moon, only scattered stars and a great three-armed spiral galaxy whose
 * heart stays fixed above the northern horizon (a dreamer's one sure bearing) while its arms turn slowly around
 * it. Everything is built once into vertex buffers of small camera-facing quads, drawn additively.
 */
object DreamSky {
    /** Where the galaxy's heart hangs: north (-Z), about 35° up. */
    val CENTRE: Vector3f = Vector3f(0f, 0.57f, -0.82f).normalize()

    /** Angular radius of the galaxy, in radians. */
    private const val GALAXY_RADIUS = 0.75f

    /** One full turn every two hours of play. */
    private const val TURN_PER_TICK = Mth.TWO_PI / (2 * 60 * 60 * 20)

    private const val SKY_DISTANCE = 100f

    private var stars: VertexBuffer? = null
    private var galaxy: VertexBuffer? = null

    fun render(pose: PoseStack, projection: Matrix4f, partialTick: Float, level: ClientLevel) {
        val shader = GameRenderer.getPositionColorShader() ?: return
        val stars = stars ?: build(::addStars).also { stars = it }
        val galaxy = galaxy ?: build(::addGalaxy).also { galaxy = it }

        RenderSystem.depthMask(false)
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE)
        RenderSystem.setShader(GameRenderer::getPositionColorShader)

        stars.bind()
        stars.drawWithShader(pose.last().pose(), projection, shader)

        pose.pushPose()
        val angle = ((level.gameTime % (2L * 60 * 60 * 20)) + partialTick) * TURN_PER_TICK
        pose.mulPose(Quaternionf().rotateAxis(angle, CENTRE))
        galaxy.bind()
        galaxy.drawWithShader(pose.last().pose(), projection, shader)
        pose.popPose()

        VertexBuffer.unbind()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
    }

    /** Drops the buffers, e.g. on leaving the world; they are rebuilt on next use. */
    fun clear() {
        stars?.close()
        galaxy?.close()
        stars = null
        galaxy = null
    }

    private fun build(fill: (BufferBuilder) -> Unit): VertexBuffer {
        val builder = Tesselator.getInstance().builder
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        fill(builder)
        return VertexBuffer(VertexBuffer.Usage.STATIC).apply {
            bind()
            upload(builder.end())
            VertexBuffer.unbind()
        }
    }

    private fun addStars(builder: BufferBuilder) {
        val random = Random(4242)
        repeat(1800) {
            // Uniform over the sphere
            val z = random.nextFloat() * 2 - 1
            val a = random.nextFloat() * Mth.TWO_PI
            val r = sqrt(1 - z * z)
            val dir = Vector3f(r * cos(a), z, r * sin(a))
            val bright = 0.45f + random.nextFloat() * 0.55f
            quad(builder, dir, 0.12f + random.nextFloat() * 0.2f, 1f, 1f, 1f, bright)
        }
    }

    private fun addGalaxy(builder: BufferBuilder) {
        val random = Random(1729)
        // Two directions across the sky, perpendicular to the centre, spanning the galaxy's disc
        val u = Vector3f(CENTRE).cross(Vector3f(0f, 1f, 0f)).normalize()
        val v = Vector3f(u).cross(CENTRE).normalize()
        fun at(x: Float, y: Float) =
            Vector3f(CENTRE).add(Vector3f(u).mul(x * GALAXY_RADIUS)).add(Vector3f(v).mul(y * GALAXY_RADIUS)).normalize()
        fun gauss() = (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f) / 1.5f

        // Faint haze along the arms, then the arms' stars, then the bright core on top
        for (layer in 0..1) {
            for (arm in 0 until 3) {
                repeat(if (layer == 0) 260 else 2400) {
                    val r = 0.06f + random.nextFloat().let { it * it } * 0.94f
                    // Logarithmic spiral: the angle grows with ln(r); arms 120° apart
                    val theta = arm * Mth.TWO_PI / 3 + 2.4f * ln(r / 0.06f) + gauss() * 0.22f * (1.2f - r)
                    val spread = gauss() * 0.035f
                    val x = (r + spread) * cos(theta)
                    val y = (r + spread) * sin(theta)
                    if (layer == 0) {
                        quad(builder, at(x, y), 1.6f + random.nextFloat() * 2.2f, 0.55f, 0.45f, 0.95f, 0.05f)
                    } else {
                        val pink = random.nextFloat() < 0.04f
                        val (cr, cg, cb) = when {
                            pink -> Triple(1f, 0.55f, 0.8f)
                            else -> Triple(0.85f - 0.35f * r, 0.8f - 0.25f * r, 1f)
                        }
                        quad(builder, at(x, y), 0.14f + random.nextFloat() * 0.22f, cr, cg, cb, 0.35f + random.nextFloat() * 0.5f)
                    }
                }
            }
        }
        repeat(1400) {
            val x = gauss() * 0.09f
            val y = gauss() * 0.09f
            val glow = 1f - sqrt(x * x + y * y) * 6f
            quad(builder, at(x, y), 0.2f + random.nextFloat() * 0.35f, 1f, 0.88f, 0.68f, (0.3f + 0.6f * glow).coerceIn(0.15f, 0.9f))
        }
        repeat(40) {
            quad(builder, at(gauss() * 0.02f, gauss() * 0.02f), 2.5f + random.nextFloat() * 3f, 1f, 0.85f, 0.6f, 0.06f)
        }
    }

    /** A square of half-width [size] facing the viewer, [SKY_DISTANCE] away in direction [dir]. */
    private fun quad(builder: BufferBuilder, dir: Vector3f, size: Float, r: Float, g: Float, b: Float, a: Float) {
        val centre = Vector3f(dir).mul(SKY_DISTANCE)
        val up = if (abs(dir.y) > 0.99f) Vector3f(1f, 0f, 0f) else Vector3f(0f, 1f, 0f)
        val side = Vector3f(dir).cross(up).normalize().mul(size)
        val top = Vector3f(side).cross(dir).normalize().mul(size)
        for ((sx, sy) in CORNERS) {
            val p = Vector3f(centre).add(Vector3f(side).mul(sx)).add(Vector3f(top).mul(sy))
            builder.vertex(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()).color(r, g, b, a).endVertex()
        }
    }

    private val CORNERS = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)
}
