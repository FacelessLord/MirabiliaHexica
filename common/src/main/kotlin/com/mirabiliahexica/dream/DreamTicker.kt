package com.mirabiliahexica.dream

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import com.mirabiliahexica.config.Tuning

/**
 * Runs once per server tick, after the worlds have ticked:
 *
 * * Sleepers who have slept long enough beside an Akashic Record fall into the dream. (This runs before the next
 *   world tick would end the night and wake them.)
 * * Dreamers who crouch without moving for [STILL_TICKS] wake.
 * * Lost Dreamers appear around dreamers, more as time goes on ([DreamSpawner]).
 * * The dream is cold: it builds up over [MAX_COLD] ticks, shown as freezing, and a fully frozen dreamer is
 *   thrown out with damage. Each piece of leather (or other freeze-proof) armor slows it by a quarter; standing
 *   near a fire, lit campfire or lava warms them back up.
 */
object DreamTicker {
    val STILL_TICKS get() = Tuning.Dream.stillTicks
    val MAX_COLD get() = Tuning.Dream.maxCold
    private val WARMTH_RADIUS get() = Tuning.Dream.warmthRadius
    private val WARMING_PER_SECOND get() = Tuning.Dream.warmingPerSecond
    private const val SNOW_RADIUS = 64
    private const val SNOW_CHECKS_PER_TICK = 32

    private val still = mutableMapOf<UUID, Stillness>()

    private class Stillness(var x: Double, var y: Double, var z: Double, var ticks: Int = 0)

    fun forget(player: ServerPlayer) {
        still.remove(player.uuid)
        DreamDanger.forget(player)
    }

    fun tick(server: MinecraftServer) {
        for (player in server.playerList.players) {
            if (Dream.isDreaming(player)) {
                tickDreamer(player)
            } else if (player.isSleepingLongEnough) {
                val bed = player.sleepingPos.orElse(null) ?: continue
                if (Dream.recordNear(player.level(), bed)) Dream.enter(player, bed)
            }
        }
    }

    private fun tickDreamer(player: ServerPlayer) {
        if (player.isSpectator) return
        if (tickStillness(player)) {
            Dream.wake(player, Dream.Waking.STILLNESS)
            return
        }
        meltOldSnow(player)
        val visits = DreamVisits.get(player.server)
        val visit = visits[player.uuid] ?: return
        // Cold and time spent change every tick
        visits.setDirty()
        DreamSpawner.tick(player, visit)
        GlassManSpawner.tick(player, visit)
        // The game mode itself rather than isCreative(), which GameTest mock players always claim
        if (player.gameMode.isCreative) return
        visit.cold = (visit.cold + coldGain(player)).coerceAtLeast(0)
        if (player.tickCount % 20 == 0 && isWarm(player)) visit.cold = (visit.cold - WARMING_PER_SECOND).coerceAtLeast(0)
        if (visit.cold >= MAX_COLD) {
            Dream.wake(player, Dream.Waking.COLD)
            return
        }
        // Vanilla's frost overlay and slowdown, kept just short of the point where vanilla starts hurting
        player.ticksFrozen = visit.cold * (player.ticksRequiredToFreeze - 1) / MAX_COLD
    }

    /**
     * Worlds from before the dream's biomes were made warm have snow lying everywhere. Clears it a few random
     * columns at a time around each dreamer, like random ticks, so it fades within a minute or two.
     */
    fun meltOldSnow(player: ServerPlayer) {
        val level = player.serverLevel()
        val random = player.random
        repeat(SNOW_CHECKS_PER_TICK) {
            val x = player.blockX + random.nextInt(2 * SNOW_RADIUS + 1) - SNOW_RADIUS
            val z = player.blockZ + random.nextInt(2 * SNOW_RADIUS + 1) - SNOW_RADIUS
            if (!level.hasChunk(x shr 4, z shr 4)) return@repeat
            val top = BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z)
            if (level.getBlockState(top).`is`(Blocks.SNOW)) level.setBlock(top, Blocks.AIR.defaultBlockState(), 2)
        }
    }

    /** True once the player has crouched in place long enough. */
    private fun tickStillness(player: ServerPlayer): Boolean {
        val s = still.getOrPut(player.uuid) { Stillness(player.x, player.y, player.z) }
        val moved = player.distanceToSqr(s.x, s.y, s.z) > 0.01
        s.x = player.x
        s.y = player.y
        s.z = player.z
        if (!player.isShiftKeyDown || moved) {
            if (s.ticks > 0) s.ticks = 0
            return false
        }
        // The Glass Man won't let them go quietly
        if (GlassManSpawner.near(player, GlassManSpawner.HOLD_RANGE)) {
            if (s.ticks == 0) player.displayClientMessage(Component.translatable("mirabilia.glass_man.holds"), true)
            s.ticks = -1
            return false
        }
        if (s.ticks < 0) s.ticks = 0
        s.ticks++
        if (s.ticks == STILL_TICKS / 4) player.displayClientMessage(Component.translatable("mirabilia.dream.stirring"), true)
        return s.ticks >= STILL_TICKS
    }

    /** Cold gained this tick: 4 per tick unprotected, one less per freeze-proof armor piece. */
    private fun coldGain(player: ServerPlayer): Int =
        4 - player.armorSlots.count { it.`is`(ItemTags.FREEZE_IMMUNE_WEARABLES) }

    private fun isWarm(player: ServerPlayer): Boolean {
        val level = player.level()
        val at = player.blockPosition()
        return BlockPos.betweenClosedStream(at.offset(-WARMTH_RADIUS, -WARMTH_RADIUS, -WARMTH_RADIUS), at.offset(WARMTH_RADIUS, WARMTH_RADIUS, WARMTH_RADIUS))
            .anyMatch { pos ->
                val state = level.getBlockState(pos)
                state.`is`(BlockTags.FIRE) || state.`is`(Blocks.LAVA) || state.`is`(Blocks.MAGMA_BLOCK) ||
                    (state.`is`(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT))
            }
    }
}
