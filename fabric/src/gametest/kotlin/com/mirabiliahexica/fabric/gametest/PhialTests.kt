package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.casting.actions.spells.PhialHelper
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.RegistrarEntry
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3

class PhialTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun joinPhialsMergesTwoPhials(helper: GameTestHelper) {
        val player = caster(helper, GameType.CREATIVE)
        val held = phial(50, 100)
        player.setItemInHand(InteractionHand.OFF_HAND, held)
        val dropped = drop(helper, phial(30, 40))

        castOk(player, listOf(EntityIota(dropped)), MirabiliaActions.JOIN_PHIALS)
        assertPhial(held, 80, 140)
        check(!dropped.isAlive) { "merged Phial wasn't used up" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun joinPhialsAbsorbsWholeStackOfAmethyst(helper: GameTestHelper) {
        val player = caster(helper, GameType.CREATIVE)
        val held = phial(0, 10)
        player.setItemInHand(InteractionHand.OFF_HAND, held)
        val dropped = drop(helper, ItemStack(HexItems.CHARGED_AMETHYST, 5))

        castOk(player, listOf(EntityIota(dropped)), MirabiliaActions.JOIN_PHIALS)
        // 5 Charged Amethyst = 50 dust, added to both charge and capacity
        assertPhial(held, 50, 60)
        check(!dropped.isAlive) { "absorbed stack wasn't used up" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun joinPhialsStopsAtCapacityCap(helper: GameTestHelper) {
        val player = caster(helper, GameType.CREATIVE)
        val capDust = PhialHelper.MAX_CAPACITY / MediaConstants.DUST_UNIT
        val held = phial(0, capDust - 15)
        player.setItemInHand(InteractionHand.OFF_HAND, held)
        val dropped = drop(helper, ItemStack(HexItems.CHARGED_AMETHYST, 5))

        castOk(player, listOf(EntityIota(dropped)), MirabiliaActions.JOIN_PHIALS)
        // Room for 15 dust: only one 10-dust crystal fits, whole items only
        assertPhial(held, 10, capDust - 5)
        check(dropped.isAlive && dropped.item.count == 4) { "expected 4 crystals left, got ${dropped.item}" }

        // A Phial too big to merge is refused, and nothing changes
        val tooBig = drop(helper, phial(0, 10))
        val result = cast(player, listOf(EntityIota(tooBig)), MirabiliaActions.JOIN_PHIALS)
        check(result == ResolvedPatternType.ERRORED) { "oversized merge should mishap, got $result" }
        check(tooBig.isAlive) { "refused Phial was destroyed" }
        assertPhial(held, 10, capDust - 5)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun bloodChargeTradesHealthForMedia(helper: GameTestHelper) {
        val player = caster(helper, GameType.SURVIVAL)
        val held = phial(0, 100)
        player.setItemInHand(InteractionHand.OFF_HAND, held)
        player.health = 20f

        castOk(player, listOf(EntityIota(player), DoubleIota(6.0)), MirabiliaActions.BLOOD_CHARGE)
        check(player.health == 14f) { "expected 14 health, got ${player.health}" }
        // Default overcast rate: 1 dust per health point
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun bloodChargeNeverTakesLastHalfHeart(helper: GameTestHelper) {
        val player = caster(helper, GameType.SURVIVAL)
        val held = phial(0, 100)
        player.setItemInHand(InteractionHand.OFF_HAND, held)
        player.health = 3f

        castOk(player, listOf(EntityIota(player), DoubleIota(10.0)), MirabiliaActions.BLOOD_CHARGE)
        check(player.health == 1f && player.isAlive) { "expected 1 health left, got ${player.health}" }
        assertPhial(held, 2, 100)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun bloodChargeOnlyFillsAvailableRoom(helper: GameTestHelper) {
        val player = caster(helper, GameType.SURVIVAL)
        val held = phial(97, 100)
        player.setItemInHand(InteractionHand.OFF_HAND, held)
        player.health = 20f

        castOk(player, listOf(EntityIota(player), DoubleIota(10.0)), MirabiliaActions.BLOOD_CHARGE)
        check(player.health == 17f) { "expected only 3 health taken, got ${20f - player.health}" }
        assertPhial(held, 100, 100)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun bloodChargeOnlyBleedsPeacefulCreatures(helper: GameTestHelper) {
        val player = caster(helper, GameType.SURVIVAL)
        val held = phial(0, 100)
        player.setItemInHand(InteractionHand.OFF_HAND, held)

        val pig = helper.spawnWithNoFreeWill(EntityType.PIG, Vec3(2.5, 2.0, 2.5))
        pig.health = 10f
        castOk(player, listOf(EntityIota(pig), DoubleIota(4.0)), MirabiliaActions.BLOOD_CHARGE)
        check(pig.health == 6f) { "expected the pig at 6 health, got ${pig.health}" }
        assertPhial(held, 4, 100)

        // Villagers hold it against you
        val villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, Vec3(1.5, 2.0, 3.5))
        castOk(player, listOf(EntityIota(villager), DoubleIota(2.0)), MirabiliaActions.BLOOD_CHARGE)
        check(villager.getPlayerReputation(player) < 0) { "the villager didn't mind being bled" }

        assertPhial(held, 6, 100)

        // Monsters can't be bled, so it can't soften up a boss or any other foe. (The mishaps below knock the
        // Phial out of hand, as mishaps do, so they come last.)
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3(3.5, 2.0, 3.5))
        val zombieHealth = zombie.health
        check(cast(player, listOf(EntityIota(zombie), DoubleIota(4.0)), MirabiliaActions.BLOOD_CHARGE) == ResolvedPatternType.ERRORED) {
            "bleeding a zombie should mishap"
        }
        check(zombie.health == zombieHealth) { "the zombie lost health" }

        val other = caster(helper, GameType.SURVIVAL)
        other.health = 20f
        val result = cast(player, listOf(EntityIota(other), DoubleIota(4.0)), MirabiliaActions.BLOOD_CHARGE)
        check(result == ResolvedPatternType.ERRORED) { "bleeding another player should mishap, got $result" }
        check(other.health == 20f) { "the other player lost health" }
        helper.succeed()
    }

    private fun phial(mediaDust: Long, capacityDust: Long): ItemStack =
        ItemMediaHolder.withMedia(ItemStack(HexItems.BATTERY), mediaDust * MediaConstants.DUST_UNIT, capacityDust * MediaConstants.DUST_UNIT)

    private fun assertPhial(stack: ItemStack, mediaDust: Long, capacityDust: Long) {
        val media = PhialHelper.media(stack) / MediaConstants.DUST_UNIT.toDouble()
        val capacity = PhialHelper.capacity(stack) / MediaConstants.DUST_UNIT.toDouble()
        check(media == mediaDust.toDouble() && capacity == capacityDust.toDouble()) {
            "Phial holds $media/$capacity dust, expected $mediaDust/$capacityDust"
        }
    }

    private fun drop(helper: GameTestHelper, stack: ItemStack): ItemEntity {
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        val entity = ItemEntity(helper.level, pos.x, pos.y, pos.z, stack)
        entity.setNoPickUpDelay()
        entity.setNeverPickUp()
        helper.level.addFreshEntity(entity)
        return entity
    }

    private fun caster(helper: GameTestHelper, mode: GameType): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(mode)
        GreatSpells.enlighten(player)
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        player.teleportTo(pos.x, pos.y, pos.z)
        return player
    }

    private fun cast(player: ServerPlayer, args: List<Iota>, entry: RegistrarEntry<ActionRegistryEntry>): ResolvedPatternType {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = args)
        return vm.queueExecuteAndWrapIota(GreatSpells.patternFor(player, entry), player.serverLevel()).resolutionType
    }

    private fun castOk(player: ServerPlayer, args: List<Iota>, entry: RegistrarEntry<ActionRegistryEntry>) {
        val result = cast(player, args, entry)
        check(result == ResolvedPatternType.EVALUATED) { "${entry.id} failed: $result" }
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
