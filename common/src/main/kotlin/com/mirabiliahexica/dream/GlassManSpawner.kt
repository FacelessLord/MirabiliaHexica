package com.mirabiliahexica.dream

import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.entity.GlassMan
import com.mirabiliahexica.registry.MirabiliaEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * When the Glass Man comes for a dreamer. After [GRACE_MINUTES] minutes in a dream, every [PERIOD] ticks there is
 * a chance it starts to come, rising to [MAX_CHANCE] at [FULL_MINUTES] minutes, three times as likely inside a
 * listening post. It never comes unannounced: a glassy chime and a shimmer [WARNING] ticks before, where it will
 * stand, [MIN_DISTANCE]–[MAX_DISTANCE] blocks off. One per dreamer; it goes when they do.
 */
object GlassManSpawner {
    val PERIOD get() = Tuning.GlassMan.period
    val GRACE_MINUTES get() = Tuning.GlassMan.graceMinutes
    val FULL_MINUTES get() = Tuning.GlassMan.fullMinutes
    val MAX_CHANCE get() = Tuning.GlassMan.maxChance
    val WARNING get() = Tuning.GlassMan.warning
    val MIN_DISTANCE get() = Tuning.GlassMan.minDistance
    val MAX_DISTANCE get() = Tuning.GlassMan.maxDistance
    /** How close it holds a dreamer in the dream. */
    val HOLD_RANGE get() = Tuning.GlassMan.holdRange
    /** How close it must be when they wake to follow them out into an empty villager. */
    val FOLLOW_RANGE get() = Tuning.GlassMan.followRange

    private val LISTENING_POST: ResourceKey<Structure> = ResourceKey.create(Registries.STRUCTURE, Mirabilia.id("listening_post"))

    private class Coming(val pos: Vec3, var ticks: Int)

    private val coming = HashMap<UUID, Coming>()

    /** Each dreamer's Glass Man. (A reloaded one claims its place on its first tick; see [claim].) */
    private val following = HashMap<UUID, GlassMan>()

    /**
     * The chance per try. [danger] ([DreamDanger.factor]) shortens the grace (3 minutes at ×1, 45 seconds at ×4)
     * and multiplies the chance, up to [CARRYING_MAX]; a listening post triples it, up to [POST_MAX].
     */
    fun chance(ticksDreaming: Int, inListeningPost: Boolean, danger: Float = 1f): Float {
        val minutes = ticksDreaming / 1200f
        val grace = GRACE_MINUTES / danger
        if (minutes < grace) return 0f
        val base = minOf(CARRYING_MAX, MAX_CHANCE * minOf(1f, (minutes - grace) / (FULL_MINUTES - grace)) * danger)
        return if (inListeningPost) minOf(POST_MAX, base * 3) else base
    }

    val CARRYING_MAX get() = Tuning.GlassMan.carryingMax
    val POST_MAX get() = Tuning.GlassMan.postMax

    /** Called every tick for each dreamer. */
    fun tick(player: ServerPlayer, visit: DreamVisit) {
        if (!Features.isOn("glass_man")) return
        coming[player.uuid]?.let { c ->
            if (--c.ticks <= 0) {
                coming.remove(player.uuid)
                if (find(player) == null) summon(player, c.pos)
            }
            return
        }
        if (visit.ticks % PERIOD != 0 || player.isSpectator || find(player) != null) return
        if (player.random.nextFloat() >= chance(visit.ticks, inListeningPost(player), DreamDanger.factor(player))) return
        spot(player)?.let { announce(player, it) }
    }

    fun inListeningPost(player: ServerPlayer) =
        player.serverLevel().structureManager().getStructureWithPieceAt(player.blockPosition(), LISTENING_POST).isValid

    /** The chime and the shimmer that come before it. */
    fun announce(player: ServerPlayer, at: Vec3) {
        coming[player.uuid] = Coming(at, WARNING)
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1f, 0.4f)
        GlassMan.shimmer(player.serverLevel(), at.x, at.y, at.z)
    }

    fun summon(player: ServerPlayer, at: Vec3): GlassMan? {
        val level = player.serverLevel()
        val glass = MirabiliaEntities.GLASS_MAN.value.create(level) ?: return null
        glass.owner = player.uuid
        glass.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f)
        glass.finalizeSpawn(level, level.getCurrentDifficultyAt(glass.blockPosition()), MobSpawnType.EVENT, null, null)
        level.addFreshEntity(glass)
        following[player.uuid] = glass
        GlassMan.shimmer(level, at.x, at.y, at.z)
        MirabiliaAdvancements.award(player, "someone_watching")
        return glass
    }

    /** The Glass Man following [player], if there is one. */
    fun find(player: ServerPlayer): GlassMan? =
        following[player.uuid]?.takeIf { it.isAlive && !it.isRemoved } ?: null.also { following.remove(player.uuid) }

    /**
     * Called by a Glass Man each tick: true if it is its dreamer's one, taking the place if it's empty (as after a
     * reload); false if another already follows them, and this one should go.
     */
    fun claim(glass: GlassMan, owner: UUID): Boolean {
        val current = following[owner]?.takeIf { it.isAlive && !it.isRemoved }
        if (current == null) following[owner] = glass
        return current == null || current === glass
    }

    fun near(player: ServerPlayer, range: Double) = find(player)?.let { it.distanceTo(player) <= range } == true

    /** Sends it away: the dreamer has woken, died or left. */
    fun dismiss(player: ServerPlayer) {
        coming.remove(player.uuid)
        find(player)?.discard()
        following.remove(player.uuid)
    }

    private fun spot(player: ServerPlayer): Vec3? {
        val level = player.serverLevel()
        repeat(8) {
            val angle = player.random.nextFloat() * Mth.TWO_PI
            val distance = MIN_DISTANCE + player.random.nextFloat() * (MAX_DISTANCE - MIN_DISTANCE)
            val x = Mth.floor(player.x + cos(angle) * distance)
            val z = Mth.floor(player.z + sin(angle) * distance)
            if (!level.hasChunk(x shr 4, z shr 4)) return@repeat
            val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            if (level.getBlockState(BlockPos(x, y, z)).isAir && level.getBlockState(BlockPos(x, y + 1, z)).isAir) {
                return Vec3(x + 0.5, y.toDouble(), z + 0.5)
            }
        }
        return null
    }
}
