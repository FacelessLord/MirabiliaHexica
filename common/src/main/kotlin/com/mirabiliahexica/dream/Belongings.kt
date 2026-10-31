package com.mirabiliahexica.dream

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerPlayer

/** What a player who dies dreaming would otherwise lose: their inventory and their experience. */
object Belongings {
    /** Takes it all off [player] (so nothing drops, not even experience orbs) and returns it. */
    fun take(player: ServerPlayer): CompoundTag {
        val tag = CompoundTag()
        tag.put("items", player.inventory.save(ListTag()))
        tag.putInt("level", player.experienceLevel)
        tag.putFloat("progress", player.experienceProgress)
        tag.putInt("total", player.totalExperience)
        player.inventory.clearContent()
        player.experienceLevel = 0
        player.experienceProgress = 0f
        player.totalExperience = 0
        return tag
    }

    fun give(player: ServerPlayer, tag: CompoundTag) {
        player.inventory.load(tag.getList("items", 10))
        player.experienceLevel = tag.getInt("level")
        player.experienceProgress = tag.getFloat("progress")
        player.totalExperience = tag.getInt("total")
    }
}
