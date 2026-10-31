package com.mirabiliahexica.imbue

import com.mirabiliahexica.lens.LensCastEnv
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack

/**
 * Where an imbued armor piece's or tool's hex runs: as the wearer (quietly, like a lens), but paid from the
 * item's own media instead of the wearer's: never their inventory, never their health. A wellspring the hex
 * binds pays first, as in any hex; when both run dry, the hex is short of media. The store is the item's, not the
 * wearer's, so it is spent even in creative mode.
 */
class ImbuedCastEnv(caster: ServerPlayer, private val stack: ItemStack, hand: InteractionHand) : LensCastEnv(caster, hand, stack) {
    /** Media taken from the item during this run. */
    var spent = 0L
        private set

    /** All the media this run's spells cost, whoever paid it (the item, or a wellspring the hex bound). */
    var totalSpent = 0L
        private set

    override fun extractMedia(cost: Long, simulate: Boolean): Long {
        val unpaid = super.extractMedia(cost, simulate)
        if (!simulate) totalSpent += (cost - unpaid).coerceAtLeast(0)
        return unpaid
    }

    override fun extractMediaEnvironment(cost: Long, simulate: Boolean): Long {
        val have = Imbuement.media(stack)
        val taken = minOf(have, cost)
        if (!simulate) {
            Imbuement.setMedia(stack, have - taken)
            spent += taken
        }
        return cost - taken
    }
}
