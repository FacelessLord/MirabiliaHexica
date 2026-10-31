package com.mirabiliahexica.imbue

import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes
import com.mirabiliahexica.lens.LensImbuement
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

/**
 * The patterns of an imbued item's hex, in order, those inside nested lists included, read straight from its data
 * (no world needed, so it works on the client, where they are drawn round whoever wears or holds the item).
 * At most [MAX], so a vast hex can't flood the screen.
 */
object ImbuedPatterns {
    const val MAX = 64

    private val PATTERN_TYPE = HexIotaTypes.REGISTRY.getKey(HexIotaTypes.PATTERN).toString()
    private val LIST_TYPE = HexIotaTypes.REGISTRY.getKey(HexIotaTypes.LIST).toString()

    fun of(stack: ItemStack): List<HexPattern> {
        val list = stack.tag?.getCompound(LensImbuement.TAG_HEX)?.getList(HexIotaTypes.KEY_DATA, Tag.TAG_COMPOUND.toInt()) ?: return emptyList()
        val out = ArrayList<HexPattern>()
        collect(list, out)
        return out
    }

    private fun collect(list: ListTag, out: MutableList<HexPattern>) {
        for (entry in list) {
            if (out.size >= MAX) return
            entry as CompoundTag
            when (entry.getString(HexIotaTypes.KEY_TYPE)) {
                PATTERN_TYPE -> out += HexPattern.fromNBT(entry.getCompound(HexIotaTypes.KEY_DATA))
                LIST_TYPE -> collect(entry.getList(HexIotaTypes.KEY_DATA, Tag.TAG_COMPOUND.toInt()), out)
            }
        }
    }
}
