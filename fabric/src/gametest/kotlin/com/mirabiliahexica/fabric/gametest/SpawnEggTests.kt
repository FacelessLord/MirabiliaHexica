package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.common.lib.hex.HexActions
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.GlassManSpawner
import com.mirabiliahexica.entity.GlassMan
import com.mirabiliahexica.entity.LostDreamer
import com.mirabiliahexica.imbue.ImbuedPatterns
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.registry.MirabiliaEntities
import com.mirabiliahexica.registry.MirabiliaItems
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.phys.Vec3

class SpawnEggTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mirabilia_dream", timeoutTicks = 100)
    fun eggsSpawnOurMobsAndAnEggGlassManWaits(helper: GameTestHelper) {
        val lostEgg = MirabiliaItems.LOST_DREAMER_SPAWN_EGG.value as SpawnEggItem
        val glassEgg = MirabiliaItems.GLASS_MAN_SPAWN_EGG.value as SpawnEggItem
        check(lostEgg.getType(null) == MirabiliaEntities.LOST_DREAMER.value) { "the Lost Dreamer egg spawns ${lostEgg.getType(null)}" }
        check(glassEgg.getType(null) == MirabiliaEntities.GLASS_MAN.value) { "the Glass Man egg spawns ${glassEgg.getType(null)}" }
        check(SpawnEggItem.byId(MirabiliaEntities.GLASS_MAN.value) === glassEgg) { "the Glass Man has no egg of its own (pick block, spawners)" }

        val dreamer = lostEgg.spawnsEntity(null, MirabiliaEntities.LOST_DREAMER.value)
        check(dreamer) { "the Lost Dreamer egg doesn't claim its mob" }
        val spawned = MirabiliaEntities.LOST_DREAMER.value.spawn(helper.level, ItemStack(lostEgg), null, helper.absolutePos(BlockPos(1, 2, 1)), MobSpawnType.SPAWN_EGG, true, false)
        check(spawned is LostDreamer) { "spawned $spawned" }

        // Awake, an egg Glass Man with nobody to follow stays, and touches nobody
        val player = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(4.5, 2.0, 4.5)).let { player.moveTo(it.x, it.y, it.z) }
        val glass = MirabiliaEntities.GLASS_MAN.value.spawn(helper.level, ItemStack(glassEgg), null, helper.absolutePos(BlockPos(3, 2, 3)), MobSpawnType.SPAWN_EGG, true, false) as GlassMan
        helper.runAfterDelay(40) {
            check(!glass.isRemoved) { "an egg Glass Man vanished" }
            check(glass.owner == null && glass.target == null) { "an egg Glass Man took ${glass.owner} awake" }
            glass.discard()
            spawned?.discard()
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mirabilia_dream", timeoutTicks = 100)
    fun anEggGlassManInTheDreamTakesADreamer(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(1.5, 2.0, 1.5)).let { player.moveTo(it.x, it.y, it.z) }
        Dream.enter(player, null)
        val dream = player.serverLevel()
        val centre = player.chunkPosition()
        for (dx in -2..2) for (dz in -2..2) dream.setChunkForced(centre.x + dx, centre.z + dz, true)
        val glass = MirabiliaEntities.GLASS_MAN.value.create(dream)!!
        glass.moveTo(player.x + 3, player.y, player.z)
        dream.addFreshEntity(glass)
        helper.runAfterDelay(20) {
            check(glass.owner == player.uuid) { "the egg Glass Man didn't take the dreamer nearby" }
            check(GlassManSpawner.find(player) === glass) { "it isn't counted as the dreamer's Glass Man" }
            Dream.wake(player, Dream.Waking.OTHER)
            for (dx in -2..2) for (dz in -2..2) dream.setChunkForced(centre.x + dx, centre.z + dz, false)
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun anImbuedItemsGlyphsAreItsWholeHex(helper: GameTestHelper) {
        val boots = ItemStack(Items.IRON_BOOTS)
        val patterns = listOf(HexActions.ADD, HexActions.SUB, HexActions.ABS, HexActions.MUL_DOT).map { PatternIota(it.prototype) }
        // Numbers are skipped; patterns inside a nested list count, in order
        Imbuement.imbue(boots, listOf(DoubleIota(3.0), patterns[0], ListIota(listOf(patterns[1], patterns[2])), patterns[3]), 10)
        val glyphs = ImbuedPatterns.of(boots)
        check(glyphs.map { it.anglesSignature() } == patterns.map { it.pattern.anglesSignature() }) { "the glyphs aren't the hex's patterns in order: $glyphs" }
        check(ImbuedPatterns.of(ItemStack(Items.IRON_BOOTS)).isEmpty()) { "plain boots have glyphs" }
        helper.succeed()
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
