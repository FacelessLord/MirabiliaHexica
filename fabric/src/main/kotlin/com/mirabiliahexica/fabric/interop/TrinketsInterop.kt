package com.mirabiliahexica.fabric.interop

import at.petrak.hexcasting.common.items.HexBaubleItem
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.registry.MirabiliaItems
import dev.emi.trinkets.api.SlotReference
import dev.emi.trinkets.api.Trinket
import dev.emi.trinkets.api.TrinketEnums
import dev.emi.trinkets.api.TrinketsApi
import dev.emi.trinkets.api.event.TrinketDropCallback
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Trinkets support. Hex Casting registers a Trinket for every [HexBaubleItem] that exists when *it* initializes, which may be
 * before our items do, so register ours ourselves. (Registering twice is harmless: the last one wins.)
 * On Forge, Hex attaches the Curios capability lazily, so nothing like this is needed there.
 */
object TrinketsInterop {
    fun init() {
        // Nothing is lost to the dream: a player who dies dreaming keeps what's in their trinket slots too
        TrinketDropCallback.EVENT.register { rule, _, _, entity ->
            if (entity is Player && Dream.isDreaming(entity)) TrinketEnums.DropRule.KEEP else rule
        }
        for (entry in MirabiliaItems.entries) {
            val bauble = entry.value as? HexBaubleItem ?: continue
            TrinketsApi.registerTrinket(entry.value, object : Trinket {
                override fun getModifiers(stack: ItemStack, slot: SlotReference, entity: LivingEntity, uuid: UUID):
                    com.google.common.collect.Multimap<Attribute, AttributeModifier> {
                    val map = super.getModifiers(stack, slot, entity, uuid)
                    map.putAll(bauble.getHexBaubleAttrs(stack))
                    return map
                }
            })
        }
    }
}
