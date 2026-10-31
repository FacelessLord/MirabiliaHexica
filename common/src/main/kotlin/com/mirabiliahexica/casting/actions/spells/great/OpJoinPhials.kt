package com.mirabiliahexica.casting.actions.spells.great

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getItemEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadItem
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.utils.extractMedia
import at.petrak.hexcasting.api.utils.isMediaItem
import com.mirabiliahexica.casting.actions.spells.PhialHelper
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.config.Tuning

/**
 * Join Phials (great spell): item entity → nothing. Grows the Phial in the caster's other hand, up to
 * [PhialHelper.MAX_CAPACITY]; Recharge Item can only refill a Phial, this makes it bigger.
 *
 * * Aimed at another dropped **Phial**: the two merge. Capacity and media both add up; the dropped one is used up.
 *   Refused, with nothing lost, if the result would be too big.
 * * Aimed at dropped **raw media** (dust, shards, Charged Amethyst; whatever Craft Phial accepts): the Phial drinks
 *   in as many whole items of the stack as fit, all at once, gaining their media as both capacity and charge.
 */
object OpJoinPhials : SpellAction {
    override val argc = 1

    val COST get() = Tuning.Spells.joinPhialsCost

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val target = args.getItemEntity(0, argc)
        env.assertEntityInRange(target)
        val held = PhialHelper.heldPhial(env)
        val room = PhialHelper.MAX_CAPACITY - PhialHelper.capacity(held)

        val plan = if (PhialHelper.isPhial(target.item)) {
            if (target.item.count != 1) throw MishapBadItem.of(target, "only_one")
            if (PhialHelper.capacity(target.item) > room) throw PhialHelper.tooBig(target)
            Plan.Merge
        } else {
            if (!isMediaItem(target.item) || extractMedia(target.item, drainForBatteries = true, simulate = true) <= 0) {
                throw MishapBadItem.of(target, "media_for_battery")
            }
            val perItem = extractMedia(target.item.copyWithCount(1), drainForBatteries = true, simulate = true)
            val count = minOf(target.item.count.toLong(), room / perItem).toInt()
            if (count <= 0) throw PhialHelper.tooBig(target)
            Plan.Absorb(count)
        }

        return SpellAction.Result(Spell(target, held, plan), COST, listOf(ParticleSpray.burst(target.position(), 0.5)))
    }

    private sealed interface Plan {
        data object Merge : Plan
        data class Absorb(val count: Int) : Plan
    }

    private data class Spell(val target: ItemEntity, val held: ItemStack, val plan: Plan) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            if (!target.isAlive) return
            val (media, capacity) = when (plan) {
                Plan.Merge -> Pair(PhialHelper.media(target.item), PhialHelper.capacity(target.item)).also { target.kill() }
                is Plan.Absorb -> {
                    val remaining = target.item.copy()
                    val absorbed = extractMedia(remaining.split(plan.count), drainForBatteries = true)
                    if (remaining.isEmpty) target.kill() else target.item = remaining
                    Pair(absorbed, absorbed)
                }
            }
            PhialHelper.set(held, PhialHelper.media(held) + media, PhialHelper.capacity(held) + capacity)
        }
    }
}
