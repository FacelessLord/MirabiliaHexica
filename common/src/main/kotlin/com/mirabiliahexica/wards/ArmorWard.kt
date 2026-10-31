package com.mirabiliahexica.wards

import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Equipable
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.config.Features

/** Warded armor: an NBT mark on any armor piece. Wearing one hides the wearer from other casters (see [Wards]). */
object ArmorWard {
    const val TAG = "mirabilia:warded"

    fun canWard(stack: ItemStack) = Equipable.get(stack)?.equipmentSlot?.type == EquipmentSlot.Type.ARMOR

    fun isWarded(stack: ItemStack) = stack.tag?.getBoolean(TAG) == true

    fun ward(stack: ItemStack) = stack.orCreateTag.putBoolean(TAG, true)

    fun protects(entity: LivingEntity) = Features.wardArmor && entity.armorSlots.any(::isWarded)
}
