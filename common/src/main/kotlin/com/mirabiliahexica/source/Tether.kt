package com.mirabiliahexica.source

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.env.PackagedItemCastEnv
import at.petrak.hexcasting.common.items.magic.ItemPackagedHex
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.lens.LensSight
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * Tethers: a wellspring bound to an item rather than to one hex. Whenever a tethered lens, imbued armor piece or
 * tool, cypher, trinket or artifact runs its hex, that hex starts out bound to the item's wellspring, which pays
 * first, as long as it is within that cast's reach. Too far away, it gives nothing and the item pays the way it
 * otherwise would (its own store, the wearer's inventory…). A hex can still bind another wellspring itself.
 */
object Tether {
    private const val TAG = "${Mirabilia.MODID}:wellspring"

    /** What can hold a tether: anything that runs a hex of its own. */
    fun canTether(stack: ItemStack) =
        LensSight.isLens(stack) || Imbuement.isImbued(stack) || stack.item is ItemPackagedHex

    fun of(stack: ItemStack): MediaSource? =
        stack.tag?.takeIf { it.contains(TAG, Tag.TAG_COMPOUND.toInt()) }?.let { MediaSource.load(it.getCompound(TAG)) }

    fun set(stack: ItemStack, source: MediaSource?) {
        if (source == null) stack.removeTagKey(TAG) else stack.orCreateTag.put(TAG, source.save())
    }

    /** The item whose hex [env] is running, if it's one that can be tethered. */
    fun castingItem(env: CastingEnvironment): ItemStack? = when (env) {
        is ItemCastEnv -> env.castingItem
        is PackagedItemCastEnv -> (env.castingEntity as? Player)?.getItemInHand(env.castingHand)
        else -> null
    }
}

/** A casting environment running the hex stored in an item (a lens, imbued armor, an imbued tool). */
interface ItemCastEnv {
    val castingItem: ItemStack?
}
