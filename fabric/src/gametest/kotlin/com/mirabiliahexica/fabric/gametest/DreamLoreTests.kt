package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.common.items.storage.ItemScroll
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.DreamLore
import com.mirabiliahexica.registry.MirabiliaActions
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3

/** Dream spells must be found on scrolls before they work. */
class DreamLoreTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mirabilia_dream")
    fun aDreamSpellWorksOnlyOnceItsScrollIsRead(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(1.5, 2.0, 1.5)).let { player.moveTo(it.x, it.y, it.z) }
        Dream.enter(player, null)
        check(castWake(player) == ResolvedPatternType.INVALID) { "Wake worked before its scroll was read" }
        check(Dream.isDreaming(player)) { "woke without knowing Wake" }

        // A Wake scroll, as the dream's loot gives them
        val scroll = listeningPostLoot(helper).firstOrNull { it.tag?.getString(ItemScroll.TAG_OP_ID) == "mirabilia:wake" }
            ?: throw GameTestAssertException("no Wake scroll in a hundred listening post chests")
        check(scroll.tag!!.contains(ItemScroll.TAG_PATTERN)) { "the scroll has no pattern to show" }
        player.inventory.setItem(0, scroll)
        player.inventoryMenu.broadcastChanges()
        check(DreamLore.knows(player, "wake")) { "picking up the scroll didn't teach Wake" }
        check(!DreamLore.knows(player, "will_upward")) { "the Wake scroll taught another spell too" }
        check(castWake(player) == ResolvedPatternType.EVALUATED && !Dream.isDreaming(player)) { "Wake didn't work once learned" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mirabilia_dream")
    // (Its chests also hold scrolls of great spells; only the dream's own are counted here)
    fun theDreamGivesUpEveryKindOfScroll(helper: GameTestHelper) {
        val found = listeningPostLoot(helper).mapNotNull { stack ->
            stack.tag?.getString(ItemScroll.TAG_OP_ID)?.takeIf { stack.`is`(HexItems.SCROLL_LARGE) && it.startsWith("mirabilia:") }
        }.toSet()
        val all = DreamLore.SPELLS.map { "mirabilia:$it" }.toSet()
        check(found.containsAll(all)) { "a hundred chests gave ${found.sorted()}, expected all of ${all.sorted()}" }
        helper.succeed()
    }

    /** What a hundred listening post chests would hold. */
    private fun listeningPostLoot(helper: GameTestHelper) = (1..100).flatMap {
        val table = helper.level.server.lootData.getLootTable(Mirabilia.id("chests/listening_post"))
        val params = LootParams.Builder(helper.level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO)).create(LootContextParamSets.CHEST)
        table.getRandomItems(params)
    }

    private fun castWake(player: ServerPlayer) = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        .queueExecuteAndWrapIota(PatternIota(MirabiliaActions.WAKE.value.prototype), player.serverLevel()).resolutionType

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
