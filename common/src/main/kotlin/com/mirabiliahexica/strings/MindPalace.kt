package com.mirabiliahexica.strings

import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.config.Tuning
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Each player's mind palace: numbered rooms ([rooms] of them) each holding a string, kept with the world, so they
 * outlast deaths, dreams and restarts.
 */
object MindPalace {
    val rooms get() = Tuning.Strings.palaceRooms

    fun read(player: ServerPlayer, room: Int): String? = Data.get(player.server).palaces[player.uuid]?.get(room)

    fun write(player: ServerPlayer, room: Int, text: String?) {
        val data = Data.get(player.server)
        val mine = data.palaces.getOrPut(player.uuid, ::HashMap)
        if (text == null) mine.remove(room) else mine[room] = text
        data.setDirty()
    }

    /** How many rooms hold something. */
    fun size(player: ServerPlayer): Int = Data.get(player.server).palaces[player.uuid]?.size ?: 0

    class Data : SavedData() {
        val palaces = HashMap<UUID, HashMap<Int, String>>()

        override fun save(tag: CompoundTag): CompoundTag {
            tag.put("players", ListTag().apply {
                palaces.forEach { (owner, rooms) ->
                    add(CompoundTag().apply {
                        putUUID("owner", owner)
                        put("rooms", ListTag().apply {
                            rooms.forEach { (room, text) -> add(CompoundTag().apply { putInt("room", room); putString("text", text) }) }
                        })
                    })
                }
            })
            return tag
        }

        companion object {
            fun get(server: MinecraftServer): Data = server.overworld().dataStorage.computeIfAbsent(::load, ::Data, "${Mirabilia.MODID}_mind_palaces")

            private fun load(tag: CompoundTag) = Data().apply {
                for (p in tag.getList("players", Tag.TAG_COMPOUND.toInt())) {
                    p as CompoundTag
                    val rooms = HashMap<Int, String>()
                    for (r in p.getList("rooms", Tag.TAG_COMPOUND.toInt())) (r as CompoundTag).let { rooms[it.getInt("room")] = it.getString("text") }
                    palaces[p.getUUID("owner")] = rooms
                }
            }
        }
    }
}
