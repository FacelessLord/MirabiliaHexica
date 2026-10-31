package com.mirabiliahexica.lens

import at.petrak.hexcasting.api.misc.DiscoveryHandlers
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.registry.MirabiliaItems
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/** Where a lens counts as worn. Works on both sides. */
object LensSight {
    /** Every lens (Scrying or Greater) the player is wearing or holding where a lens works (head, hands, bauble slots). */
    fun lenses(player: Player): List<ItemStack> {
        val candidates = listOf(
            player.getItemBySlot(EquipmentSlot.HEAD),
            player.mainHandItem,
            player.offhandItem,
        ) + DiscoveryHandlers.collectExtraEquipments(player)
        return candidates.filter(::isLens)
    }

    fun isLens(stack: ItemStack) = stack.`is`(MirabiliaItems.GREATER_LENS.value) || stack.`is`(HexItems.SCRYING_LENS)
}
