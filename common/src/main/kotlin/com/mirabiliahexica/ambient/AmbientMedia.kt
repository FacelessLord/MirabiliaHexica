package com.mirabiliahexica.ambient

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.Mirabilia
import dev.architectury.event.events.common.TickEvent
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * Ambient media: what hangs in the air of each chunk after magic is done there. A share of every hex's cost
 * ([CAST_SHARE]) settles in the chunk it was cast in, and budding amethyst breathes a little out with every growth
 * tick ([BUDDING_TICK]). It fades ([DECAY] kept every [STEP] ticks) and spills into neighbouring chunks
 * ([SPREAD] of the difference to each), so it never piles up forever.
 *
 * High levels are felt: from [CHARGED] there are sparks in the air; from [STUFFY] hexes cast there may misfire
 * (see [misfires]); from [PULL] the dream may take a caster (Tanako's World).
 *
 * All the numbers are here, to tune.
 */
object AmbientMedia {
    const val DUST = MediaConstants.DUST_UNIT

    val CAST_SHARE get() = Tuning.Ambient.castShare
    val BUDDING_TICK get() = Tuning.Ambient.buddingTick
    val STEP get() = Tuning.Ambient.stepTicks
    val DECAY get() = Tuning.Ambient.decay
    val SPREAD get() = Tuning.Ambient.spread
    val CHARGED get() = Tuning.Ambient.charged
    val STUFFY get() = Tuning.Ambient.stuffy
    val PULL get() = Tuning.Ambient.pull
    /** Misfire chance per cast: (level − STUFFY) ÷ this, up to [MAX_MISFIRE]. */
    val MISFIRE_PER get() = Tuning.Ambient.misfirePer
    val MAX_MISFIRE get() = Tuning.Ambient.maxMisfire

    /** Anything weaker than this is forgotten. */
    private const val TRACE = DUST / 100

    fun init() {
        CastingEnvironment.addCreateEventListener { env, _ -> env.addExtension(Tap(env)) }
        TickEvent.SERVER_POST.register(::tick)
    }

    fun get(level: ServerLevel, chunk: ChunkPos): Long = data(level).levels.get(chunk.toLong())

    fun get(level: ServerLevel, pos: BlockPos) = get(level, ChunkPos(pos))

    fun add(level: ServerLevel, pos: BlockPos, amount: Long) {
        if (amount <= 0 || !Features.ambientMedia) return
        val data = data(level)
        data.levels.addTo(ChunkPos(pos).toLong(), amount)
        data.setDirty()
    }

    fun set(level: ServerLevel, chunk: ChunkPos, amount: Long) {
        val data = data(level)
        if (amount <= 0) data.levels.remove(chunk.toLong()) else data.levels.put(chunk.toLong(), amount)
        data.setDirty()
    }

    /** Whether a cast by [env] falls apart in stuffy air (rolled once, when the cast begins). */
    fun misfires(env: CastingEnvironment): Boolean {
        if (!Features.ambientMedia) return false
        val level = get(env.world, BlockPos.containing(castPos(env)))
        if (level <= STUFFY) return false
        val chance = minOf(MAX_MISFIRE, (level - STUFFY).toDouble() / MISFIRE_PER)
        return env.world.random.nextDouble() < chance
    }

    /** Where a cast happens: the caster for player casts, otherwise the circle's impetus, the standing spell… */
    fun castPos(env: CastingEnvironment): Vec3 =
        (env as? PlayerBasedCastEnv)?.castingEntity?.position() ?: env.mishapSprayPos()

    private fun tick(server: MinecraftServer) {
        if (!Features.ambientMedia) return
        for (level in server.allLevels) {
            if (server.tickCount % STEP == 0) step(level)
            if (server.tickCount % 40 == 0) sparks(level)
        }
    }

    /** One round of fading and spreading. */
    fun step(level: ServerLevel) {
        val data = data(level)
        if (data.levels.isEmpty()) return
        val next = Long2LongOpenHashMap()
        for (entry in data.levels.long2LongEntrySet()) {
            val chunk = ChunkPos(entry.longKey)
            var here = (entry.longValue * DECAY).toLong()
            for ((dx, dz) in NEIGHBOURS) {
                val there = ChunkPos.asLong(chunk.x + dx, chunk.z + dz)
                val difference = here - data.levels.get(there)
                if (difference <= 0) continue
                val flow = (difference * SPREAD).toLong()
                next.addTo(there, flow)
                here -= flow
            }
            next.addTo(entry.longKey, here)
        }
        data.levels.clear()
        for (entry in next.long2LongEntrySet()) if (entry.longValue >= TRACE) data.levels.put(entry.longKey, entry.longValue)
        data.setDirty()
    }

    /** Charged air sparks round the players in it. */
    private fun sparks(level: ServerLevel) {
        for (player in level.players()) {
            val here = get(level, player.blockPosition())
            if (here < CHARGED) continue
            MirabiliaAdvancements.award(player, "root", "charged_air")
            val count = minOf(12, 1 + ((here - CHARGED) / (250L * DUST)).toInt())
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.x, player.y + 1, player.z, count, 3.0, 1.5, 3.0, 0.05)
        }
    }

    private val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /** Notes what each cast spends, as it's paid, and lets a share settle where it was cast. */
    private class Tap(private val env: CastingEnvironment) : CastingEnvironmentComponent.ExtractMedia.Pre {
        override fun getKey() = KEY

        override fun onExtractMedia(cost: Long, simulate: Boolean): Long {
            if (!simulate && cost > 0) add(env.world, BlockPos.containing(castPos(env)), (cost * CAST_SHARE).toLong())
            return cost
        }
    }

    private val KEY = object : CastingEnvironmentComponent.Key<Tap> {}

    private fun data(level: ServerLevel): Data = level.dataStorage.computeIfAbsent(::load, ::Data, "${Mirabilia.MODID}_ambient_media")

    private fun load(tag: CompoundTag) = Data().apply {
        val chunks = tag.getLongArray("chunks")
        val amounts = tag.getLongArray("amounts")
        for (i in chunks.indices) levels.put(chunks[i], amounts.getOrElse(i) { 0 })
    }

    /** The levels of one dimension, by chunk. */
    class Data : SavedData() {
        val levels = Long2LongOpenHashMap()

        override fun save(tag: CompoundTag): CompoundTag {
            tag.putLongArray("chunks", levels.keys.toLongArray())
            tag.putLongArray("amounts", levels.keys.map { levels.get(it) }.toLongArray())
            return tag
        }
    }
}
