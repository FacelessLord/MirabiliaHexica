package com.mirabiliahexica.dream

import at.petrak.hexcasting.common.lib.HexBlocks
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaEffects
import com.mirabiliahexica.registry.MirabiliaTags
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import com.mirabiliahexica.entity.GlassMan
import com.mirabiliahexica.entity.LostDreamer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.RelativeMovement
import net.minecraft.world.entity.player.Player
import java.util.EnumSet
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning
import java.util.Locale

/**
 * Tanako's World, the dream. Sleeping in a bed near an Akashic Record carries a player there, to a different
 * spot every time; they wake by standing still and crouching for a while ([DreamTicker]), by the Wake pattern,
 * by freezing, or by dying (which leaves them [MirabiliaEffects.CAME_BACK_WRONG], but with everything they carried). Too much
 * ambient media can pull a caster in too ([DreamPull]).
 */
object Dream {
    val LEVEL: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, Mirabilia.id("tanakos_world"))

    /** How close the Akashic Record must be to the bed. */
    val RECORD_RADIUS get() = Tuning.Dream.recordRadius

    /** Arrival spots are picked within this many blocks of the dream's origin. */
    val ARRIVAL_SPREAD get() = Tuning.Dream.arrivalSpread

    /** Minutes the dreamer stays wrong after dying in a dream. */
    val WRONG_MINUTES get() = Tuning.Dream.cameBackWrongMinutes

    enum class Waking { STILLNESS, PATTERN, COLD, MASK, OTHER }

    fun isDreaming(entity: Entity) = entity.level().dimension() == LEVEL

    fun level(player: ServerPlayer): ServerLevel? = player.server.getLevel(LEVEL)

    /** Whether an Akashic Record lies within [RECORD_RADIUS] blocks of [pos]. */
    fun recordNear(level: Level, pos: BlockPos) = BlockPos.betweenClosedStream(
        pos.offset(-RECORD_RADIUS, -RECORD_RADIUS, -RECORD_RADIUS),
        pos.offset(RECORD_RADIUS, RECORD_RADIUS, RECORD_RADIUS),
    ).anyMatch { level.getBlockState(it).`is`(HexBlocks.AKASHIC_RECORD) }

    /**
     * Carries [player] into the dream; [bed] is where they'll wake (else where they stood). [at] is where they arrive,
     * a random spot if not given. Returns false if the dream can't be reached.
     */
    fun enter(player: ServerPlayer, bed: BlockPos?, at: Vec3? = null, message: String = "mirabilia.dream.enter"): Boolean {
        if (!Features.tanakosWorld) return false
        val dream = level(player) ?: return false
        if (isDreaming(player)) return false
        val visits = DreamVisits.get(player.server)
        visits.start(DreamVisit(player.uuid, player.level().dimension(), player.position(), bed))
        if (player.isSleeping) player.stopSleeping()
        val arrival = at ?: arrival(dream)
        player.teleportTo(dream, arrival.x, arrival.y, arrival.z, player.yRot, 0f)
        player.displayClientMessage(Component.translatable(message), true)
        return true
    }

    /** Brings [player] back from the dream, to their bed if it still stands. */
    fun wake(player: ServerPlayer, how: Waking) {
        val visit = DreamVisits.get(player.server).end(player.uuid)
        log(player, "woke (${how.name.lowercase()})", visit)
        DreamTicker.forget(player)
        // The Glass Man stays behind, unless it was close enough to follow them out
        val followed = GlassManSpawner.near(player, GlassManSpawner.FOLLOW_RANGE)
        // Was he close enough that his touch, not the dream alone, is what finished the cold?
        val touched = how == Waking.COLD && GlassManSpawner.near(player, 4.0)
        if (GlassManSpawner.near(player, JUST_IN_TIME)) MirabiliaAdvancements.award(player, "just_in_time")
        when (how) {
            Waking.COLD -> MirabiliaAdvancements.award(player, "cold_welcome")
            Waking.PATTERN -> MirabiliaAdvancements.award(player, "act_of_will")
            else -> {}
        }
        GlassManSpawner.dismiss(player)
        val (level, pos) = wakingPlace(player, visit)
        // Whatever came in with them (or was found there) and stands close by comes back too, to not be lost
        val company = if (Features.carryCreatures) around(player) { it !is Player && it !is LostDreamer && it !is GlassMan } else emptyList()
        // Hurt before leaving: players can't be hurt while changing dimension. Never fatal, so the cold can't
        // count as dying in the dream.
        if (how == Waking.COLD) player.hurt(player.damageSources().freeze(), minOf(6f, player.health - 1f))
        player.clearFire()
        player.ticksFrozen = 0
        player.teleportTo(level, pos.x, pos.y, pos.z, player.yRot, player.xRot)
        carry(company, level, pos)
        player.displayClientMessage(Component.translatable("mirabilia.dream.wake.${how.name.lowercase()}"), true)
        // And in chat, which stays, saying plainly why
        val why = if (touched) "cold_glass" else how.name.lowercase()
        player.sendSystemMessage(Component.translatable("mirabilia.dream.why.$why").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
        if (followed && Features.isOn("glass_man")) Possession.follow(player)
    }

    /** Waking with the Glass Man this close is cutting it fine. */
    val JUST_IN_TIME get() = Tuning.Advancements.justInTime

    /** How close to a dreamer things must be to go with them, into the dream or out of it. */
    val CARRY_RADIUS get() = Tuning.Pull.carryRadius

    /** The living things within [CARRY_RADIUS] of [player] (not themselves), as [filter] allows. */
    fun around(player: ServerPlayer, filter: (LivingEntity) -> Boolean = { true }): List<LivingEntity> =
        player.serverLevel().getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(CARRY_RADIUS)) {
            it !== player && it.isAlive && !it.isSpectator && it.distanceTo(player) <= CARRY_RADIUS && filter(it)
        }

    /** Moves [entities] (not players) to [level], scattered a block or so round [at]. */
    fun carry(entities: List<LivingEntity>, level: ServerLevel, at: Vec3) {
        for (entity in entities) {
            entity.stopRiding()
            entity.ejectPassengers()
            val x = at.x + entity.random.nextDouble() * 2 - 1
            val z = at.z + entity.random.nextDouble() * 2 - 1
            entity.teleportTo(level, x, at.y, z, EnumSet.noneOf(RelativeMovement::class.java), entity.yRot, entity.xRot)
        }
    }

    /**
     * A line in the server log whenever a dreamer leaves the dream, saying why and in what state, since the
     * message they see on screen isn't logged.
     */
    fun log(player: ServerPlayer, what: String, visit: DreamVisit?) {
        val glass = GlassManSpawner.find(player)?.let { String.format(Locale.ROOT, "%.1f blocks away", it.distanceTo(player)) } ?: "none"
        val cold = visit?.let { "${it.cold}/${DreamTicker.MAX_COLD}" } ?: "?"
        val minutes = visit?.let { String.format(Locale.ROOT, "%.1f", it.ticks / 1200.0) } ?: "?"
        Mirabilia.LOGGER.info("{} {} from Tanako's World after {} min: health {}/{}, cold {}, Glass Man {}",
            player.gameProfile.name, what, minutes, player.health, player.maxHealth, cold, glass)
    }

    /** Called when a player who died in a dream respawns. */
    fun cameBackWrong(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MirabiliaEffects.CAME_BACK_WRONG.value, WRONG_MINUTES * 60 * 20))
        player.displayClientMessage(Component.translatable("mirabilia.dream.came_back_wrong"), false)
    }

    private fun wakingPlace(player: ServerPlayer, visit: DreamVisit?): Pair<ServerLevel, Vec3> {
        val server = player.server
        if (visit != null) {
            val level = server.getLevel(visit.returnDim)
            if (level != null && level.dimension() != LEVEL) {
                val bed = visit.bed
                val state = bed?.let(level::getBlockState)
                if (bed != null && state != null && state.block is BedBlock) {
                    val standUp = BedBlock.findStandUpPosition(EntityType.PLAYER, level, bed, state.getValue(BedBlock.FACING), player.yRot)
                    if (standUp.isPresent) return level to standUp.get()
                }
                return level to visit.returnPos
            }
        }
        // No record of where they came from: their spawn point, or the world's
        val level = server.getLevel(player.respawnDimension)?.takeIf { it.dimension() != LEVEL } ?: server.overworld()
        val spawn = player.respawnPosition?.takeIf { level.dimension() == player.respawnDimension } ?: level.sharedSpawnPos
        return level to Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn))
    }

    /** A random spot on solid ground, different on every visit. */
    fun arrival(dream: ServerLevel): Vec3 {
        val random = dream.random
        repeat(16) {
            val x = random.nextInt(2 * ARRIVAL_SPREAD + 1) - ARRIVAL_SPREAD
            val z = random.nextInt(2 * ARRIVAL_SPREAD + 1) - ARRIVAL_SPREAD
            safeSpot(dream, x, z)?.let { return it }
        }
        return safeSpot(dream, 0, 0) ?: Vec3(0.5, 100.0, 0.5)
    }

    private fun safeSpot(dream: ServerLevel, x: Int, z: Int): Vec3? {
        dream.getChunk(x shr 4, z shr 4) // generates it if needed, so the heightmap is real
        val y = dream.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
        if (y <= dream.minBuildHeight + 1) return null
        val ground = BlockPos(x, y - 1, z)
        // Open ground only: not on top of a pillar, boulder or shrine
        val groundState = dream.getBlockState(ground)
        if (!groundState.`is`(MirabiliaTags.DREAM_GLASS) && !groundState.`is`(MirabiliaBlocks.MARBLE_PEBBLES.value)) return null
        if (!dream.getBlockState(ground.above()).isAir || !dream.getBlockState(ground.above(2)).isAir) return null
        return Vec3(x + 0.5, y.toDouble(), z + 0.5)
    }
}
