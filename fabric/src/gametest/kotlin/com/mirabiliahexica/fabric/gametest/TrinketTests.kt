package com.mirabiliahexica.fabric.gametest

import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.registry.MirabiliaItems
import dev.emi.trinkets.api.TrinketsApi
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3

/** Trinkets (Fabric's bauble slots): what's worn there survives dying in the dream. */
class TrinketTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mirabilia_dream", timeoutTicks = 200)
    fun dyingInTheDreamKeepsTrinkets(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.SURVIVAL)
        helper.absoluteVec(Vec3(1.5, 2.0, 1.5)).let { player.moveTo(it.x, it.y, it.z) }
        val face = faceSlot(player)
        face.setItem(0, ItemStack(MirabiliaItems.GREATER_LENS.value))
        check(faceSlot(player).getItem(0).`is`(MirabiliaItems.GREATER_LENS.value)) { "the lens didn't go in the face slot" }
        Dream.enter(player, null)
        player.hasChangedDimension()
        // Past the few seconds of protection players get after changing dimension
        helper.runAfterDelay(80) {
            val dream = player.serverLevel()
            val where = player.boundingBox.inflate(8.0)
            player.kill()
            check(dream.getEntitiesOfClass(ItemEntity::class.java, where).isEmpty()) { "something dropped in the dream" }
            val back = player.server.playerList.respawn(player, false)
            check(faceSlot(back).getItem(0).`is`(MirabiliaItems.GREATER_LENS.value)) { "the lens in the face slot was lost: ${faceSlot(back).getItem(0)}" }
            helper.succeed()
        }
    }

    private fun faceSlot(player: ServerPlayer) =
        TrinketsApi.getTrinketComponent(player).get().inventory["head"]?.get("face") ?: throw GameTestAssertException("no head/face trinket slot")

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
