package com.mirabiliahexica.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getLivingEntityButNotArmorStand
import at.petrak.hexcasting.api.casting.getPositiveDouble
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.casting.mishaps.MishapBadEntity
import at.petrak.hexcasting.api.mod.HexConfig
import at.petrak.hexcasting.common.lib.HexDamageTypes
import net.minecraft.core.registries.Registries
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.village.ReputationEventType
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.math.floor
import com.mirabiliahexica.config.Tuning

/**
 * Blood Charge (great spell): entity, number → nothing. Drains up to that many health points (2 per heart) from a
 * living entity in range into the Phial in the caster's other hand, at Hex's overcast rate (by default 1 Amethyst
 * Dust per health point, so 2 per heart).
 *
 * Only the caster or a peaceful creature (animals, villagers, golems…) can be bled: never another player, and
 * never a monster, so it can't wear down a boss or any other foe for an easy kill. Bleeding a villager angers
 * it, as hitting it would: it remembers, and the village's golems may too.
 *
 * Never takes the last half-heart (so it never kills), and never more than the Phial has room for. The media
 * comes from health actually lost, so invulnerability or resistance can't turn it into free media.
 */
object OpBloodCharge : SpellAction {
    override val argc = 2

    /** Health points always left behind: half a heart. */
    private val HEALTH_FLOOR get() = Tuning.Spells.bloodHealthFloor

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val source = args.getLivingEntityButNotArmorStand(0, argc)
        val requested = args.getPositiveDouble(1, argc)
        env.assertEntityInRange(source)
        if (!canBleed(source, env)) throw MishapBadEntity.of(source, "mirabilia.bleedable")
        val held = PhialHelper.heldPhial(env)

        return SpellAction.Result(
            Spell(source, held, requested),
            0,
            listOf(ParticleSpray.cloud(source.position().add(0.0, source.bbHeight / 2.0, 0.0), 0.5)),
        )
    }

    /** The caster themselves, or a peaceful creature: not another player, not anything hostile. */
    fun canBleed(entity: LivingEntity, env: CastingEnvironment): Boolean = when {
        entity == env.castingEntity -> true
        entity is Player -> false
        entity is Enemy -> false
        else -> entity.type.category.isFriendly
    }

    private data class Spell(val source: LivingEntity, val held: ItemStack, val requested: Double) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            val rate = HexConfig.common().mediaToHealthRate()
            val room = PhialHelper.capacity(held) - PhialHelper.media(held)
            val affordable = (source.health - HEALTH_FLOOR).toDouble()
            val toTake = minOf(requested, affordable, room / rate)
            if (toTake <= 0.0) return

            val before = source.health
            val overcast = DamageSource(source.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(HexDamageTypes.OVERCAST))
            Mishap.trulyHurt(source, overcast, toTake.toFloat())
            val lost = (before - source.health).coerceAtLeast(0f)
            if (lost > 0f && source is Villager) angerVillager(source, env.castingEntity)

            val media = floor(lost * rate).toLong()
            PhialHelper.set(held, PhialHelper.media(held) + media, PhialHelper.capacity(held))
        }

        /** As if struck: the villager holds it against the caster, and shows it. */
        private fun angerVillager(villager: Villager, caster: Entity?) {
            if (caster != null) villager.onReputationEventFrom(ReputationEventType.VILLAGER_HURT, caster)
            villager.level().broadcastEntityEvent(villager, EntityEvent.VILLAGER_ANGRY)
        }
    }
}
