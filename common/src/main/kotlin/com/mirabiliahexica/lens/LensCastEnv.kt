package com.mirabiliahexica.lens

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.eval.MishapEnvironment
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect
import at.petrak.hexcasting.api.pigment.FrozenPigment
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import com.mirabiliahexica.source.ItemCastEnv
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/**
 * Where an imbued lens's hex runs: as the wearer, with their ambit, paid from their inventory media (Phials,
 * amethyst…) but never by overcasting. It runs many times a minute in the background, so it is quiet: mishaps
 * don't hurt or disarm the wearer, and messages go to the action bar instead of chat.
 */
open class LensCastEnv(
    caster: ServerPlayer,
    private val hand: InteractionHand = InteractionHand.MAIN_HAND,
    /** The item whose hex this runs, for its wellspring [tether][com.mirabiliahexica.source.Tether]. */
    override val castingItem: ItemStack? = null,
) : PlayerBasedCastEnv(caster, hand), ItemCastEnv {
    /** The last mishap, if any, so the runner can tell a failed run from an empty one. */
    var mishapped = false
        private set

    override fun extractMediaEnvironment(cost: Long, simulate: Boolean): Long {
        // The game-mode ability rather than isCreative(): same for real players, and GameTest mock players
        // always claim isCreative()
        if (isCreativeMode) return 0
        return extractMediaFromInventory(cost, false, simulate)
    }

    override fun getCastingHand() = hand

    override fun getPigment(): FrozenPigment = HexAPI.instance().getColorizer(caster)

    override fun getMishapEnvironment(): MishapEnvironment = QuietMishapEnv(caster)

    override fun sendMishapMsgToPlayer(mishap: OperatorSideEffect.DoMishap) {
        mishapped = true
        mishap.mishap.errorMessageWithName(this, mishap.errorCtx)?.let { caster.displayClientMessage(it, true) }
    }

    override fun printMessage(message: Component) {
        caster.displayClientMessage(message, true)
    }

    /** A lens mishap only stops that run; it doesn't punish the wearer. */
    private class QuietMishapEnv(caster: ServerPlayer) : MishapEnvironment(caster.serverLevel(), caster) {
        override fun yeetHeldItemsTowards(targetPos: Vec3) {}
        override fun dropHeldItems() {}
        override fun drown() {}
        override fun damage(healthProportion: Float) {}
        override fun removeXp(amount: Int) {}
        override fun blind(ticks: Int) {}
    }
}
