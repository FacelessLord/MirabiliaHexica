package com.mirabiliahexica.lens

import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.machines.Sunlight
import dev.architectury.event.events.common.TickEvent
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * Notices, on the server, when a player sees the [SunSign]: wearing a lens, in the overworld, under open sky,
 * looking within [WITHIN_DEGREES] of the sun while it is up and the rain hasn't hidden it. The sign itself is drawn
 * only on the client; this is for the advancements.
 */
object SunSighting {
    val WITHIN_DEGREES get() = Tuning.Advancements.sunWithinDegrees
    private val WITHIN_COS get() = cos(Math.toRadians(WITHIN_DEGREES))

    fun init() {
        TickEvent.SERVER_POST.register(::tick)
    }

    /** Where the sun is in the sky, as the renderer places it: rising in the east (+X), overhead at noon. */
    fun sunDirection(level: Level): Vec3 {
        val angle = level.getSunAngle(1f)
        return Vec3(-Mth.sin(angle).toDouble(), Mth.cos(angle).toDouble(), 0.0)
    }

    fun seesSign(player: ServerPlayer): Boolean {
        if (!Features.sunCaltrop) return false
        val level = player.level()
        if (level.dimension() != Level.OVERWORLD || LensSight.lenses(player).isEmpty()) return false
        if (level.getRainLevel(1f) > 0.5f) return false
        val sun = sunDirection(level)
        if (sun.y <= 0) return false
        if (player.lookAngle.dot(sun) < WITHIN_COS) return false
        return Sunlight.underOpenSky(level, BlockPos.containing(player.eyePosition))
    }

    private fun tick(server: MinecraftServer) {
        if (server.tickCount % 20 != 0) return
        for (player in server.playerList.players) {
            if (seesSign(player)) MirabiliaAdvancements.sawCaltrop(player, SunSign.day(player.level().dayTime))
        }
    }
}
