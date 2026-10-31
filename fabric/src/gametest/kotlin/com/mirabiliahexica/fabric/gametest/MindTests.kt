package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import com.mirabiliahexica.casting.iota.StringIota
import com.mirabiliahexica.config.Tuning
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.RegistrarEntry
import com.mirabiliahexica.strings.ChatMemory
import com.mirabiliahexica.strings.MindPalace
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3

/** Echo Purification and the mind palace. */
class MindTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun echoReadsTheCastersOwnChat(helper: GameTestHelper) {
        val player = player(helper)
        val other = player(helper)
        for (i in 1..40) ChatMemory.remember(player, "message $i")
        ChatMemory.remember(other, "someone else talking")
        check(read(player, MirabiliaActions.CHAT_ECHO!!, DoubleIota(0.0)) == "message 40") { "the latest message is wrong" }
        check(read(player, MirabiliaActions.CHAT_ECHO!!, DoubleIota(2.0)) == "message 38") { "two back is wrong" }
        // Only the last few dozen are kept
        val history = Tuning.Strings.chatHistory
        check(read(player, MirabiliaActions.CHAT_ECHO!!, DoubleIota(history - 1.0)) == "message ${41 - history}") { "the oldest kept message is wrong" }
        check(read(player, MirabiliaActions.CHAT_ECHO!!, DoubleIota(history.toDouble())) == null) { "kept more than $history messages" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun theMindPalaceKeepsStringsByRoom(helper: GameTestHelper) {
        val player = player(helper)
        check(read(player, MirabiliaActions.PALACE_SIZE!!) == "0") { "a new palace isn't empty" }
        cast(player, MirabiliaActions.MEMORY_WRITE!!, StringIota("the way home"), DoubleIota(3.0))
        cast(player, MirabiliaActions.MEMORY_WRITE!!, StringIota("a name"), DoubleIota(0.0))
        check(read(player, MirabiliaActions.MEMORY_READ!!, DoubleIota(3.0)) == "the way home") { "room 3 doesn't hold what was put there" }
        check(read(player, MirabiliaActions.MEMORY_READ!!, DoubleIota(1.0)) == null) { "an empty room isn't Null" }
        check(read(player, MirabiliaActions.PALACE_SIZE!!) == "2") { "two rooms filled, but the size says otherwise" }
        cast(player, MirabiliaActions.MEMORY_WRITE!!, NullIota(), DoubleIota(3.0))
        check(read(player, MirabiliaActions.MEMORY_READ!!, DoubleIota(3.0)) == null && MindPalace.size(player) == 1) { "Null didn't empty the room" }
        // Past the last room, and a non-string, are refused
        check(cast(player, MirabiliaActions.MEMORY_WRITE!!, StringIota("x"), DoubleIota(MindPalace.rooms.toDouble())) == ResolvedPatternType.ERRORED) { "wrote past the last room" }
        check(cast(player, MirabiliaActions.MEMORY_WRITE!!, DoubleIota(5.0), DoubleIota(1.0)) == ResolvedPatternType.ERRORED) { "kept a number" }
        // Each player has their own
        val other = player(helper)
        check(read(other, MirabiliaActions.MEMORY_READ!!, DoubleIota(0.0)) == null) { "another player read my palace" }
        helper.succeed()
    }

    private fun cast(player: ServerPlayer, entry: RegistrarEntry<ActionRegistryEntry>, vararg stack: Iota): ResolvedPatternType {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = stack.toList())
        return vm.queueExecuteAndWrapIota(PatternIota(entry.value.prototype), player.serverLevel()).resolutionType
    }

    /** What the pattern leaves on top, as text (a number plainly), or null for Null. */
    private fun read(player: ServerPlayer, entry: RegistrarEntry<ActionRegistryEntry>, vararg stack: Iota): String? {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = stack.toList())
        vm.queueExecuteAndWrapIota(PatternIota(entry.value.prototype), player.serverLevel())
        return when (val top = vm.image.stack.lastOrNull()) {
            is StringIota -> top.string
            is DoubleIota -> top.double.toInt().toString()
            else -> null
        }
    }

    private fun player(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(1.5, 2.0, 1.5)).let { player.moveTo(it.x, it.y, it.z) }
        return player
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
