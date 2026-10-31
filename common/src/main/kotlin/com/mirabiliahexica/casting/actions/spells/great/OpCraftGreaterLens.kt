package com.mirabiliahexica.casting.actions.spells.great

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.registry.MirabiliaItems
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.config.Tuning

/**
 * Craft Greater Lens (great spell): nothing → nothing. Turns the Scrying Lens in the caster's other hand into a
 * Greater Lens. This is the only way to make one.
 */
object OpCraftGreaterLens : SpellAction {
    override val argc = 0

    val COST get() = Tuning.Spells.greaterLensCost

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val (lens, hand) = env.getHeldItemToOperateOn { it.`is`(HexItems.SCRYING_LENS) }
            ?: throw MishapBadOffhandItem.of(ItemStack.EMPTY.copy(), "mirabilia.scrying_lens")
        if (lens.count != 1) throw MishapBadOffhandItem.of(lens, "only_one")
        return SpellAction.Result(Spell(lens, hand), COST, listOf(ParticleSpray.burst(env.mishapSprayPos(), 1.0)))
    }

    private data class Spell(val lens: ItemStack, val hand: InteractionHand?) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            env.replaceItem({ it === lens }, ItemStack(MirabiliaItems.GREATER_LENS.value), hand)
        }
    }
}
