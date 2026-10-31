package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.casting.iota.StringIota
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.lens.LensImbuement
import com.mirabiliahexica.lens.LensVisibility
import com.mirabiliahexica.network.MirabiliaNetworking
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.MirabiliaEnchantments
import com.mirabiliahexica.registry.MirabiliaItems
import com.mirabiliahexica.registry.RegistrarEntry
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class LensImbuementTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun imbueLensStoresAndClearsAHex(helper: GameTestHelper) {
        val player = creativeCaster(helper)
        GreatSpells.enlighten(player)
        val lens = ItemStack(MirabiliaItems.GREATER_LENS.value)
        player.setItemInHand(InteractionHand.OFF_HAND, lens)

        val hex = ListIota(listOf(PatternIota(HexPattern.fromAngles("aqaaw", HexDir.EAST)), DoubleIota(1.0)))
        // Three Charged Amethyst make the lens a 30-dust store
        val crystals = EntityIota(drop(helper, ItemStack(HexItems.CHARGED_AMETHYST, 3)))
        check(cast(player, listOf(crystals, hex), MirabiliaActions.IMBUE_LENS) == ResolvedPatternType.EVALUATED) { "Imbue Lens failed" }
        check(LensImbuement.isImbued(lens) && LensImbuement.hexSize(lens) == 2) { "lens not imbued with the 2-iota hex" }
        check(Imbuement.capacity(lens) == 30 * MediaConstants.DUST_UNIT && Imbuement.media(lens) == Imbuement.capacity(lens)) {
            "lens store is ${Imbuement.media(lens)}/${Imbuement.capacity(lens)}"
        }

        val more = EntityIota(drop(helper, ItemStack(HexItems.AMETHYST_DUST, 1)))
        check(cast(player, listOf(more, ListIota(listOf())), MirabiliaActions.IMBUE_LENS) == ResolvedPatternType.EVALUATED) { "clearing failed" }
        check(!LensImbuement.isImbued(lens)) { "empty list didn't clear the lens" }

        // No lens in the other hand: mishap
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        val again = EntityIota(drop(helper, ItemStack(HexItems.AMETHYST_DUST, 1)))
        check(cast(player, listOf(again, hex), MirabiliaActions.IMBUE_LENS) == ResolvedPatternType.ERRORED) { "imbued thin air" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun wornImbuedLensTurnsItsResultIntoLabels(helper: GameTestHelper) {
        val player = creativeCaster(helper)
        val near = helper.absoluteVec(Vec3(2.5, 3.0, 2.5))
        val far = near.add(200.0, 0.0, 0.0)
        val pig = helper.spawn(EntityType.PIG, Vec3(1.5, 2.0, 1.5))
        // Hex: Consideration, then a ready-made list of pairs, so the run leaves exactly that list on the stack
        val pairs = ListIota(listOf(
            ListIota(listOf(Vec3Iota(near), DoubleIota(5.0))),
            ListIota(listOf(Vec3Iota(far), DoubleIota(6.0))),
            ListIota(listOf(EntityIota(pig), DoubleIota(7.0))),
            DoubleIota(8.0),
        ))
        val lens = imbuedLens(listOf(consideration(), pairs))

        // Imbued but only in the inventory: nothing runs
        player.inventory.setItem(20, lens.copy()) // a backpack slot, not the hotbar
        check(LensImbuement.run(player).isEmpty()) { "a lens in the backpack shouldn't run" }

        player.setItemSlot(EquipmentSlot.HEAD, lens)
        val labels = LensImbuement.run(player)
        check(labels.size == 2) { "expected the near label and the pig's (far and malformed skipped), got $labels" }
        check(labels[0].anchor == MirabiliaNetworking.Label.Anchor.Fixed(near) && labels[0].lines.single().string.startsWith("5")) {
            "wrong fixed label: ${labels[0]}"
        }
        check(labels[1].anchor == MirabiliaNetworking.Label.Anchor.Following(pig.id) && labels[1].lines.single().string.startsWith("7")) {
            "wrong entity label: ${labels[1]}"
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun aListValueBecomesOneLinePerItem(helper: GameTestHelper) {
        val player = creativeCaster(helper)
        val pig = helper.spawn(EntityType.PIG, Vec3(1.5, 2.0, 1.5))
        val lines = ListIota(listOf(StringIota("Wilbur"), StringIota("HP 7"), DoubleIota(3.0)))
        val pairs = ListIota(listOf(
            ListIota(listOf(EntityIota(pig), lines)),
            // An empty list has nothing to show, so that label is dropped
            ListIota(listOf(EntityIota(player), ListIota(listOf()))),
        ))
        player.setItemSlot(EquipmentSlot.HEAD, imbuedLens(listOf(consideration(), pairs)))

        val labels = LensImbuement.run(player)
        check(labels.size == 1) { "expected one label, got $labels" }
        val shown = labels[0].lines.map { it.string }
        check(shown.size == 3 && shown[0] == "Wilbur" && shown[1] == "HP 7" && shown[2].startsWith("3")) { "lines were $shown" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun imbuedLensNeedsMediaAndMishapsQuietly(helper: GameTestHelper) {
        // An empty lens: the upkeep can't be paid (creative or not: the store is the lens's own); the lens never takes the wearer's media or health
        val poor = helper.makeMockServerPlayerInLevel()
        poor.setGameMode(GameType.SURVIVAL)
        poor.health = 20f
        poor.inventory.add(ItemStack(HexItems.AMETHYST_DUST, 10))
        val pairs = ListIota(listOf(ListIota(listOf(EntityIota(poor), DoubleIota(1.0)))))
        val lens = imbuedLens(listOf(consideration(), pairs), store = 0)
        poor.setItemSlot(EquipmentSlot.HEAD, lens)
        check(LensImbuement.run(poor).isEmpty()) { "lens ran without media" }
        val rich = creativeCaster(helper)
        rich.setItemSlot(EquipmentSlot.HEAD, imbuedLens(listOf(consideration(), pairs), store = 0))
        check(LensImbuement.run(rich).isEmpty()) { "an empty lens ran for a creative wearer" }
        check(poor.health == 20f) { "lens overcast its wearer" }
        check(poor.inventory.countItem(HexItems.AMETHYST_DUST) == 10) { "lens drank from the wearer's pockets" }
        // With media in its store, it runs, and pays from that
        Imbuement.imbue(lens, listOf(consideration(), pairs), capacity = MediaConstants.DUST_UNIT)
        check(LensImbuement.run(poor).size == 1) { "a charged lens didn't run" }
        check(Imbuement.media(lens) == MediaConstants.DUST_UNIT - LensImbuement.UPKEEP) { "the lens didn't pay its upkeep from its store" }

        // Creative (free media), but the hex mishaps: no labels, and the wearer isn't harmed or disarmed
        val unlucky = creativeCaster(helper)
        unlucky.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.STICK))
        unlucky.setItemSlot(EquipmentSlot.HEAD, imbuedLens(listOf(PatternIota(MirabiliaActions.FREEZE.value.prototype))))
        check(LensImbuement.run(unlucky).isEmpty()) { "mishapping hex produced labels" }
        check(unlucky.mainHandItem.`is`(Items.STICK)) { "the lens's mishap knocked items out of the wearer's hand" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun hiddenResultsAreKeptButNotSeen(helper: GameTestHelper) {
        val player = creativeCaster(helper)
        val behind = helper.absoluteVec(Vec3(2.5, 2.5, 6.5))
        val pig = helper.spawnWithNoFreeWill(EntityType.PIG, Vec3(4.5, 2.0, 6.5))
        // A wall across the plot, between the wearer (z 2.5) and both targets (z 6.5)
        for (x in 0..7) for (y in 1..5) helper.setBlock(BlockPos(x, y, 4), Blocks.STONE)
        val pairs = ListIota(listOf(
            ListIota(listOf(Vec3Iota(behind), DoubleIota(1.0))),
            ListIota(listOf(EntityIota(pig), DoubleIota(2.0))),
        ))
        val lens = imbuedLens(listOf(consideration(), pairs))
        player.setItemSlot(EquipmentSlot.HEAD, lens)

        // The hex's results are all kept, hidden or not...
        check(LensImbuement.run(player).size == 2) { "results behind the wall were thrown away" }
        // ...but neither target can be seen, so neither would be drawn
        check(!LensVisibility.canSee(player, behind) && !LensVisibility.canSee(player, pig)) { "saw through the wall" }
        check(!LensVisibility.seesThroughWalls(lens)) { "a plain lens claims Deepsight" }

        // Deepsight: only on the Greater Lens, and then everything is drawn
        check(MirabiliaEnchantments.DEEPSIGHT.value.canEnchant(lens)) { "Deepsight won't go on a Greater Lens" }
        check(!MirabiliaEnchantments.DEEPSIGHT.value.canEnchant(ItemStack(HexItems.SCRYING_LENS))) { "Deepsight went on a Scrying Lens" }
        lens.enchant(MirabiliaEnchantments.DEEPSIGHT.value, 1)
        check(LensVisibility.seesThroughWalls(lens)) { "Deepsight lens doesn't see through walls" }

        // See-through blocks don't hide anything: a wall of glass, then one of leaves
        for (see in listOf(Blocks.GLASS, Blocks.OAK_LEAVES)) {
            for (x in 0..7) for (y in 1..5) helper.setBlock(BlockPos(x, y, 4), see)
            check(LensVisibility.canSee(player, behind) && LensVisibility.canSee(player, pig)) { "couldn't see through $see" }
        }

        // Without the wall, both are in plain view
        for (x in 0..7) for (y in 1..5) helper.setBlock(BlockPos(x, y, 4), Blocks.AIR)
        check(LensVisibility.canSee(player, behind) && LensVisibility.canSee(player, pig)) { "couldn't see what's in plain view" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun scryingLensCanBeImbuedToo(helper: GameTestHelper) {
        val player = creativeCaster(helper)
        GreatSpells.enlighten(player)
        val lens = ItemStack(HexItems.SCRYING_LENS)
        player.setItemInHand(InteractionHand.OFF_HAND, lens)
        val pairs = ListIota(listOf(ListIota(listOf(Vec3Iota(helper.absoluteVec(Vec3(2.5, 3.0, 3.5))), DoubleIota(9.0)))))
        val hex = ListIota(listOf(consideration(), pairs))
        val dust = EntityIota(drop(helper, ItemStack(HexItems.AMETHYST_DUST, 4)))
        check(cast(player, listOf(dust, hex), MirabiliaActions.IMBUE_LENS) == ResolvedPatternType.EVALUATED) { "couldn't imbue a Scrying Lens" }
        check(LensImbuement.isImbued(lens)) { "Scrying Lens not imbued" }

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        player.setItemSlot(EquipmentSlot.HEAD, lens)
        check(LensImbuement.run(player).size == 1) { "worn imbued Scrying Lens didn't label" }
        helper.succeed()
    }

    /** A lens with [hex] and a store of [store] (enough for a long while by default: the store is always spent). */
    private fun imbuedLens(hex: List<Iota>, store: Long = 100 * MediaConstants.DUST_UNIT) =
        ItemStack(MirabiliaItems.GREATER_LENS.value).apply { Imbuement.imbue(this, hex, capacity = store) }

    private fun drop(helper: GameTestHelper, stack: ItemStack): ItemEntity {
        val pos = helper.absoluteVec(Vec3(3.5, 2.0, 3.5))
        val entity = ItemEntity(helper.level, pos.x, pos.y, pos.z, stack)
        entity.setNeverPickUp()
        helper.level.addFreshEntity(entity)
        return entity
    }

    /** Consideration: the next iota in the hex is pushed as-is instead of being run. */
    private fun consideration() = PatternIota(HexPattern.fromAngles("qqqaw", HexDir.WEST))

    private fun creativeCaster(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.CREATIVE)
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        player.moveTo(pos.x, pos.y, pos.z)
        return player
    }

    private fun cast(player: ServerPlayer, args: List<Iota>, entry: RegistrarEntry<ActionRegistryEntry>): ResolvedPatternType {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = args)
        return vm.queueExecuteAndWrapIota(GreatSpells.patternFor(player, entry), player.serverLevel()).resolutionType
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
