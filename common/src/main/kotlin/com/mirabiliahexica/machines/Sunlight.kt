package com.mirabiliahexica.machines

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import com.mirabiliahexica.config.Tuning

/**
 * How strongly the Sun is thinking at a place, 0 to 1: the sun's height in the overworld sky (1 at noon, nothing
 * from dusk to dawn), dimmed by rain ([RAIN]) and all but quenched by thunder ([THUNDER]). Nothing anywhere else.
 */
object Sunlight {
    val RAIN get() = Tuning.Machines.solarRain
    val THUNDER get() = Tuning.Machines.solarThunder

    fun intensity(level: Level, pos: BlockPos): Double {
        if (level.dimension() != Level.OVERWORLD) return 0.0
        val height = Mth.cos(level.getSunAngle(1f)).toDouble()
        if (height <= 0) return 0.0
        val weather = when {
            level.isThunderingAt(pos) -> THUNDER
            level.isRainingAt(pos) -> RAIN
            else -> 1.0
        }
        return height * weather
    }

    /** Whether nothing above [pos] keeps the sun off it (glass and the like let it through). */
    fun underOpenSky(level: Level, pos: BlockPos): Boolean {
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.x, pos.z)
        val cursor = pos.mutable()
        for (y in pos.y + 1 until top) {
            cursor.setY(y)
            if (!level.getBlockState(cursor).propagatesSkylightDown(level, cursor)) return false
        }
        return true
    }

    private fun Level.isThunderingAt(pos: BlockPos) = isThundering && isRainingAt(pos)
}
