package com.mirabiliahexica.registry

import com.mirabiliahexica.lens.DeepsightEnchantment
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.enchantment.Enchantment

object MirabiliaEnchantments : MirabiliaRegistrar<Enchantment>(Registries.ENCHANTMENT, { BuiltInRegistries.ENCHANTMENT }) {
    val DEEPSIGHT = register("deepsight") { DeepsightEnchantment() }
}
