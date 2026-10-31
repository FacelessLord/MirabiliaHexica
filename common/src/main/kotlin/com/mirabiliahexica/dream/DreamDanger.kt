package com.mirabiliahexica.dream

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.misc.DiscoveryHandlers
import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.imbue.Imbuement
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import com.mirabiliahexica.config.Tuning

/**
 * Media draws the dream's attention. The more a dreamer carries (Phials, amethyst of every kind, the charge in
 * cyphers, trinkets and artifacts, the stores of imbued lenses, armor and tools), the sooner Lost Dreamers and the
 * Glass Man find them: their [factor] is 1 + carried dust ÷ [DUST_PER_STEP], at most [MAX_FACTOR]. Counted every
 * [RECOUNT] ticks.
 */
object DreamDanger {
    val DUST_PER_STEP get() = Tuning.Dream.dangerDustPerStep
    val MAX_FACTOR get() = Tuning.Dream.dangerMax
    const val RECOUNT = 40

    private val factors = HashMap<UUID, Float>()

    fun factor(player: ServerPlayer): Float {
        if (player.tickCount % RECOUNT == 0 || player.uuid !in factors) factors[player.uuid] = factorFor(carried(player))
        return factors.getValue(player.uuid)
    }

    fun factorFor(media: Long) = minOf(MAX_FACTOR, 1f + media.toFloat() / (DUST_PER_STEP * MediaConstants.DUST_UNIT))

    /** All the media [player] carries, in Hex's units. */
    fun carried(player: ServerPlayer): Long {
        val stacks = player.inventory.items + player.inventory.armor + player.inventory.offhand + DiscoveryHandlers.collectExtraEquipments(player)
        return stacks.filter { !it.isEmpty }.sumOf { stack ->
            val held = HexAPI.instance().findMediaHolder(stack)?.media ?: 0L
            held + if (Imbuement.isImbued(stack)) Imbuement.media(stack) else 0L
        }
    }

    fun forget(player: ServerPlayer) {
        factors.remove(player.uuid)
    }
}
