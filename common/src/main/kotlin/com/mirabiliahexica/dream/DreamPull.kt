package com.mirabiliahexica.dream

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import com.mirabiliahexica.ambient.AmbientMedia
import dev.architectury.event.events.common.TickEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import java.util.UUID
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * Where the air is thickest with media ([AmbientMedia.PULL] and up), the dream comes close enough to fall into. Each
 * cast a player makes there has a [chance] to pull them in, along with every living thing within [RADIUS] blocks.
 * The dream feeds as it does, halving the chunk's ambient media. From [WARN_AT] there is a warning, once every
 * [WARN_EVERY] ticks.
 */
object DreamPull {
    val BASE_CHANCE get() = Tuning.Pull.baseChance
    val MAX_CHANCE get() = Tuning.Pull.maxChance
    /** Ambient media per extra 100% chance above [AmbientMedia.PULL]. */
    val CHANCE_PER get() = Tuning.Pull.chancePer
    val RADIUS get() = Tuning.Pull.carryRadius
    val WARN_AT get() = Tuning.Pull.warnAt
    val WARN_EVERY get() = Tuning.Pull.warnEvery

    /** Players to pull at the end of this tick: not mid-cast, so the hex that called the dream finishes first. */
    private val pending = LinkedHashSet<ServerPlayer>()
    private val warned = HashMap<UUID, Long>()

    fun init() {
        TickEvent.SERVER_POST.register(::tick)
    }

    fun chance(level: Long): Double =
        if (level < AmbientMedia.PULL) 0.0 else minOf(MAX_CHANCE, BASE_CHANCE + (level - AmbientMedia.PULL).toDouble() / CHANCE_PER)

    /** Rolled once as each cast begins. True if [env]'s caster is to be pulled under; the cast is then silenced. */
    fun rolls(env: CastingEnvironment): Boolean {
        if (!Features.isOn("dream_pull")) return false
        val player = env.castingEntity as? ServerPlayer ?: return false
        if (Dream.isDreaming(player) || player in pending) return false
        val level = AmbientMedia.get(env.world, player.blockPosition())
        if (env.world.random.nextDouble() >= chance(level)) return false
        pending += player
        return true
    }

    /** Pulls [player] into the dream now, with everything living close by. False if they can't go. */
    fun pull(player: ServerPlayer): Boolean {
        val from = player.serverLevel()
        val dream = Dream.level(player) ?: return false
        if (Dream.isDreaming(player) || player.isSpectator || !player.isAlive) return false
        val around = Dream.around(player)
        val origin = player.position()
        val arrival = Dream.arrival(dream)
        if (!Dream.enter(player, null, arrival, "mirabilia.dream.pulled")) return false
        MirabiliaAdvancements.award(player, "fell_through")
        val chunk = ChunkPos(BlockPos.containing(origin))
        AmbientMedia.set(from, chunk, AmbientMedia.get(from, chunk) / 2)
        // Other players fall as dreamers in their own right; everything else just comes along
        for (other in around.filterIsInstance<ServerPlayer>()) {
            if (Dream.enter(other, null, arrival.add(other.random.nextDouble() * 2 - 1, 0.0, other.random.nextDouble() * 2 - 1), "mirabilia.dream.pulled_along")) {
                MirabiliaAdvancements.award(other, "fell_through")
            }
        }
        if (Features.carryCreatures) Dream.carry(around.filter { it !is ServerPlayer }, dream, arrival)
        return true
    }

    private fun tick(server: MinecraftServer) {
        if (pending.isNotEmpty()) {
            val now = pending.toList()
            pending.clear()
            now.forEach(::pull)
        }
        if (server.tickCount % 40 != 0) return
        for (player in server.playerList.players) {
            if (Dream.isDreaming(player)) continue
            if (AmbientMedia.get(player.serverLevel(), player.blockPosition()) < WARN_AT) continue
            val last = warned[player.uuid]
            if (last != null && server.tickCount - last < WARN_EVERY) continue
            warned[player.uuid] = server.tickCount.toLong()
            player.displayClientMessage(Component.translatable("mirabilia.dream.thin_air"), true)
        }
    }
}
