package com.mirabiliahexica.strings

import com.mirabiliahexica.config.Tuning
import dev.architectury.event.EventResult
import dev.architectury.event.events.common.ChatEvent
import dev.architectury.event.events.common.PlayerEvent
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Each player's own recent chat messages, newest first, for Echo Purification: only their own, so a hex isn't
 * thrown off by other people talking. Kept in memory ([Tuning.Strings.chatHistory] each), not saved.
 */
object ChatMemory {
    private val messages = HashMap<UUID, ArrayDeque<String>>()

    fun init() {
        ChatEvent.RECEIVED.register { player, message ->
            if (player != null) remember(player, message.string)
            EventResult.pass()
        }
        PlayerEvent.PLAYER_QUIT.register { messages.remove(it.uuid) }
    }

    fun remember(player: ServerPlayer, message: String) {
        val mine = messages.getOrPut(player.uuid, ::ArrayDeque)
        mine.addFirst(message)
        while (mine.size > Tuning.Strings.chatHistory) mine.removeLast()
    }

    /** The message [back] places before the latest (0: the latest), or null. */
    fun message(player: ServerPlayer, back: Int): String? = messages[player.uuid]?.getOrNull(back)
}
