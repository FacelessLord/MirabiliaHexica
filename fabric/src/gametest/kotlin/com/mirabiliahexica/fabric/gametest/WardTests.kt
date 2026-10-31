package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.wards.ArmorWard
import com.mirabiliahexica.wards.WardStoneBlockEntity
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

/**
 * Stones reach 8 blocks, further than the gap between test plots, so these run in a batch of their own and
 * remove their stones when done.
 */
class WardTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun wardingStoneGuardsItsSurroundings(helper: GameTestHelper) {
        val owner = player(helper, Vec3(1.5, 2.0, 1.5))
        val other = player(helper, Vec3(1.5, 2.0, 2.5))
        val stone = place(helper, MirabiliaBlocks.WARDING_STONE.value, BlockPos(3, 2, 3), owner)
        val near = helper.absoluteVec(Vec3(5.5, 2.5, 5.5))
        val far = near.add(20.0, 0.0, 0.0)

        check(!env(other).isVecInRange(near)) { "warded spot was in range" }
        check(env(other).isVecInRange(far)) { "spot outside the ward was out of range" }
        check(env(owner).isVecInRange(near)) { "owner should be spared by default" }
        // Even a player's entity iota, which normally ignores range, can't reach into the ward
        val sheltered = player(helper, Vec3(5.5, 2.0, 5.5))
        check(!env(other).isEntityInRange(sheltered)) { "player inside the ward was in range" }

        stone.ownerExempt = false
        check(!env(owner).isVecInRange(near)) { "owner should be held back once exemption is off" }
        helper.setBlock(BlockPos(3, 2, 3), Blocks.AIR)
        check(env(other).isVecInRange(near)) { "ward outlived its stone" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun silenceStoneSilencesCastersInside(helper: GameTestHelper) {
        val owner = player(helper, Vec3(0.5, 2.0, 0.5))
        val inside = player(helper, Vec3(2.5, 2.0, 2.5))
        place(helper, MirabiliaBlocks.SILENCE_STONE.value, BlockPos(3, 2, 3), owner)
        val selfPos = inside.position()

        check(!env(inside).isVecInRange(selfPos)) { "silenced caster could still reach themselves" }
        // The stone doesn't guard places: a caster outside its sphere can still reach into it
        inside.moveTo(inside.x + 20, inside.y, inside.z)
        check(env(inside).isVecInRange(selfPos)) { "caster outside the silence was silenced" }
        check(env(owner).isVecInRange(owner.position())) { "owner should be spared by default" }
        helper.setBlock(BlockPos(3, 2, 3), Blocks.AIR)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun wardedArmorHidesWearerFromOthers(helper: GameTestHelper) {
        val wearer = player(helper, Vec3(1.5, 2.0, 1.5))
        val other = player(helper, Vec3(3.5, 2.0, 3.5))
        check(env(other).isEntityInRange(wearer)) { "unwarded wearer should be in range" }

        val chestplate = ItemStack(Items.IRON_CHESTPLATE)
        ArmorWard.ward(chestplate)
        wearer.setItemSlot(EquipmentSlot.CHEST, chestplate)
        check(!env(other).isEntityInRange(wearer)) { "warded wearer was in range of someone else" }
        check(env(wearer).isEntityInRange(wearer)) { "wearer should still reach themselves" }
        check(env(other).isVecInRange(wearer.position().add(0.5, 0.0, 0.0))) { "spot beside the wearer was refused" }

        // Mobs can wear it too
        val pig = EntityType.PIG.create(helper.level)!!
        pig.moveTo(helper.absoluteVec(Vec3(2.5, 2.0, 2.5)))
        pig.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.LEATHER_HELMET).also(ArmorWard::ward))
        helper.level.addFreshEntity(pig)
        check(!env(other).isEntityInRange(pig)) { "warded pig was in range" }
        pig.discard()
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun wardArmorSpellWardsOffhandArmor(helper: GameTestHelper) {
        val player = player(helper, Vec3(1.5, 2.0, 1.5))
        player.setGameMode(GameType.CREATIVE)
        val helmet = ItemStack(Items.DIAMOND_HELMET)
        player.setItemInHand(InteractionHand.OFF_HAND, helmet)

        check(cast(player) == ResolvedPatternType.EVALUATED) { "Ward Armor failed" }
        check(ArmorWard.isWarded(helmet)) { "helmet wasn't warded" }
        check(cast(player) == ResolvedPatternType.ERRORED) { "warding twice should mishap" }

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack(Items.STICK))
        check(cast(player) == ResolvedPatternType.ERRORED) { "warding a stick should mishap" }
        helper.succeed()
    }

    private fun cast(player: ServerPlayer): ResolvedPatternType {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        val pattern = GreatSpells.patternFor(player, MirabiliaActions.WARD_ARMOR)
        return vm.queueExecuteAndWrapIota(pattern, player.serverLevel()).resolutionType
    }

    /** A staff casting environment, created through a VM so the create event (and the ward hook) fires. */
    private fun env(player: ServerPlayer) = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND)).env

    private fun place(helper: GameTestHelper, block: Block, rel: BlockPos, owner: ServerPlayer): WardStoneBlockEntity {
        helper.setBlock(rel, block)
        val stone = helper.getBlockEntity(rel) as WardStoneBlockEntity
        stone.owner = owner.uuid
        return stone
    }

    private fun player(helper: GameTestHelper, rel: Vec3): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        val pos = helper.absoluteVec(rel)
        player.moveTo(pos.x, pos.y, pos.z)
        return player
    }

    private companion object {
        const val BATCH = "mirabilia_wards"
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
