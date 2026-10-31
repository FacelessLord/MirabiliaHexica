package com.mirabiliahexica.dream

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent
import at.petrak.hexcasting.common.items.magic.ItemPackagedHex
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.registry.MirabiliaEffects
import dev.architectury.event.CompoundEventResult
import dev.architectury.event.EventResult
import dev.architectury.event.events.common.EntityEvent
import dev.architectury.event.events.common.InteractionEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/**
 * What the dream forbids, and the price of dying in it:
 *
 * * Hexes cast in the dream reach nothing (like a Silence Stone everywhere); only dream patterns such as Wake,
 *   which need no reach, work. Casting items (cyphers, trinkets, artifacts) won't even start.
 * * Beds don't work there.
 * * Dying there costs nothing carried: inventory, bauble slots and experience come back on respawn.
 * * A player who dies dreaming respawns [MirabiliaEffects.CAME_BACK_WRONG]: while it lasts, each cast has a
 *   [MISFIRE_CHANCE] chance to reach nothing, as if silenced. Milk cures it, like any effect.
 */
object DreamRules {
    val MISFIRE_CHANCE get() = Tuning.Dream.cameBackWrongMisfire

    fun init() {
        CastingEnvironment.addCreateEventListener { env, _ -> if (shouldSilence(env)) env.addExtension(Silenced) }
        TickEvent.SERVER_POST.register(DreamTicker::tick)
        InteractionEvent.RIGHT_CLICK_ITEM.register { player, hand ->
            val stack = player.getItemInHand(hand)
            if (stack.item is ItemPackagedHex && Dream.isDreaming(player)) {
                if (!player.level().isClientSide) player.displayClientMessage(Component.translatable("mirabilia.dream.no_casting_items"), true)
                CompoundEventResult.interruptFalse(stack)
            } else {
                CompoundEventResult.pass()
            }
        }
        InteractionEvent.RIGHT_CLICK_BLOCK.register { player, _, pos, _ ->
            if (Dream.isDreaming(player) && player.level().getBlockState(pos).block is BedBlock) {
                if (!player.level().isClientSide) player.displayClientMessage(Component.translatable("mirabilia.dream.no_beds"), true)
                EventResult.interruptFalse()
            } else {
                EventResult.pass()
            }
        }
        EntityEvent.LIVING_DEATH.register { entity, _ ->
            if (entity is ServerPlayer && Dream.isDreaming(entity)) {
                val visits = DreamVisits.get(entity.server)
                visits.end(entity.uuid)
                visits.markDied(entity.uuid)
                // Nothing is lost to the dream: inventory and experience wait for them to respawn (bauble
                // slots are kept by the Curios/Trinkets interop)
                if (Features.keepItemsOnDreamDeath) visits.keep(entity.uuid, Belongings.take(entity))
                DreamTicker.forget(entity)
                GlassManSpawner.dismiss(entity)
            }
            EventResult.pass()
        }
        PlayerEvent.PLAYER_RESPAWN.register { player, _ ->
            val visits = DreamVisits.get(player.server)
            visits.takeKept(player.uuid)?.let { Belongings.give(player, it) }
            if (visits.takeDied(player.uuid)) Dream.cameBackWrong(player)
        }
    }

    /** Whether [env]'s cast reaches nothing at all, because it happens in the dream or it misfired. */
    fun silenced(env: CastingEnvironment) = env.getExtension(SILENCED) != null

    private fun shouldSilence(env: CastingEnvironment): Boolean {
        if (env.world.dimension() == Dream.LEVEL) return true
        // Stuffy air: too much ambient media where the cast happens
        if (AmbientMedia.misfires(env)) {
            (env.castingEntity as? ServerPlayer)?.let { MirabiliaAdvancements.award(it, "stuffy") }
            return true
        }
        // Thicker still: the dream may take the caster, and the hex goes nowhere
        if (DreamPull.rolls(env)) return true
        val caster = env.castingEntity ?: return false
        return caster.hasEffect(MirabiliaEffects.CAME_BACK_WRONG.value) && env.world.random.nextFloat() < MISFIRE_CHANCE
    }

    private object Silenced : CastingEnvironmentComponent.IsVecInRange {
        override fun getKey() = SILENCED

        override fun onIsVecInRange(vec: Vec3, current: Boolean) = false
    }

    private val SILENCED = object : CastingEnvironmentComponent.Key<Silenced> {}
}
