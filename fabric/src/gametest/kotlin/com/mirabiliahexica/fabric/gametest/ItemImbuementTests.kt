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
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexActions
import com.mirabiliahexica.imbue.ArmorImbuement
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.RegistrarEntry
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.phys.Vec3

class ItemImbuementTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun imbuingMakesAStoreAndRechargingRefillsIt(helper: GameTestHelper) {
        val player = caster(helper, GameType.CREATIVE)
        GreatSpells.enlighten(player)
        val hex = ListIota(listOf(PatternIota(HexActions.GET_CASTER.prototype)))

        // Imbuing uses up the whole stack: five Charged Amethyst make a 50-dust store, full
        val chestplate = ItemStack(Items.IRON_CHESTPLATE)
        player.setItemInHand(InteractionHand.OFF_HAND, chestplate)
        val crystals = drop(helper, ItemStack(HexItems.CHARGED_AMETHYST, 5))
        check(cast(player, MirabiliaActions.IMBUE_ARMOR, EntityIota(crystals), hex) == ResolvedPatternType.EVALUATED) { "Imbue Armor failed" }
        check(Imbuement.isImbued(chestplate)) { "armor not imbued" }
        check(Imbuement.capacity(chestplate) == 50 * DUST && Imbuement.media(chestplate) == 50 * DUST) {
            "store is ${Imbuement.media(chestplate) / DUST}/${Imbuement.capacity(chestplate) / DUST} dust, expected 50/50"
        }
        check(!crystals.isAlive) { "the amethyst wasn't used up" }

        // Recharging takes only what fits: 30 dust of room from a stack of 64 dust
        Imbuement.setMedia(chestplate, 20 * DUST)
        val dust = drop(helper, ItemStack(HexItems.AMETHYST_DUST, 64))
        player.setItemInHand(InteractionHand.OFF_HAND, chestplate)
        check(cast(player, MirabiliaActions.RECHARGE_IMBUED, EntityIota(dust)) == ResolvedPatternType.EVALUATED) { "Recharge failed" }
        check(Imbuement.media(chestplate) == 50 * DUST) { "recharged to ${Imbuement.media(chestplate) / DUST} dust, expected 50" }
        check(dust.isAlive && dust.item.count == 34) { "expected 34 dust left on the ground, got ${dust.item}" }

        // Or from a wellspring: bind a chest, then recharge with null
        Imbuement.setMedia(chestplate, 45 * DUST)
        helper.setBlock(BlockPos(4, 2, 1), Blocks.CHEST)
        (helper.getBlockEntity(BlockPos(4, 2, 1)) as ChestBlockEntity).setItem(0, ItemStack(HexItems.AMETHYST_DUST, 20))
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = listOf(Vec3Iota(Vec3.atCenterOf(helper.absolutePos(BlockPos(4, 2, 1))))))
        vm.queueExecuteAndWrapIota(PatternIota(MirabiliaActions.SOURCE_BIND.value.prototype), player.serverLevel())
        vm.image = vm.image.copy(stack = listOf(NullIota()))
        val view = vm.queueExecuteAndWrapIota(PatternIota(MirabiliaActions.RECHARGE_IMBUED.value.prototype), player.serverLevel())
        check(view.resolutionType == ResolvedPatternType.EVALUATED) { "Recharge from a wellspring failed: ${view.resolutionType}" }
        check(Imbuement.media(chestplate) == 50 * DUST) { "wellspring recharged to ${Imbuement.media(chestplate) / DUST} dust, expected 50" }
        val chestDust = (helper.getBlockEntity(BlockPos(4, 2, 1)) as ChestBlockEntity).getItem(0).count
        // 5 dust for the room, and the spell's own cost of a shard (5 dust; also paid from the wellspring first)
        check(chestDust == 20 - 5 - 5) { "the chest has $chestDust dust left, expected 10" }
        // Null with no wellspring bound is a mishap
        check(cast(player, MirabiliaActions.RECHARGE_IMBUED, NullIota()) == ResolvedPatternType.ERRORED) { "null recharge without a wellspring" }

        // Tools are imbued the same way; armor isn't a tool, a tool isn't armor, and lenses keep their own imbuement
        val pick = ItemStack(Items.IRON_PICKAXE)
        player.setItemInHand(InteractionHand.OFF_HAND, pick)
        check(cast(player, MirabiliaActions.IMBUE_ARMOR, EntityIota(drop(helper, ItemStack(HexItems.AMETHYST_DUST, 4))), hex) == ResolvedPatternType.ERRORED) {
            "a pickaxe taken for armor"
        }
        player.setItemInHand(InteractionHand.OFF_HAND, pick)
        check(cast(player, MirabiliaActions.IMBUE_TOOL, EntityIota(drop(helper, ItemStack(HexItems.AMETHYST_DUST, 4))), hex) == ResolvedPatternType.EVALUATED) {
            "Imbue Tool failed"
        }
        check(Imbuement.isImbued(pick) && Imbuement.capacity(pick) == 4 * DUST) { "pickaxe not imbued with a 4-dust store" }
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack(HexItems.SCRYING_LENS))
        check(cast(player, MirabiliaActions.IMBUE_ARMOR, EntityIota(drop(helper, ItemStack(HexItems.AMETHYST_DUST, 4))), hex) == ResolvedPatternType.ERRORED) {
            "a lens taken for armor"
        }
        helper.succeed()
    }

    private fun drop(helper: GameTestHelper, stack: ItemStack): ItemEntity {
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        val entity = ItemEntity(helper.level, pos.x, pos.y, pos.z, stack)
        entity.setNeverPickUp()
        helper.level.addFreshEntity(entity)
        return entity
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun wornArmorRunsOnItsOwnMediaUntilDry(helper: GameTestHelper) {
        val player = caster(helper, GameType.SURVIVAL)
        // Media in the pockets, which the armor must leave alone
        player.inventory.add(ItemStack(HexItems.AMETHYST_DUST, 10))
        val boots = ItemStack(Items.LEATHER_BOOTS)
        Imbuement.imbue(boots, listOf(PatternIota(HexActions.GET_CASTER.prototype)), capacity = ArmorImbuement.UPKEEP * 2 + ArmorImbuement.UPKEEP / 2)

        // Not worn: nothing runs
        // (A copy: adding a stack to the inventory moves it, leaving the original empty)
        player.inventory.add(boots.copy())
        check(ArmorImbuement.runArmor(player) == 0) { "armor in the backpack ran" }
        player.inventory.clearContent()
        player.inventory.add(ItemStack(HexItems.AMETHYST_DUST, 10))

        player.setItemSlot(EquipmentSlot.FEET, boots)
        check(ArmorImbuement.runArmor(player) == 1) { "worn armor didn't run" }
        check(ArmorImbuement.runArmor(player) == 1) { "worn armor didn't run twice" }
        check(ArmorImbuement.runArmor(player) == 0) { "armor ran without the media for its upkeep" }
        check(player.inventory.countItem(HexItems.AMETHYST_DUST) == 10) { "armor drank from the wearer's pockets" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun anImbuedHexMayBindAWellspring(helper: GameTestHelper) {
        val player = caster(helper, GameType.SURVIVAL)
        val chestPos = BlockPos(4, 2, 1)
        helper.setBlock(chestPos, Blocks.CHEST)
        val chest = helper.getBlockEntity(chestPos) as ChestBlockEntity
        chest.setItem(0, ItemStack(HexItems.AMETHYST_DUST, 10))
        val water = helper.absolutePos(BlockPos(3, 2, 3))
        helper.setBlock(BlockPos(3, 2, 3), Blocks.WATER)
        // Bind the chest, then Freeze (1.25 dust): the chest pays for the spell, the boots only their upkeep
        val hex = listOf(
            PatternIota(HexActions.ESCAPE.prototype), Vec3Iota(Vec3.atCenterOf(helper.absolutePos(chestPos))),
            PatternIota(MirabiliaActions.SOURCE_BIND.value.prototype),
            PatternIota(HexActions.ESCAPE.prototype), Vec3Iota(Vec3.atCenterOf(water)),
            PatternIota(HexActions.ESCAPE.prototype), DoubleIota(1.0),
            PatternIota(MirabiliaActions.FREEZE.value.prototype),
        )
        val boots = ItemStack(Items.LEATHER_BOOTS)
        Imbuement.imbue(boots, hex, capacity = DUST)
        player.setItemSlot(EquipmentSlot.FEET, boots)

        check(ArmorImbuement.runArmor(player) == 1) { "the boots didn't run" }
        check(helper.level.getBlockState(water).`is`(Blocks.ICE)) { "the hex didn't freeze the water" }
        check(Imbuement.media(boots) == DUST - ArmorImbuement.UPKEEP) { "the boots paid ${(DUST - Imbuement.media(boots)) / DUST.toDouble()} dust" }
        // Whole dust items: 1.25 dust takes two
        check(chest.getItem(0).count == 8) { "the chest has ${chest.getItem(0).count} dust left, expected 8" }

        // A tool wears for all its hex spends, even what the wellspring paid: Freeze radius 6 is 10 dust
        val pick = ItemStack(Items.IRON_PICKAXE)
        val bigFreeze = hex.dropLast(3) + listOf(
            PatternIota(HexActions.ESCAPE.prototype), Vec3Iota(Vec3.atCenterOf(water)),
            PatternIota(HexActions.ESCAPE.prototype), DoubleIota(6.0),
            PatternIota(MirabiliaActions.FREEZE.value.prototype),
        )
        chest.setItem(0, ItemStack(HexItems.AMETHYST_DUST, 64))
        Imbuement.imbue(pick, bigFreeze, capacity = DUST)
        player.setItemInHand(InteractionHand.MAIN_HAND, pick)
        check(ArmorImbuement.useTool(player, InteractionHand.MAIN_HAND)) { "the tool didn't cast" }
        check(Imbuement.media(pick) == DUST) { "the tool paid from its own store though the chest could" }
        check(pick.damageValue == 2) { "expected 1 + 1 wear for 10 dust, got ${pick.damageValue}" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun toolCastsOnUseAndWearsOut(helper: GameTestHelper) {
        val player = caster(helper, GameType.SURVIVAL)
        val water = helper.absolutePos(BlockPos(3, 2, 3))
        helper.setBlock(BlockPos(3, 2, 3), Blocks.WATER)
        // Freeze, radius 1, at the water: 1.25 dust
        val hex = listOf(
            PatternIota(HexActions.ESCAPE.prototype), Vec3Iota(Vec3.atCenterOf(water)),
            PatternIota(HexActions.ESCAPE.prototype), DoubleIota(1.0),
            PatternIota(MirabiliaActions.FREEZE.value.prototype),
        )
        val pick = ItemStack(Items.IRON_PICKAXE)
        Imbuement.imbue(pick, hex, capacity = 3 * DUST)
        player.setItemInHand(InteractionHand.MAIN_HAND, pick)

        check(ArmorImbuement.useTool(player, InteractionHand.MAIN_HAND)) { "the tool didn't cast" }
        check(helper.level.getBlockState(water).`is`(Blocks.ICE)) { "the hex didn't run: no ice" }
        check(Imbuement.media(pick) == 3 * DUST - DUST - DUST / 4) { "spent ${(3 * DUST - Imbuement.media(pick)) / DUST.toDouble()} dust" }
        check(pick.damageValue == 1) { "expected 1 point of wear, got ${pick.damageValue}" }

        // Cooling down: a second use straight away does nothing
        check(!ArmorImbuement.useTool(player, InteractionHand.MAIN_HAND)) { "cast again during the cooldown" }

        // Dry: nothing is cast, nothing is worn
        player.cooldowns.removeCooldown(pick.item)
        Imbuement.setMedia(pick, 0)
        check(!ArmorImbuement.useTool(player, InteractionHand.MAIN_HAND)) { "a dry tool cast" }
        check(pick.damageValue == 1) { "a dry tool wore anyway" }
        helper.succeed()
    }

    private fun caster(helper: GameTestHelper, mode: GameType): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(mode)
        val pos = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        player.moveTo(pos.x, pos.y, pos.z)
        return player
    }

    private fun cast(player: ServerPlayer, entry: RegistrarEntry<ActionRegistryEntry>, vararg args: Iota): ResolvedPatternType {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = args.toList())
        return vm.queueExecuteAndWrapIota(GreatSpells.patternFor(player, entry), player.serverLevel()).resolutionType
    }

    private companion object {
        const val DUST = MediaConstants.DUST_UNIT
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
