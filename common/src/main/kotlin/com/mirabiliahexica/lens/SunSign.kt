package com.mirabiliahexica.lens

import net.minecraft.util.Mth
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The sign on the Sun: a caltrop (four spikes from one centre, at equal angles, pointing to the corners of a
 * tetrahedron) that anyone wearing a lens sees laid over the sun. One spike always points up the sky, leaning a
 * little towards the viewer ([TILT]) so the other three are seen as if from slightly above; those three turn about
 * it a little further each day ([DAILY_TURN]) and hold it until the next; as they repeat every 120°, the sign comes
 * back round every ten days. Lore only; nothing reads the angle. Drawn by `SunSignRenderer` on
 * the client, in the sun's own frame: the viewer looks along +Y, the disc lies in X–Z.
 */
object SunSign {
    /** How far the three lower spikes turn each day. */
    val DAILY_TURN = Math.toRadians(12.0).toFloat()

    /** How far the up spike leans towards the viewer, so the turn of the other three reads. */
    private val TILT = Math.toRadians(25.0).toFloat()

    /** How far from noon (in sun angle) "up" turns over from the morning sky to the evening one, through south. */
    private val TURNOVER = Math.toRadians(20.0).toFloat()

    fun day(dayTime: Long) = Math.floorDiv(dayTime, 24000L)

    /**
     * "Up the sky" across the sun's disc, in its frame, for a sun angle (`Level.getSunAngle`): towards the zenith
     * through the morning and the evening; around noon, when the sun is overhead and has no up, it turns over
     * through south. It leans [TILT] towards the viewer (who looks along +Y).
     */
    fun up(sunAngle: Float): Vector3f {
        val fromNoon = atan2(sin(sunAngle), cos(sunAngle))
        val turn = ((fromNoon + TURNOVER) / (2 * TURNOVER)).coerceIn(0f, 1f) * Mth.PI
        return Vector3f(sin(turn) * cos(TILT), -sin(TILT), cos(turn) * cos(TILT))
    }

    /** The four spike directions (unit length) on [day]: the first is [up], the others turned about it. */
    fun spikes(day: Long, up: Vector3f): List<Vector3f> {
        val axis = Vector3f(up).normalize()
        // Any direction square to the up spike, to start the other three from
        val across = Vector3f(axis).cross(0f, 1f, 0f).let { if (it.lengthSquared() < 1e-4f) Vector3f(axis).cross(1f, 0f, 0f) else it }.normalize()
        val turn = (day % 1_000_000L) * DAILY_TURN
        // The other three spikes sit at cos = -1/3 from the first, 120° apart around it
        val down = -1f / 3f
        val out = sqrt(1f - down * down)
        return listOf(axis) + (0 until 3).map { i ->
            val around = Quaternionf().rotateAxis(turn + i * 2f * Mth.PI / 3f, axis).transform(Vector3f(across))
            Vector3f(axis).mul(down).add(around.mul(out)).normalize()
        }
    }
}
