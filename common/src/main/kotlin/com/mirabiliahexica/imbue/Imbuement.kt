package com.mirabiliahexica.imbue

import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.lens.LensImbuement
import com.mirabiliahexica.lens.LensSight
import com.mirabiliahexica.wards.ArmorWard
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.config.Tuning

/**
 * Imbued items: a hex stored in a lens ([LensImbuement]), a piece of armor or a tool, with a store of media of
 * its own, sized by the amethyst used to imbue it. They pay from that store, never from the wearer (though a
 * wellspring the hex binds, or the item is tethered to, pays first), and simply stop when it runs dry;
 * Recharge Imbued Item refills it.
 *
 * * **Armor** runs its hex as the wearer every [ArmorImbuement.PERIOD] ticks while worn, each piece on its own.
 *   Nothing is shown; it's for what the hex does.
 * * **Tools** still work as tools, but using one (right-click) casts its hex once. Bending a tool to magic wears
 *   it out: each cast costs durability, one point plus one per [DUST_PER_WEAR] dust the hex spent, whatever
 *   paid for it.
 */
object Imbuement {
    val DUST_PER_WEAR get() = Tuning.Imbuing.dustPerWear

    private const val TAG_MEDIA = "${Mirabilia.MODID}:media"
    private const val TAG_CAPACITY = "${Mirabilia.MODID}:capacity"

    fun canImbueLens(stack: ItemStack) = LensSight.isLens(stack)

    fun canImbueArmor(stack: ItemStack) = ArmorWard.canWard(stack) && !LensSight.isLens(stack)

    fun canImbueTool(stack: ItemStack) = stack.isDamageableItem && !ArmorWard.canWard(stack) && !LensSight.isLens(stack)

    /** An imbued lens, armor piece or tool. */
    fun isImbued(stack: ItemStack) = LensImbuement.isImbued(stack)

    /** Stores [hex] with a full store of [capacity]; an empty hex clears the item, store and all. */
    fun imbue(stack: ItemStack, hex: List<Iota>, capacity: Long) {
        LensImbuement.imbue(stack, hex)
        if (hex.isEmpty()) {
            stack.removeTagKey(TAG_MEDIA)
            stack.removeTagKey(TAG_CAPACITY)
            return
        }
        stack.orCreateTag.putLong(TAG_CAPACITY, capacity)
        setMedia(stack, capacity)
    }

    fun capacity(stack: ItemStack): Long = stack.tag?.getLong(TAG_CAPACITY) ?: 0

    fun media(stack: ItemStack): Long = stack.tag?.getLong(TAG_MEDIA) ?: 0

    fun setMedia(stack: ItemStack, media: Long) = stack.orCreateTag.putLong(TAG_MEDIA, media.coerceIn(0, capacity(stack)))

    fun hex(stack: ItemStack, player: ServerPlayer): List<Iota>? {
        val tag = stack.tag?.getCompound(LensImbuement.TAG_HEX)?.takeIf { !it.isEmpty } ?: return null
        return (IotaType.deserialize(tag, player.serverLevel()) as? ListIota)?.list?.toList()
    }

    /**
     * Runs [stack]'s hex once as [player], paid from the stack (or a wellspring the hex binds). [upkeep] is
     * taken first; if even that can't be paid, nothing runs. Returns all the media the run cost, from any
     * source (upkeep included), or null if it didn't run.
     */
    fun cast(player: ServerPlayer, stack: ItemStack, hand: InteractionHand, upkeep: Long = 0): Long? =
        run(player, stack, hand, upkeep)?.first?.totalSpent

    /** As [cast], but hands back the finished run itself (for a lens, which reads what the hex left). */
    fun run(player: ServerPlayer, stack: ItemStack, hand: InteractionHand, upkeep: Long): Pair<ImbuedCastEnv, CastingVM>? {
        val hex = hex(stack, player) ?: return null
        val env = ImbuedCastEnv(player, stack, hand)
        // Attach the hooks (wards, the item's wellspring tether…) now, so even the upkeep goes through them
        env.triggerCreateEvent(CompoundTag())
        if (env.extractMedia(upkeep, true) > 0 || (upkeep == 0L && media(stack) <= 0)) return null
        env.extractMedia(upkeep, false)
        val vm = CastingVM.empty(env)
        try {
            vm.queueExecuteAndWrapIotas(hex, player.serverLevel())
        } catch (e: Exception) {
            Mirabilia.LOGGER.warn("Imbued {} of {} crashed", stack.item, player.gameProfile.name, e)
            return null
        }
        return env to vm
    }

    fun hexSize(stack: ItemStack) = LensImbuement.hexSize(stack)
}
