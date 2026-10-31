package com.mirabiliahexica.items

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * A dreamer's notes, found as treasure. Carrying one unlocks an entry of the Hex notebook (an advancement on
 * picking it up, which the entry requires); the item itself does nothing else.
 */
class ItemLoreNotes(properties: Properties) : Item(properties) {
    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip += Component.translatable("$descriptionId.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
    }

    override fun isFoil(stack: ItemStack) = true
}
