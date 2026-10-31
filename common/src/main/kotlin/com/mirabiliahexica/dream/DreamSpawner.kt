package com.mirabiliahexica.dream

import com.mirabiliahexica.entity.LostDreamer
import com.mirabiliahexica.registry.MirabiliaEntities
import com.mirabiliahexica.registry.MirabiliaTags
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.cos
import kotlin.math.sin
import com.mirabiliahexica.config.Tuning

/**
 * Lost Dreamers find whoever lingers in the dream. Every [PERIOD] ticks each dreamer has a chance of one
 * appearing [MIN_DISTANCE]–[MAX_DISTANCE] blocks away, as long as fewer than the cap are already near them.
 * Both grow with the time spent dreaming this visit:
 *
 * | minutes dreaming | chance per try | cap nearby |
 * |---|---|---|
 * | 0 | 3% | 1 |
 * | 5 | 23% | 3 |
 * | 10 | 40% | 5 |
 * | 13+ | 40% | 6 |
 *
 * Carrying media multiplies the chance (up to [MAX_CHANCE]) and raises the cap by one per whole step of the
 * [DreamDanger] factor, so up to 9.
 */
object DreamSpawner {
    val PERIOD get() = Tuning.Dream.lostPeriod
    val MIN_DISTANCE get() = Tuning.Dream.lostMinDistance
    val MAX_DISTANCE get() = Tuning.Dream.lostMaxDistance
    private const val NEARBY = 48.0

    /** The chance per try; [danger] is [DreamDanger.factor]: carrying media makes them come faster. */
    fun chance(ticksDreaming: Int, danger: Float = 1f): Float =
        minOf(Tuning.Dream.lostChanceCap, Tuning.Dream.lostBaseChance + minutes(ticksDreaming) * Tuning.Dream.lostChancePerMinute).let { minOf(MAX_CHANCE, it * danger) }

    /** How many may be near at once; one more for each whole step of [danger]. */
    fun cap(ticksDreaming: Int, danger: Float = 1f): Int = minOf(Tuning.Dream.lostMaxNearby, 1 + minutes(ticksDreaming) * 2 / 5) + (danger.toInt() - 1)

    val MAX_CHANCE get() = Tuning.Dream.lostMaxChance

    private fun minutes(ticks: Int) = ticks / (60 * 20)

    /** Called every tick for each dreamer. */
    fun tick(player: ServerPlayer, visit: DreamVisit) {
        visit.ticks++
        if (visit.ticks % PERIOD != 0 || player.isSpectator) return
        val danger = DreamDanger.factor(player)
        if (player.random.nextFloat() >= chance(visit.ticks, danger)) return
        if (nearby(player) >= cap(visit.ticks, danger)) return
        spawnNear(player)
    }

    fun nearby(player: ServerPlayer) =
        player.serverLevel().getEntitiesOfClass(LostDreamer::class.java, player.boundingBox.inflate(NEARBY)).size

    /** Tries a few spots around [player]; returns the new Lost Dreamer, or null if none was suitable. */
    fun spawnNear(player: ServerPlayer): LostDreamer? {
        val level = player.serverLevel()
        val random = player.random
        repeat(6) {
            val angle = random.nextFloat() * Mth.TWO_PI
            val distance = MIN_DISTANCE + random.nextFloat() * (MAX_DISTANCE - MIN_DISTANCE)
            val x = Mth.floor(player.x + cos(angle) * distance)
            val z = Mth.floor(player.z + sin(angle) * distance)
            if (!level.hasChunk(x shr 4, z shr 4)) return@repeat
            val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            val ground = BlockPos(x, y - 1, z)
            if (!level.getBlockState(ground).`is`(MirabiliaTags.DREAM_SHAPEABLE)) return@repeat
            val dreamer = MirabiliaEntities.LOST_DREAMER.value.create(level) ?: return null
            dreamer.moveTo(x + 0.5, y.toDouble(), z + 0.5, random.nextFloat() * 360f, 0f)
            if (!level.noCollision(dreamer)) return@repeat
            // Never a baby, never riding a chicken
            dreamer.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, Zombie.ZombieGroupData(false, false), null)
            level.addFreshEntityWithPassengers(dreamer)
            return dreamer
        }
        return null
    }
}
