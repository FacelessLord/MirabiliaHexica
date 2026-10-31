package com.mirabiliahexica.casting.actions.spells

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.mishaps.MishapBadItem
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder
import at.petrak.hexcasting.common.lib.HexItems
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.config.Tuning

/** Shared rules for the Phial spells. A Phial's capacity is `hexcasting:start_media`, set when it is made. */
object PhialHelper {
    /** No Phial can grow past this: four stacks' worth of Charged Amethyst. */
    val MAX_CAPACITY get() = Tuning.Spells.phialMaxCapacity

    fun isPhial(stack: ItemStack) = stack.`is`(HexItems.BATTERY)

    fun media(stack: ItemStack) = HexItems.BATTERY.getMedia(stack)

    fun capacity(stack: ItemStack) = HexItems.BATTERY.getMaxMedia(stack)

    fun set(stack: ItemStack, media: Long, capacity: Long) {
        ItemMediaHolder.withMedia(stack, media, capacity)
    }

    /** Mishap for a Phial that can't grow any further. */
    fun tooBig(target: ItemEntity) = MishapBadItem(target,
        Component.translatable("mirabilia.mishap.bad_item.phial_room", MAX_CAPACITY / MediaConstants.DUST_UNIT))

    /** The single Phial in the caster's other hand (or wherever the environment says to look), else a mishap. */
    fun heldPhial(env: CastingEnvironment): ItemStack {
        val held = env.getHeldItemToOperateOn(::isPhial)?.stack
            ?: throw MishapBadOffhandItem.of(ItemStack.EMPTY.copy(), "mirabilia.phial")
        if (held.count != 1) throw MishapBadOffhandItem.of(held, "only_one")
        return held
    }
}
