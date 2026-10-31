package com.mirabiliahexica.machines

import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.registry.MirabiliaBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import net.minecraft.nbt.CompoundTag
import kotlin.math.roundToLong
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/** Draws [SHARE] of its chunk's ambient media every second. Made by Flay Mind: a farmer onto budding amethyst. */
class AmbientCollectorBlockEntity(pos: BlockPos, state: BlockState) :
    MediaStore(MirabiliaBlockEntities.AMBIENT_COLLECTOR.value, pos, state, CAPACITY) {
    fun second(level: ServerLevel) {
        if (!Features.isOn("ambient_collector")) return
        val chunk = ChunkPos(blockPos)
        val here = AmbientMedia.get(level, chunk)
        val taken = fill((here * SHARE).toLong())
        if (taken > 0) AmbientMedia.set(level, chunk, here - taken)
        if (!announced) {
            announced = true
            setChanged()
            MirabiliaAdvancements.awardNear(level, blockPos, "background_harvest")
        }
    }

    /** Whether those nearby have been told it was made (it is made by Flay Mind, not placed). */
    private var announced = false

    override fun load(tag: CompoundTag) {
        super.load(tag)
        announced = tag.getBoolean(ANNOUNCED)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putBoolean(ANNOUNCED, announced)
    }

    companion object {
        val SHARE get() = Tuning.Machines.ambientShare
        private const val ANNOUNCED = "announced"
        val CAPACITY get() = Tuning.Machines.ambientCapacity
    }
}

/** Gathers the Sun's thought: [AT_NOON] a second at noon under open sky, scaled by [Sunlight.intensity]. */
class SolarCollectorBlockEntity(pos: BlockPos, state: BlockState) :
    MediaStore(MirabiliaBlockEntities.SOLAR_COLLECTOR.value, pos, state, CAPACITY) {
    fun second(level: ServerLevel) {
        if (!Features.isOn("solar_collector") || !Sunlight.underOpenSky(level, blockPos)) return
        val intensity = Sunlight.intensity(level, blockPos)
        val gathered = fill((AT_NOON * intensity).toLong())
        val owner = owner ?: return
        if (gathered <= 0) return
        MirabiliaAdvancements.solarGathered(level.server, owner, gathered)
        if (intensity >= NOON) level.server.playerList.getPlayer(owner)?.let { MirabiliaAdvancements.award(it, "noon") }
    }

    companion object {
        val AT_NOON get() = Tuning.Machines.solarAtNoon
        /** Intensity that counts as noon, for the advancement. */
        const val NOON = 0.99
        val CAPACITY get() = Tuning.Machines.solarCapacity
    }
}
