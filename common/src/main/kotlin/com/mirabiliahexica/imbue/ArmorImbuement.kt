package com.mirabiliahexica.imbue

import at.petrak.hexcasting.api.misc.MediaConstants
import dev.architectury.event.CompoundEventResult
import dev.architectury.event.events.common.InteractionEvent
import dev.architectury.event.events.common.TickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning

/** Runs worn imbued armor, and casts imbued tools on use. See [Imbuement]. */
object ArmorImbuement {
    val PERIOD get() = Tuning.Imbuing.armorPeriod
    /** A tenth of a dust per run, as for a lens: twenty seconds a dust. */
    val UPKEEP get() = Tuning.Imbuing.armorUpkeep
    /** Ticks before an imbued tool can cast again. */
    val TOOL_COOLDOWN get() = Tuning.Imbuing.toolCooldown

    private val ARMOR = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    fun init() {
        TickEvent.SERVER_POST.register(::tick)
        InteractionEvent.RIGHT_CLICK_ITEM.register { player, hand ->
            val stack = player.getItemInHand(hand)
            when {
                !Imbuement.isImbued(stack) || !Imbuement.canImbueTool(stack) -> CompoundEventResult.pass()
                player !is ServerPlayer -> CompoundEventResult.interruptTrue(stack)
                else -> {
                    useTool(player, hand)
                    CompoundEventResult.interruptTrue(player.getItemInHand(hand))
                }
            }
        }
    }

    private fun tick(server: MinecraftServer) {
        if (server.tickCount % PERIOD != 0 || !Features.imbueArmor) return
        for (player in server.playerList.players) runArmor(player)
    }

    /** Runs each worn imbued armor piece's hex once. Returns how many ran. */
    fun runArmor(player: ServerPlayer): Int = ARMOR.count { slot ->
        val stack = player.getItemBySlot(slot)
        // A lens worn on the head runs as a lens (LensImbuement), not as armor
        Imbuement.isImbued(stack) && Imbuement.canImbueArmor(stack) && Imbuement.cast(player, stack, InteractionHand.MAIN_HAND, UPKEEP) != null
    }

    /**
     * Casts the imbued tool in [hand] once, wearing it down for the effort. Returns false if it couldn't (out of
     * media, or still cooling down).
     */
    fun useTool(player: ServerPlayer, hand: InteractionHand): Boolean {
        if (!Features.imbueTool) return false
        val stack = player.getItemInHand(hand)
        if (player.cooldowns.isOnCooldown(stack.item)) return false
        val spent = Imbuement.cast(player, stack, hand)
        if (spent == null) {
            player.displayClientMessage(Component.translatable("mirabilia.imbued.empty"), true)
            return false
        }
        player.cooldowns.addCooldown(stack.item, TOOL_COOLDOWN)
        val wear = 1 + (spent / (Imbuement.DUST_PER_WEAR * MediaConstants.DUST_UNIT)).toInt()
        stack.hurtAndBreak(wear, player) { it.broadcastBreakEvent(hand) }
        return true
    }
}
