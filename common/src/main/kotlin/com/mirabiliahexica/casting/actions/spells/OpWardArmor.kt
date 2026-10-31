package com.mirabiliahexica.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem
import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.wards.ArmorWard
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.config.Tuning

/**
 * Ward Armor: nothing → nothing. Wards the unwarded armor piece in the caster's other hand; see
 * [com.mirabiliahexica.wards.Wards] for what wearing it does.
 */
object OpWardArmor : SpellAction {
    override val argc = 0

    val COST get() = Tuning.Spells.wardArmorCost

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val armor = env.getHeldItemToOperateOn { ArmorWard.canWard(it) && !ArmorWard.isWarded(it) }?.stack
            ?: throw MishapBadOffhandItem.of(ItemStack.EMPTY.copy(), "mirabilia.unwarded_armor")
        return SpellAction.Result(Spell(armor), COST, listOf(ParticleSpray.burst(env.mishapSprayPos(), 0.5)))
    }

    private data class Spell(val armor: ItemStack) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            ArmorWard.ward(armor)
        }
    }
}
