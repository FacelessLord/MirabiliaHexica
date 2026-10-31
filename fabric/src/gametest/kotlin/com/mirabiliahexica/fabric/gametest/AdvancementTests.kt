package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.lens.SunSighting
import com.mirabiliahexica.registry.MirabiliaActions
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3

/** Advancements that the other tests don't already earn along the way. */
class AdvancementTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
    fun chargedAirOpensTheTab(helper: GameTestHelper) {
        val player = player(helper)
        val chunk = ChunkPos(player.blockPosition())
        check(!MirabiliaAdvancements.has(player, "root")) { "the tab was open from the start" }
        AmbientMedia.set(helper.level, chunk, AmbientMedia.CHARGED)
        // Sparks are looked for every two seconds
        helper.runAfterDelay(45) {
            check(MirabiliaAdvancements.has(player, "root")) { "standing in charged air didn't earn the root" }
            AmbientMedia.set(helper.level, chunk, 0)
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun freezingToAFullBlockIsHoarfrost(helper: GameTestHelper) {
        val player = player(helper)
        player.setGameMode(GameType.CREATIVE)
        helper.setBlock(net.minecraft.core.BlockPos(4, 1, 4), net.minecraft.world.level.block.Blocks.STONE)
        val target = helper.absoluteVec(Vec3(4.5, 2.0, 4.5))
        repeat(7) {
            freeze(player, target)
            check(!MirabiliaAdvancements.has(player, "hoarfrost")) { "Hoarfrost before the snow was full" }
        }
        freeze(player, target)
        check(MirabiliaAdvancements.has(player, "hoarfrost")) { "no Hoarfrost; snow there: ${helper.level.getBlockState(net.minecraft.core.BlockPos.containing(target))}" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun theSignOnTheSun(helper: GameTestHelper) {
        val player = player(helper)
        helper.level.dayTime = 6000
        helper.level.setWeatherParameters(6000, 0, false, false)
        player.xRot = -90f // straight up, at the noon sun
        check(!SunSighting.seesSign(player)) { "saw the sign without a lens" }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack(HexItems.SCRYING_LENS))
        check(SunSighting.seesSign(player)) { "a lens wearer looking at the noon sun didn't see the sign" }
        player.xRot = 0f
        check(!SunSighting.seesSign(player)) { "saw the sign looking at the horizon" }

        MirabiliaAdvancements.sawCaltrop(player, 1)
        check(MirabiliaAdvancements.has(player, "caltrop")) { "no Caltrop" }
        repeat(5) { MirabiliaAdvancements.sawCaltrop(player, 1) }
        check(!MirabiliaAdvancements.has(player, "different_every_day")) { "the same day counted many times" }
        (2L..7L).forEach { MirabiliaAdvancements.sawCaltrop(player, it) }
        check(MirabiliaAdvancements.has(player, "different_every_day")) { "seven days, but no Different Every Day" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun aThousandDustOfSunlight(helper: GameTestHelper) {
        val player = player(helper)
        MirabiliaAdvancements.solarGathered(helper.level.server, player.uuid, 999 * DUST)
        check(!MirabiliaAdvancements.has(player, "kind_thoughts")) { "Kind Thoughts before a thousand dust" }
        MirabiliaAdvancements.solarGathered(helper.level.server, player.uuid, DUST)
        check(MirabiliaAdvancements.has(player, "kind_thoughts")) { "a thousand dust, but no Kind Thoughts" }
        helper.succeed()
    }

    private fun freeze(player: ServerPlayer, at: Vec3) {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = listOf(Vec3Iota(at), DoubleIota(1.0)))
        vm.queueExecuteAndWrapIota(PatternIota(MirabiliaActions.FREEZE.value.prototype), player.serverLevel())
    }

    private fun player(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(1.5, 2.0, 1.5)).let { player.moveTo(it.x, it.y, it.z) }
        return player
    }

    private companion object {
        const val BATCH = "mirabilia_ambient"
        const val DUST = MediaConstants.DUST_UNIT
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
