package com.mirabiliahexica.standing

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.Mirabilia
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID
import com.mirabiliahexica.config.Tuning
import com.mirabiliahexica.config.Features

/**
 * Every standing spell on the server, saved with the overworld (so entity-bound spells can follow their entity
 * between dimensions).
 *
 * Rules:
 * * Raising one costs [STARTUP_COST] plus the media it is funded with; that funding is its whole budget.
 * * Every tick it is active it pays [UPKEEP_PER_TICK]. Every [RUN_PERIOD] ticks it runs its hex, whose spells are
 *   also paid from the budget. The hex starts with the anchor on the stack: the block's centre as a vector, or
 *   the entity it rides on.
 * * It ends when the budget runs out, when its hex mishaps (running out mid-hex included), or when dispelled.
 * * Block-bound spells pause while their chunk is unloaded; entity-bound ones while their entity is unloaded
 *   (a player who logged off, a mob in an unloaded chunk).
 * * An entity-bound spell ends when its entity dies or is destroyed (an item despawning, a boat breaking).
 *   Block-bound spells don't care what happens to their owner.
 * * Its ambit is a [RANGE]-block sphere around the anchor (Hex's sentinel radius).
 */
class StandingSpells : SavedData() {
    private val spells = LinkedHashMap<UUID, StandingSpell>()

    val all: Collection<StandingSpell> get() = spells.values

    fun atBlock(dimension: ResourceKey<Level>, pos: BlockPos) =
        spells.values.firstOrNull { (it.anchor as? StandingSpell.Anchor.Block)?.let { a -> a.dimension == dimension && a.pos == pos } == true }

    fun onEntity(entity: UUID) =
        spells.values.firstOrNull { (it.anchor as? StandingSpell.Anchor.Entity)?.uuid == entity }

    fun countOwnedBy(owner: UUID) = spells.values.count { it.owner == owner }

    fun add(spell: StandingSpell) {
        spells[spell.id] = spell
        setDirty()
    }

    fun remove(spell: StandingSpell) {
        if (spells.remove(spell.id) != null) setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        tag.put(TAG_SPELLS, ListTag().apply { spells.values.forEach { add(it.save()) } })
        return tag
    }

    fun tick(server: MinecraftServer) {
        // Switched off, raised spells wait rather than vanish, so turning them back on finds them where they were
        if (spells.isEmpty() || !Features.standingSpells) return
        for (spell in spells.values.toList()) {
            val (level, entity) = when (val state = state(server, spell)) {
                State.Paused -> continue
                is State.Ended -> {
                    end(state.level, spell)
                    continue
                }
                is State.Active -> state
            }
            spell.media -= UPKEEP_PER_TICK
            if (spell.media <= 0) {
                end(level, spell)
                continue
            }
            if (--spell.ticksUntilRun <= 0) {
                spell.ticksUntilRun = RUN_PERIOD
                if (!run(level, spell, entity)) end(level, spell)
            }
        }
        setDirty()
    }

    private sealed interface State {
        data object Paused : State
        data class Ended(val level: ServerLevel) : State
        data class Active(val level: ServerLevel, val entity: Entity?) : State
    }

    /** Where a spell stands this tick: running (and in which level), paused, or gone. */
    private fun state(server: MinecraftServer, spell: StandingSpell): State = when (val anchor = spell.anchor) {
        is StandingSpell.Anchor.Block ->
            server.getLevel(anchor.dimension)?.takeIf { it.isLoaded(anchor.pos) }?.let { State.Active(it, null) } ?: State.Paused
        is StandingSpell.Anchor.Entity -> {
            val last = spell.entityRef
            when {
                last != null && !last.isRemoved -> State.Active(last.level() as ServerLevel, last)
                // Killed or discarded (not just unloaded or moved to another dimension): the spell goes with it
                last != null && last.removalReason?.shouldDestroy() == true -> State.Ended(last.level() as ServerLevel)
                else -> findEntity(server, anchor.uuid)
                    ?.also { spell.entityRef = it }
                    ?.let { State.Active(it.level() as ServerLevel, it) }
                    ?: State.Paused
            }
        }
    }

    private fun findEntity(server: MinecraftServer, uuid: UUID): Entity? =
        server.playerList.getPlayer(uuid) ?: server.allLevels.firstNotNullOfOrNull { it.getEntity(uuid) }

    /** Runs the hex once; false if it mishapped. */
    private fun run(level: ServerLevel, spell: StandingSpell, entity: Entity?): Boolean {
        val env = StandingSpellEnv(level, spell, entity)
        val start = if (entity != null) EntityIota(entity) else Vec3Iota(env.anchorPos())
        val vm = CastingVM(CastingImage().copy(stack = listOf(start), userData = spell.userData.copy()), env)
        val result = try {
            vm.queueExecuteAndWrapIotas(spell.hex(level), level)
        } catch (e: Exception) {
            Mirabilia.LOGGER.warn("Standing spell {} crashed while running; ending it", spell.id, e)
            return false
        }
        spell.userData = vm.image.userData
        return result.resolutionType != ResolvedPatternType.ERRORED && result.resolutionType != ResolvedPatternType.INVALID
    }

    /** Called just before any living entity dies. */
    fun onEntityDeath(entity: LivingEntity) {
        val level = entity.level() as? ServerLevel ?: return
        onEntity(entity.uuid)?.let { end(level, it) }
    }

    fun end(level: ServerLevel, spell: StandingSpell) {
        remove(spell)
        val pos = StandingSpellEnv(level, spell, spell.entityRef).anchorPos()
        level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 12, 0.3, 0.3, 0.3, 0.02)
    }

    companion object {
        val STARTUP_COST get() = Tuning.Spells.standingStartup
        /** Half a dust per second. (Hexal's Cyclic Wisp: about 0.65 dust per second.) */
        val UPKEEP_PER_TICK get() = Tuning.Spells.standingUpkeepPerSecond / 20
        /** Ticks between runs of the hex: twice a second. */
        val RUN_PERIOD get() = Tuning.Spells.standingPeriod
        val RANGE get() = Tuning.Spells.standingRange
        val MAX_PER_PLAYER get() = Tuning.Spells.standingMax

        private const val TAG_SPELLS = "spells"
        private const val DATA_NAME = "${Mirabilia.MODID}_standing_spells"

        fun get(server: MinecraftServer): StandingSpells =
            server.overworld().dataStorage.computeIfAbsent(::load, ::StandingSpells, DATA_NAME)

        private fun load(tag: CompoundTag) = StandingSpells().apply {
            for (entry in tag.getList(TAG_SPELLS, Tag.TAG_COMPOUND.toInt())) {
                val spell = StandingSpell.load(entry as CompoundTag)
                spells[spell.id] = spell
            }
        }
    }
}
