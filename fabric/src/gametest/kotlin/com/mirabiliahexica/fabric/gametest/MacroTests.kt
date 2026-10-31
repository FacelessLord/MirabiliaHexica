package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.hex.HexActions
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.macro.Macros
import com.mirabiliahexica.registry.MirabiliaActions
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3

class MacroTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mirabilia_ambient")
    fun aMacroRunsItsHexForItsOwnerAlone(helper: GameTestHelper) {
        val player = player(helper)
        // A macro that pushes 2 and 3 and adds them
        val hex = ListIota(listOf(PatternIota(number(2)), PatternIota(number(3)), PatternIota(HexActions.ADD.prototype)))
        val vm = cast(player, listOf(PatternIota(SHORT), hex), MirabiliaActions.MACRO_BIND.value.prototype)
        check(vm.second == ResolvedPatternType.EVALUATED) { "binding failed: ${vm.second}" }
        check(Macros.of(player).containsKey(SHORT.anglesSignature())) { "the macro wasn't kept" }
        check(MirabiliaAdvancements.has(player, "short_speech")) { "no Short Speech" }

        // Drawing it runs the hex, for one dust. (Mock players are always creative and pay nothing from their
        // pockets, so the charge is read from the quarter of it that settles in the air.)
        val chunk = ChunkPos(player.blockPosition())
        AmbientMedia.set(helper.level, chunk, 0)
        val run = cast(player, listOf(), SHORT)
        check(run.second == ResolvedPatternType.EVALUATED) { "drawing the macro gave ${run.second}" }
        check((run.first.image.stack.lastOrNull() as? DoubleIota)?.double == 5.0) { "the macro left ${run.first.image.stack}" }
        check(AmbientMedia.get(helper.level, chunk) == DUST / 4) { "a call was charged ${AmbientMedia.get(helper.level, chunk) * 4.0 / DUST} dust, expected 1" }
        AmbientMedia.set(helper.level, chunk, 0)

        // Someone else drawing it gets nothing
        val other = player(helper)
        val stranger = cast(other, listOf(), SHORT)
        check(stranger.second == ResolvedPatternType.INVALID) { "another player drew my macro: ${stranger.second}" }

        // Reflection lists it; Release forgets it
        val listed = cast(player, listOf(), MirabiliaActions.MACRO_LIST.value.prototype).first.image.stack.last() as ListIota
        check(listed.list.toList().map { (it as PatternIota).pattern.anglesSignature() } == listOf(SHORT.anglesSignature())) { "Macro Reflection gave $listed" }
        cast(player, listOf(PatternIota(SHORT)), MirabiliaActions.MACRO_RELEASE.value.prototype)
        check(Macros.of(player).isEmpty()) { "Release didn't forget it" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun takenPatternsAndTheLimitAreRefused(helper: GameTestHelper) {
        val player = player(helper)
        val hex = ListIota(listOf())
        // A pattern of the art, and a number literal
        for (taken in listOf(HexActions.ADD.prototype, number(7))) {
            check(cast(player, listOf(PatternIota(taken), hex), MirabiliaActions.MACRO_BIND.value.prototype).second == ResolvedPatternType.ERRORED) {
                "bound ${taken.anglesSignature()}, which already means something"
            }
        }
        // Four at first; a fifth is refused, until a milestone raises the limit
        val free = FREE.map { HexPattern.fromAngles(it, HexDir.EAST) }
        free.take(4).forEach { bind(player, it, hex) }
        check(Macros.of(player).size == 4) { "only ${Macros.of(player).size} of four bound" }
        check(cast(player, listOf(PatternIota(free[4]), hex), MirabiliaActions.MACRO_BIND.value.prototype).second == ResolvedPatternType.ERRORED) { "a fifth macro at the start" }
        // Rebinding one already held is fine
        bind(player, free[0], hex)
        var expected = Macros.baseLimit
        for ((milestone, more) in Macros.milestones) {
            val advancement = player.server.advancements.getAdvancement(milestone)!!
            advancement.criteria.keys.forEach { player.advancements.award(advancement, it) }
            expected += more
            check(Macros.limit(player) == expected) { "limit ${Macros.limit(player)} after $milestone, expected $expected" }
        }
        free.drop(4).forEach { bind(player, it, hex) }
        check(Macros.of(player).size == 8) { "only ${Macros.of(player).size} of eight bound" }
        check(MirabiliaAdvancements.has(player, "full_vocabulary")) { "eight macros, but no Full Vocabulary" }
        helper.succeed()
    }

    private fun bind(player: ServerPlayer, pattern: HexPattern, hex: ListIota) {
        val result = cast(player, listOf(PatternIota(pattern), hex), MirabiliaActions.MACRO_BIND.value.prototype).second
        check(result == ResolvedPatternType.EVALUATED) { "couldn't bind ${pattern.anglesSignature()}: $result" }
    }

    private fun cast(player: ServerPlayer, stack: List<Iota>, pattern: HexPattern): Pair<CastingVM, ResolvedPatternType> {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = stack)
        val view = vm.queueExecuteAndWrapIota(PatternIota(pattern), player.serverLevel())
        return vm to view.resolutionType
    }

    /** Numerical Reflection for a small whole number. */
    private fun number(n: Int) = HexPattern.fromAngles("aqaa" + "w".repeat(n), HexDir.EAST)

    private fun player(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.CREATIVE)
        helper.absoluteVec(Vec3(1.5, 2.0, 1.5)).let { player.moveTo(it.x, it.y, it.z) }
        return player
    }

    private companion object {
        val SHORT: HexPattern = HexPattern.fromAngles("qeq", HexDir.EAST)
        /** Shapes no pattern uses (checked against Hex's and ours). */
        val FREE = listOf("qeq", "eqe", "qeqe", "eqeq", "qeqeq", "wqeq", "weqe", "qqeq")
        const val DUST = MediaConstants.DUST_UNIT
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
