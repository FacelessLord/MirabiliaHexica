package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.mod.HexTags
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexActions
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.MirabiliaItems
import com.mirabiliahexica.registry.RegistrarEntry
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3

class GreatSpellTests {
    private val greatSpells = listOf(MirabiliaActions.CRAFT_GREATER_LENS, MirabiliaActions.JOIN_PHIALS, MirabiliaActions.BLOOD_CHARGE, MirabiliaActions.IMBUE_LENS)

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun ourGreatSpellsCarryHexsGreatSpellTags(helper: GameTestHelper) {
        for (entry in greatSpells) {
            val holder = HexActions.REGISTRY.getHolderOrThrow(entry.key)
            for (tag in listOf(HexTags.Actions.PER_WORLD_PATTERN, HexTags.Actions.REQUIRES_ENLIGHTENMENT, HexTags.Actions.CAN_START_ENLIGHTEN)) {
                check(holder.`is`(tag)) { "${entry.id} is missing from ${tag.location()}" }
            }
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun craftGreaterLensReworksAScryingLens(helper: GameTestHelper) {
        val player = caster(helper)
        GreatSpells.enlighten(player)
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack(HexItems.SCRYING_LENS))

        check(cast(player, listOf(), MirabiliaActions.CRAFT_GREATER_LENS) == ResolvedPatternType.EVALUATED) { "Craft Greater Lens failed" }
        check(player.offhandItem.`is`(MirabiliaItems.GREATER_LENS.value)) { "other hand holds ${player.offhandItem}" }

        // Nothing to rework now: mishap
        check(cast(player, listOf(), MirabiliaActions.CRAFT_GREATER_LENS) == ResolvedPatternType.ERRORED) { "reworked a Greater Lens again" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun greatSpellsNeedEnlightenment(helper: GameTestHelper) {
        val player = caster(helper)
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack(HexItems.SCRYING_LENS))
        check(cast(player, listOf(), MirabiliaActions.CRAFT_GREATER_LENS) != ResolvedPatternType.EVALUATED) { "unenlightened caster crafted a lens" }
        // Hex's unenlightened mishap knocks held items loose, so the lens is dropped, but it must not have changed
        check(!player.offhandItem.`is`(MirabiliaItems.GREATER_LENS.value)) { "the Scrying Lens became a Greater Lens anyway" }
        val lenses = helper.level.getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(8.0)).map { it.item }
        check(lenses.none { it.`is`(MirabiliaItems.GREATER_LENS.value) }) { "a Greater Lens was made anyway" }

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack(MirabiliaItems.GREATER_LENS.value))
        check(cast(player, listOf(ListIota(listOf())), MirabiliaActions.IMBUE_LENS) != ResolvedPatternType.EVALUATED) { "unenlightened caster imbued a lens" }
        helper.succeed()
    }

    private fun caster(helper: GameTestHelper): ServerPlayer {
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
