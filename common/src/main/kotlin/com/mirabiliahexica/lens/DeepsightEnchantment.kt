package com.mirabiliahexica.lens

import com.mirabiliahexica.registry.MirabiliaItems
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.EnchantmentCategory

/**
 * Deepsight: lets an imbued Greater Lens show its labels through walls (see [LensVisibility]). Only the Greater
 * Lens takes it, by anvil. It's a treasure of the dream: never offered by the enchanting table or by librarians,
 * found only as a book in Dream Shrines.
 */
class DeepsightEnchantment : Enchantment(
    Rarity.VERY_RARE,
    // Only nominal: as a treasure the category never decides where it goes; canEnchant does
    EnchantmentCategory.ARMOR_HEAD,
    arrayOf(EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND),
) {
    override fun canEnchant(stack: ItemStack) = stack.`is`(MirabiliaItems.GREATER_LENS.value)

    override fun isTreasureOnly() = true

    override fun isTradeable() = false

    override fun isDiscoverable() = false

    override fun getMinCost(level: Int) = 25

    override fun getMaxCost(level: Int) = 50
}
