package com.mirabiliahexica.items

import at.petrak.hexcasting.common.items.ItemLens
import at.petrak.hexcasting.common.lib.HexAttributes
import com.google.common.collect.HashMultimap
import com.mirabiliahexica.lens.LensImbuement
import com.mirabiliahexica.MirabiliaConfig
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import com.google.common.collect.Multimap
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * A stronger Scrying Lens: same slots (head, either hand, Trinkets/Curios face slot) and the same scrying
 * sight, but much more grid zoom, so far larger hexes fit on screen. It can also be imbued with a hex that draws
 * labels over the wearer's screen (`LensImbuement`).
 */
class ItemGreaterLens(properties: Properties) : ItemLens(properties) {
    override fun getDefaultAttributeModifiers(slot: EquipmentSlot): Multimap<Attribute, AttributeModifier> {
        val out = HashMultimap.create(super.getDefaultAttributeModifiers(slot))
        // Swap the Scrying Lens zoom that ItemLens adds for ours
        out.remove(HexAttributes.GRID_ZOOM, ItemLens.GRID_ZOOM)
        if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            out.putAll(lensModifiers())
        }
        return out
    }

    override fun getHexBaubleAttrs(stack: ItemStack): Multimap<Attribute, AttributeModifier> = lensModifiers()

    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, level, tooltip, flag)
        if (LensImbuement.isImbued(stack)) {
            tooltip += Component.translatable("item.mirabilia.greater_lens.imbued", LensImbuement.hexSize(stack))
                .withStyle(ChatFormatting.LIGHT_PURPLE)
        }
    }

    /** Glints when imbued. */
    override fun isFoil(stack: ItemStack) = super.isFoil(stack) || LensImbuement.isImbued(stack)

    companion object {
        /**
         * +100% of base zoom (Scrying Lens: +33%), so grid zoom 2.0 on its own. Its own UUID, so wearing a
         * Scrying Lens as well stacks (2.33); the attribute is capped at 4.0 either way.
         */
        val GRID_ZOOM by lazy {
            AttributeModifier(
            UUID.fromString("6c2a8f0e-3d51-4b8e-9a77-2f4b9c1d5e10"),
            "Greater Lens Zoom", MirabiliaConfig.greaterLensZoom, AttributeModifier.Operation.MULTIPLY_BASE,
            )
        }

        private fun lensModifiers(): Multimap<Attribute, AttributeModifier> = HashMultimap.create<Attribute, AttributeModifier>().apply {
            put(HexAttributes.GRID_ZOOM, GRID_ZOOM)
            // Same modifier as the Scrying Lens on purpose: sight is on/off, it shouldn't stack
            put(HexAttributes.SCRY_SIGHT, ItemLens.SCRY_SIGHT)
        }
    }
}
