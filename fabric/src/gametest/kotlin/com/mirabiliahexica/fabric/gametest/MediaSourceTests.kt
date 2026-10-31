package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexActions
import com.mirabiliahexica.imbue.ImbuedCastEnv
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.lens.LensImbuement
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.RegistrarEntry
import com.mirabiliahexica.source.MediaSource
import com.mirabiliahexica.source.MediaSources
import com.mirabiliahexica.source.Tether
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

class MediaSourceTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun bindingPaysFirstAndTravelsWithTheImage(helper: GameTestHelper) {
        val player = player(helper)
        // Media in the pockets, which a bound wellspring pays before
        player.inventory.add(ItemStack(HexItems.AMETHYST_DUST, 10))
        val rel = BlockPos(3, 2, 2)
        helper.setBlock(rel, Blocks.CHEST)
        val chest = helper.getBlockEntity(rel) as ChestBlockEntity
        chest.setItem(0, ItemStack(HexItems.AMETHYST_DUST, 64))

        val vm = vm(player)
        cast(vm, player, MirabiliaActions.SOURCE_BIND, Vec3Iota(Vec3.atCenterOf(helper.absolutePos(rel))))
        check(vm.env.extractMedia(30 * DUST, false) == 0L) { "the bound chest didn't pay" }
        check(chest.getItem(0).count == 34) { "chest has ${chest.getItem(0).count} dust, expected 34" }
        check(player.inventory.countItem(HexItems.AMETHYST_DUST) == 10) { "pocket dust was spent instead" }

        // Reflection reports what's left
        cast(vm, player, MirabiliaActions.SOURCE_MEDIA)
        val left = (vm.image.stack.last() as DoubleIota).double
        check(left == 34.0) { "Wellspring Reflection said $left, expected 34" }

        // The binding travels with the image: a later cast on it (the staff's next pattern) still draws from it
        val next = CastingVM(vm.image, StaffCastEnv(player, InteractionHand.MAIN_HAND))
        next.env.extractMedia(20 * DUST, false)
        check(chest.getItem(0).count == 14) { "the binding didn't carry over to the next cast" }

        cast(next, player, MirabiliaActions.SOURCE_UNBIND)
        check(MediaSources.current(next.env) == null) { "Unbinding left a source bound" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun chestPaysFromItsAmethyst(helper: GameTestHelper) {
        val player = player(helper)
        val rel = BlockPos(2, 2, 2)
        helper.setBlock(rel, Blocks.CHEST)
        val chest = helper.getBlockEntity(rel) as ChestBlockEntity
        chest.setItem(0, ItemStack(HexItems.AMETHYST_DUST, 20))

        val vm = vm(player)
        cast(vm, player, MirabiliaActions.SOURCE_BIND, Vec3Iota(Vec3.atCenterOf(helper.absolutePos(rel))))
        check(MediaSources.current(vm.env) is MediaSource.Block) { "chest wasn't bound" }
        check(vm.env.extractMedia(5 * DUST, false) == 0L) { "the chest didn't pay" }
        check(chest.getItem(0).count == 15) { "chest has ${chest.getItem(0).count} dust, expected 15" }

        // Out of reach (the caster wandered off), the chest gives nothing; back in reach, it pays again
        player.moveTo(player.x + 100, player.y, player.z)
        check(MediaSources.current(vm.env)!!.withdraw(vm.env, 5 * DUST, false) == 0L) { "out-of-reach chest still paid" }
        check(chest.getItem(0).count == 15) { "out-of-reach chest lost dust" }
        player.moveTo(player.x - 100, player.y, player.z)

        // A source that runs dry hands the rest of the cost back to the usual payment
        val left = MediaSources.current(vm.env)!!.withdraw(vm.env, 40 * DUST, false)
        check(left == 15 * DUST && chest.getItem(0).isEmpty) { "drew $left from a chest with 15 dust" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun droppedStackPaysAndIsUsedUp(helper: GameTestHelper) {
        val player = player(helper)
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        val dropped = ItemEntity(helper.level, pos.x, pos.y, pos.z, ItemStack(HexItems.CHARGED_AMETHYST, 3))
        dropped.setNeverPickUp()
        helper.level.addFreshEntity(dropped)

        val vm = vm(player)
        cast(vm, player, MirabiliaActions.SOURCE_BIND, EntityIota(dropped))
        check(vm.env.extractMedia(25 * DUST, false) == 0L) { "the stack didn't pay" }
        // Whole crystals only, as Hex does: three are used for 25 dust
        check(!dropped.isAlive) { "spent stack is still lying there with ${dropped.item.count}" }

        // Players can't be bound: their media is their own
        val other = player(helper)
        val result = cast(vm, player, MirabiliaActions.SOURCE_BIND, EntityIota(other))
        check(result == ResolvedPatternType.ERRORED) { "binding a player should mishap, got $result" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun tetheredItemsDrawFromTheirWellspringWhileInReach(helper: GameTestHelper) {
        val player = player(helper)
        val chestPos = BlockPos(3, 2, 3)
        helper.setBlock(chestPos, Blocks.CHEST)
        val chest = helper.getBlockEntity(chestPos) as ChestBlockEntity
        chest.setItem(0, ItemStack(HexItems.AMETHYST_DUST, 20))
        val chestVec = Vec3Iota(Vec3.atCenterOf(helper.absolutePos(chestPos)))

        // Tether an imbued lens (the wearer has no media at all) and an imbued tool with an empty-ish store
        val lens = ItemStack(HexItems.SCRYING_LENS)
        LensImbuement.imbue(lens, listOf(PatternIota(HexActions.GET_CASTER.prototype)))
        player.setItemInHand(InteractionHand.OFF_HAND, lens)
        check(cast(vm(player), player, MirabiliaActions.SOURCE_TETHER, chestVec) == ResolvedPatternType.EVALUATED) { "couldn't tether the lens" }
        check(Tether.of(lens) == MediaSource.Block(helper.absolutePos(chestPos))) { "lens not tethered" }
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        player.setItemSlot(EquipmentSlot.HEAD, lens)

        // The lens's upkeep (a tenth of a dust) comes from the chest: one dust item spent
        LensImbuement.run(player)
        check(chest.getItem(0).count == 19) { "the tethered lens didn't draw from the chest: ${chest.getItem(0).count} left" }

        // Too far away, the chest gives nothing; the wearer has nothing either, so the lens just doesn't run
        player.moveTo(player.x + 100, player.y, player.z)
        LensImbuement.run(player)
        check(chest.getItem(0).count == 19) { "an out-of-reach chest still paid" }
        player.moveTo(player.x - 100, player.y, player.z)

        // A tethered tool pays from the chest before its own store
        val pick = ItemStack(Items.IRON_PICKAXE)
        Imbuement.imbue(pick, listOf(PatternIota(HexActions.GET_CASTER.prototype)), capacity = DUST)
        Tether.set(pick, MediaSource.Block(helper.absolutePos(chestPos)))
        val env = ImbuedCastEnv(player, pick, InteractionHand.MAIN_HAND)
        CastingVM.empty(env).env.extractMedia(3 * DUST, false)
        check(Imbuement.media(pick) == DUST && chest.getItem(0).count == 16) { "the tethered tool paid from itself" }

        // Null cuts the tether
        player.setItemInHand(InteractionHand.OFF_HAND, pick)
        cast(vm(player), player, MirabiliaActions.SOURCE_TETHER, NullIota())
        check(Tether.of(pick) == null) { "the tether wasn't cut" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun tetheredArtifactCastsOnItsWellspring(helper: GameTestHelper) {
        val player = player(helper)
        val chestPos = BlockPos(3, 2, 3)
        helper.setBlock(chestPos, Blocks.CHEST)
        val chest = helper.getBlockEntity(chestPos) as ChestBlockEntity
        chest.setItem(0, ItemStack(HexItems.AMETHYST_DUST, 20))
        val water = helper.absolutePos(BlockPos(1, 2, 4))
        helper.setBlock(BlockPos(1, 2, 4), Blocks.WATER)

        // An empty artifact holding Freeze (1.25 dust), tethered to the chest; the caster has no media
        val artifact = ItemStack(HexItems.ARTIFACT)
        HexItems.ARTIFACT.writeHex(artifact, listOf(
            PatternIota(HexActions.ESCAPE.prototype), Vec3Iota(Vec3.atCenterOf(water)),
            PatternIota(HexActions.ESCAPE.prototype), DoubleIota(1.0),
            PatternIota(MirabiliaActions.FREEZE.value.prototype),
        ), null, 0)
        Tether.set(artifact, MediaSource.Block(helper.absolutePos(chestPos)))
        player.setItemInHand(InteractionHand.MAIN_HAND, artifact)
        HexItems.ARTIFACT.use(helper.level, player, InteractionHand.MAIN_HAND)
        check(helper.level.getBlockState(water).`is`(Blocks.ICE)) { "the tethered artifact didn't cast" }
        check(chest.getItem(0).count == 18) { "the chest has ${chest.getItem(0).count} dust, expected 18 (1.25 dust, whole items)" }
        helper.succeed()
    }

    private fun player(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.SURVIVAL)
        val pos = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        player.moveTo(pos.x, pos.y, pos.z)
        return player
    }

    private fun vm(player: ServerPlayer) = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))

    private fun cast(vm: CastingVM, player: ServerPlayer, entry: RegistrarEntry<ActionRegistryEntry>, vararg args: Iota): ResolvedPatternType {
        vm.image = vm.image.copy(stack = vm.image.stack + args)
        return vm.queueExecuteAndWrapIota(GreatSpells.patternFor(player, entry), player.serverLevel()).resolutionType
    }

    private companion object {
        const val DUST = MediaConstants.DUST_UNIT
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
