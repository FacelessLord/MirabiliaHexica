package com.mirabiliahexica

import com.mirabiliahexica.dream.Dream
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import dev.architectury.event.events.common.CommandRegistrationEvent
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * `/mirabilia wake [players]`: wakes dreamers at once, as the Wake pattern would (for operators: a way out for a
 * stuck player, or for testing). Without players, wakes whoever runs it.
 */
object MirabiliaCommands {
    fun init() {
        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ -> register(dispatcher) }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(Mirabilia.MODID)
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("wake")
                        .executes { wake(it, listOf(it.source.playerOrException)) }
                        .then(Commands.argument("players", EntityArgument.players())
                            .executes { wake(it, EntityArgument.getPlayers(it, "players")) }),
                ),
        )
    }

    private fun wake(context: CommandContext<CommandSourceStack>, players: Collection<ServerPlayer>): Int {
        val dreamers = players.filter(Dream::isDreaming)
        for (player in dreamers) Dream.wake(player, Dream.Waking.OTHER)
        val source = context.source
        when {
            dreamers.isEmpty() -> source.sendFailure(Component.translatable("commands.mirabilia.wake.none"))
            dreamers.size == 1 -> source.sendSuccess({ Component.translatable("commands.mirabilia.wake.one", dreamers.first().displayName) }, true)
            else -> source.sendSuccess({ Component.translatable("commands.mirabilia.wake.many", dreamers.size) }, true)
        }
        return dreamers.size
    }
}
