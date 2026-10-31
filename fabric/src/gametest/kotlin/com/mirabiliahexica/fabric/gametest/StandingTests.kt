package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.RegistrarEntry
import com.mirabiliahexica.standing.StandingSpell
import com.mirabiliahexica.standing.StandingSpells
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import net.minecraft.gametest.framework.BeforeBatch
import net.minecraft.gametest.framework.AfterBatch
import net.minecraft.server.level.ServerLevel
import com.mirabiliahexica.config.Features

class StandingTests {
    // Standing spells are off by default; these tests turn them on for their batch alone
    @BeforeBatch(batch = STANDING)
    fun switchOn(level: ServerLevel) {
        Features.standingSpells = true
    }

    @AfterBatch(batch = STANDING)
    fun switchOff(level: ServerLevel) {
        Features.standingSpells = false
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING, timeoutTicks = 60)
    fun standingSpellRunsItsHexAtItsAnchor(helper: GameTestHelper) {
        for (x in 0..4) for (z in 0..4) {
            helper.setBlock(x, 0, z, Blocks.STONE)
            helper.setBlock(x, 1, z, Blocks.STONE)
        }
        helper.setBlock(ANCHOR, Blocks.WATER)
        val player = caster(helper)

        // Hex: push 2, then Freeze. Each run starts with the anchor's centre on the stack: Freeze(anchor, 2).
        val hex = listOf(number2(), pattern(MirabiliaActions.FREEZE))
        castOk(player, listOf(ListIota(hex), anchorVec(helper), DoubleIota(30.0)), MirabiliaActions.STANDING_RAISE)
        check(spellAt(helper) != null) { "no standing spell after raising one" }
        // Nothing happens on the cast itself; the first run is on the next server tick
        helper.assertBlockPresent(Blocks.WATER, ANCHOR)

        helper.runAfterDelay(15) {
            helper.assertBlockPresent(Blocks.ICE, ANCHOR)
            val spell = spellAt(helper) ?: throw GameTestAssertException("spell ended too early")
            check(spell.media < 30 * DUST) { "upkeep isn't being paid" }
            StandingSpells.get(helper.level.server).remove(spell)
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING)
    fun standingSpellCanBeQueriedFedAndDispelled(helper: GameTestHelper) {
        val player = caster(helper)
        castOk(player, listOf(ListIota(listOf(number0())), anchorVec(helper), DoubleIota(10.0)), MirabiliaActions.STANDING_RAISE)

        // 10 dust at half a dust per second
        val remaining = castQuery(player, listOf(anchorVec(helper)), MirabiliaActions.STANDING_REMAINING)
        check(remaining is DoubleIota && abs(remaining.double - 20.0) < 1e-6) { "expected 20 s remaining, got $remaining" }

        castOk(player, listOf(anchorVec(helper), DoubleIota(5.0)), MirabiliaActions.STANDING_FEED)
        val fed = castQuery(player, listOf(anchorVec(helper)), MirabiliaActions.STANDING_REMAINING)
        check(fed is DoubleIota && abs(fed.double - 30.0) < 1e-6) { "expected 30 s after feeding, got $fed" }

        // A second spell can't share the block
        val again = cast(player, listOf(ListIota(listOf(number0())), anchorVec(helper), DoubleIota(1.0)), MirabiliaActions.STANDING_RAISE)
        check(again.first == ResolvedPatternType.ERRORED) { "raised two spells on one block" }

        castOk(player, listOf(anchorVec(helper)), MirabiliaActions.STANDING_DISPEL)
        check(spellAt(helper) == null) { "spell survived being dispelled" }
        val gone = castQuery(player, listOf(anchorVec(helper)), MirabiliaActions.STANDING_REMAINING)
        check(gone is NullIota) { "expected null for a dispelled spell, got $gone" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING, timeoutTicks = 80)
    fun standingSpellEndsWhenMediaRunsOut(helper: GameTestHelper) {
        val player = caster(helper)
        // 1 dust lasts 2 seconds (40 ticks)
        castOk(player, listOf(ListIota(listOf(number0())), anchorVec(helper), DoubleIota(1.0)), MirabiliaActions.STANDING_RAISE)
        helper.runAfterDelay(30) { check(spellAt(helper) != null) { "spell ended before its media ran out" } }
        helper.runAfterDelay(50) {
            check(spellAt(helper) == null) { "spell outlived its media" }
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING)
    fun standingSpellEndsOnMishap(helper: GameTestHelper) {
        val player = caster(helper)
        // Freeze with only the anchor on the stack: not enough arguments, so the first run mishaps
        castOk(player, listOf(ListIota(listOf(pattern(MirabiliaActions.FREEZE))), anchorVec(helper), DoubleIota(10.0)),
            MirabiliaActions.STANDING_RAISE)
        helper.runAfterDelay(5) {
            check(spellAt(helper) == null) { "spell kept running after its hex mishapped" }
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING, timeoutTicks = 40)
    fun standingSpellCanRideOnItsCaster(helper: GameTestHelper) {
        val player = caster(helper)
        castOk(player, listOf(ListIota(listOf(number0())), EntityIota(player), DoubleIota(5.0)), MirabiliaActions.STANDING_RAISE_ENTITY)
        val spells = StandingSpells.get(helper.level.server)
        check(spells.onEntity(player.uuid) != null) { "no spell riding on the caster" }

        // The caster names their own spell by entity
        val remaining = castQuery(player, listOf(EntityIota(player)), MirabiliaActions.STANDING_REMAINING)
        check(remaining is DoubleIota) { "couldn't query the caster's own spell, got $remaining" }

        helper.runAfterDelay(15) {
            val spell = spells.onEntity(player.uuid) ?: throw GameTestAssertException("caster-bound spell ended early")
            check(spell.media < 5 * DUST) { "caster-bound spell isn't paying upkeep (is the owner found online?)" }
            spells.remove(spell)
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING)
    fun ridingSpellEndsWhenOwnerDies(helper: GameTestHelper) {
        val player = caster(helper)
        castOk(player, listOf(ListIota(listOf(number0())), EntityIota(player), DoubleIota(5.0)), MirabiliaActions.STANDING_RAISE_ENTITY)
        castOk(player, listOf(ListIota(listOf(number0())), anchorVec(helper), DoubleIota(5.0)), MirabiliaActions.STANDING_RAISE)
        val spells = StandingSpells.get(helper.level.server)
        check(spells.onEntity(player.uuid) != null && spellAt(helper) != null) { "spells weren't raised" }

        // Creative players can still die to /kill-style damage
        player.kill()
        check(player.isDeadOrDying) { "test player didn't die" }
        check(spells.onEntity(player.uuid) == null) { "riding spell outlived its owner" }
        val blockSpell = spellAt(helper) ?: throw GameTestAssertException("block-bound spell ended with its owner")
        spells.remove(blockSpell)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING, timeoutTicks = 40)
    fun spellOnAMobRunsWithTheMobOnTheStackAndEndsWhenItDies(helper: GameTestHelper) {
        val player = caster(helper)
        val pig = helper.spawn(EntityType.PIG, Vec3(2.5, 2.0, 2.5))
        pig.setNoAi(true)
        // A harmless hex (push 0); the pig is on the stack underneath it each run
        castOk(player, listOf(ListIota(listOf(number0())), EntityIota(pig), DoubleIota(5.0)), MirabiliaActions.STANDING_RAISE_ENTITY)
        val spells = StandingSpells.get(helper.level.server)
        check(spells.onEntity(pig.uuid) != null) { "no spell riding on the pig" }

        // Another player's spell can't share the pig
        val again = cast(player, listOf(ListIota(listOf(number0())), EntityIota(pig), DoubleIota(1.0)), MirabiliaActions.STANDING_RAISE_ENTITY)
        check(again.first == ResolvedPatternType.ERRORED) { "raised two spells on one entity" }

        helper.runAfterDelay(15) {
            val spell = spells.onEntity(pig.uuid) ?: throw GameTestAssertException("spell on the pig ended early")
            check(spell.media < 5 * DUST) { "spell on the pig isn't running" }
            pig.kill()
            check(spells.onEntity(pig.uuid) == null) { "spell outlived the pig" }
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING, timeoutTicks = 40)
    fun spellOnAnItemEndsWhenTheItemIsDestroyed(helper: GameTestHelper) {
        val player = caster(helper)
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        val item = ItemEntity(helper.level, pos.x, pos.y, pos.z, ItemStack(Items.STICK))
        item.setNeverPickUp()
        helper.level.addFreshEntity(item)
        castOk(player, listOf(ListIota(listOf(number0())), EntityIota(item), DoubleIota(5.0)), MirabiliaActions.STANDING_RAISE_ENTITY)
        val spells = StandingSpells.get(helper.level.server)

        helper.runAfterDelay(5) {
            check(spells.onEntity(item.uuid) != null) { "spell on the item ended early" }
            // Not a death, so no death event: the next tick has to notice the item is gone for good
            item.discard()
        }
        helper.runAfterDelay(10) {
            check(spells.onEntity(item.uuid) == null) { "spell outlived the destroyed item" }
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = STANDING)
    fun standingSpellSurvivesSaveAndLoad(helper: GameTestHelper) {
        val anchor = StandingSpell.Anchor.Block(helper.level.dimension(), helper.absolutePos(ANCHOR))
        val original = StandingSpell.create(java.util.UUID.randomUUID(), anchor, listOf(number2(), pattern(MirabiliaActions.FREEZE)), 1234)
        val copy = StandingSpell.load(original.save())
        check(copy.id == original.id && copy.owner == original.owner && copy.anchor == original.anchor && copy.media == 1234L) {
            "fields changed in a save/load round trip"
        }
        check(copy.hex(helper.level).size == 2) { "hex changed in a save/load round trip" }
        helper.succeed()
    }

    private fun spellAt(helper: GameTestHelper): StandingSpell? =
        StandingSpells.get(helper.level.server).atBlock(helper.level.dimension(), helper.absolutePos(ANCHOR))

    private fun anchorVec(helper: GameTestHelper) = Vec3Iota(helper.absoluteVec(Vec3.atCenterOf(ANCHOR)))

    /** Numerical Reflection: "aqaa" then one "w" per +1. */
    private fun number0() = PatternIota(HexPattern.fromAngles("aqaa", HexDir.EAST))
    private fun number2() = PatternIota(HexPattern.fromAngles("aqaaww", HexDir.EAST))

    private fun pattern(entry: RegistrarEntry<ActionRegistryEntry>) = PatternIota(entry.value.prototype)

    private fun caster(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.CREATIVE)
        val pos = helper.absoluteVec(Vec3(2.5, 4.0, 2.5))
        player.teleportTo(pos.x, pos.y, pos.z)
        return player
    }

    private fun cast(player: ServerPlayer, args: List<Iota>, entry: RegistrarEntry<ActionRegistryEntry>): Pair<ResolvedPatternType, List<Iota>> {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = args)
        val view = vm.queueExecuteAndWrapIota(pattern(entry), player.serverLevel())
        return view.resolutionType to vm.image.stack
    }

    private fun castOk(player: ServerPlayer, args: List<Iota>, entry: RegistrarEntry<ActionRegistryEntry>) {
        val (result, stack) = cast(player, args, entry)
        check(result == ResolvedPatternType.EVALUATED) { "${entry.id} failed: $result, stack=${stack.map { it.display().string }}" }
    }

    private fun castQuery(player: ServerPlayer, args: List<Iota>, entry: RegistrarEntry<ActionRegistryEntry>): Iota? =
        cast(player, args, entry).second.lastOrNull()

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }

    companion object {
        const val STANDING = "mirabilia_standing"
        private val ANCHOR = BlockPos(2, 1, 2)
        private const val DUST = at.petrak.hexcasting.api.misc.MediaConstants.DUST_UNIT
    }
}
