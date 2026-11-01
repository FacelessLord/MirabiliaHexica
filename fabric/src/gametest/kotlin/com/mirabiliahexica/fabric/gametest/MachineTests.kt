package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.machines.AmbientCollectorBlockEntity
import com.mirabiliahexica.machines.CrystallizerBlockEntity
import com.mirabiliahexica.machines.MediaDischarger
import com.mirabiliahexica.machines.SolarCollectorBlockEntity
import com.mirabiliahexica.machines.Sunlight
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaItems
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB
import com.mirabiliahexica.source.MediaSources
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.advancement.MirabiliaAdvancements

/** The machines of media. In the ambient batch, apart from tests that don't expect ambient media to change. */
class MachineTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun dischargerArcsIntoCreatures(helper: GameTestHelper) {
        helper.setBlock(BlockPos(1, 2, 1), MirabiliaBlocks.MEDIA_DISCHARGER.value)
        val pos = helper.absolutePos(BlockPos(1, 2, 1))
        val chunk = ChunkPos(pos)
        val pig = helper.spawn(EntityType.PIG, BlockPos(3, 2, 3))
        // Watching from beyond the arc's reach, so never its target
        val watcher = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(1.5, 2.0, 12.5)).let { watcher.moveTo(it.x, it.y, it.z) }
        // Without a rod on top it never discharges
        AmbientMedia.set(helper.level, chunk, 200 * DUST)
        MediaDischarger.second(helper.level, pos)
        check(AmbientMedia.get(helper.level, chunk) == 200 * DUST && pig.health == pig.maxHealth) { "discharged without a lightning rod" }
        helper.setBlock(BlockPos(1, 3, 1), net.minecraft.world.level.block.Blocks.LIGHTNING_ROD)
        // Thin air: it rests, and nothing is hurt
        AmbientMedia.set(helper.level, chunk, 49 * DUST)
        MediaDischarger.second(helper.level, pos)
        check(AmbientMedia.get(helper.level, chunk) == 49 * DUST && pig.health == pig.maxHealth) { "discharged below the threshold" }
        AmbientMedia.set(helper.level, chunk, 200 * DUST)
        MediaDischarger.second(helper.level, pos)
        check(AmbientMedia.get(helper.level, chunk) == 180 * DUST) { "took ${(200 * DUST - AmbientMedia.get(helper.level, chunk)) / DUST} dust, expected 20" }
        // The arc picks a random living thing in reach, which may be a neighbouring test's mob: try until it's the pig
        repeat(20) {
            if (pig.health < pig.maxHealth) return@repeat
            pig.invulnerableTime = 0
            AmbientMedia.set(helper.level, chunk, 200 * DUST)
            MediaDischarger.second(helper.level, pos)
        }
        check(pig.health == pig.maxHealth - 2f) { "pig took ${pig.maxHealth - pig.health}, expected 2" }
        check(MediaDischarger.damage(1000 * DUST) == MediaDischarger.MAX_DAMAGE) { "damage isn't capped" }
        // A killing arc. Its target is random among living things in reach, and a neighbouring test's mob may be
        // in reach too, so give it a few tries to find the pig
        pig.health = 1f
        repeat(20) {
            if (!pig.isAlive) return@repeat
            pig.invulnerableTime = 0 // arcs come a second apart; here the last one was a moment ago
            AmbientMedia.set(helper.level, chunk, 200 * DUST)
            MediaDischarger.second(helper.level, pos)
        }
        check(!pig.isAlive && MirabiliaAdvancements.has(watcher, "high_voltage")) { "no High Voltage when the arc killed" }
        // Powered, it rests
        val before = AmbientMedia.get(helper.level, chunk)
        helper.setBlock(BlockPos(1, 2, 2), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK)
        MediaDischarger.second(helper.level, pos)
        check(AmbientMedia.get(helper.level, chunk) == before) { "a powered discharger still drank" }
        AmbientMedia.set(helper.level, chunk, 0)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun collectorsGather(helper: GameTestHelper) {
        helper.setBlock(BlockPos(1, 2, 1), MirabiliaBlocks.AMBIENT_COLLECTOR.value)
        val ambient = (helper.getBlockEntity(BlockPos(1, 2, 1)) as AmbientCollectorBlockEntity)
        val chunk = ChunkPos(ambient.blockPos)
        AmbientMedia.set(helper.level, chunk, 1000 * DUST)
        ambient.second(helper.level)
        val share = (1000 * DUST * AmbientCollectorBlockEntity.SHARE).toLong()
        check(ambient.media == share) { "ambient collector gathered ${ambient.media}, expected $share" }
        check(AmbientMedia.get(helper.level, chunk) == 1000 * DUST - share) { "the air didn't give it up" }
        AmbientMedia.set(helper.level, chunk, 0)

        helper.setBlock(BlockPos(3, 2, 1), MirabiliaBlocks.SOLAR_COLLECTOR.value)
        val solar = (helper.getBlockEntity(BlockPos(3, 2, 1)) as SolarCollectorBlockEntity)
        helper.level.dayTime = 6000 // noon
        helper.level.setWeatherParameters(6000, 0, false, false)
        check(Math.abs(Sunlight.intensity(helper.level, solar.blockPos) - 1.0) < 0.01) { "noon isn't full sun" }
        solar.second(helper.level)
        check(Math.abs(solar.media - DUST / 10) <= 10) { "solar collector gathered ${solar.media}, expected a tenth of a dust" }
        helper.level.dayTime = 18000 // midnight
        check(Sunlight.intensity(helper.level, solar.blockPos) == 0.0) { "the sun shines at midnight" }
        // Roofed over, it catches nothing
        helper.level.dayTime = 6000
        helper.setBlock(BlockPos(3, 4, 1), net.minecraft.world.level.block.Blocks.STONE)
        val before = solar.media
        solar.second(helper.level)
        check(solar.media == before) { "a roofed collector caught sunlight" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 300)
    fun crystallizerMakesShardsFromSunlight(helper: GameTestHelper) {
        helper.setBlock(BlockPos(1, 2, 1), MirabiliaBlocks.CRYSTALLIZER.value)
        helper.setBlock(BlockPos(3, 2, 1), MirabiliaBlocks.SOLAR_COLLECTOR.value)
        helper.setBlock(BlockPos(5, 2, 1), MirabiliaBlocks.AMBIENT_COLLECTOR.value)
        val watcher = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(2.5, 2.0, 4.5)).let { watcher.moveTo(it.x, it.y, it.z) }
        // Roofed, so the solar collector gathers nothing more while we watch
        helper.setBlock(BlockPos(3, 3, 1), net.minecraft.world.level.block.Blocks.STONE)
        val crystallizer = (helper.getBlockEntity(BlockPos(1, 2, 1)) as CrystallizerBlockEntity)
        val solar = (helper.getBlockEntity(BlockPos(3, 2, 1)) as SolarCollectorBlockEntity)
        val ambient = (helper.getBlockEntity(BlockPos(5, 2, 1)) as AmbientCollectorBlockEntity)
        solar.fill(3 * DUST)
        ambient.fill(10 * DUST)
        check(crystallizer.insert(ItemStack(HexItems.AMETHYST_DUST, 8))) { "dust wouldn't go in" }
        check(!crystallizer.insert(ItemStack(Items.DIRT))) { "dirt went in" }
        // First shard: all from the Sun; the second needs the ambient collector too
        helper.runAfterDelay(CrystallizerBlockEntity.TIME + 5L) {
            check(crystallizer.shards == 1 && crystallizer.dust == 4) { "after one round: ${crystallizer.shards} shards, ${crystallizer.dust} dust" }
            check(crystallizer.lastSolar) { "the first shard should be sunlight alone" }
            check(MirabiliaAdvancements.has(watcher, "crystal_of_light")) { "no Crystal of Light for the one watching" }
            check(MirabiliaAdvancements.has(watcher, "background_harvest")) { "no Background Harvest beside a new Ambient Collector" }
            check(solar.media == DUST) { "solar collector has ${solar.media / DUST.toDouble()} dust left, expected 1" }
            helper.runAfterDelay(CrystallizerBlockEntity.TIME.toLong()) {
                check(crystallizer.shards == 2 && crystallizer.dust == 0) { "after two rounds: ${crystallizer.shards} shards, ${crystallizer.dust} dust" }
                check(!crystallizer.lastSolar) { "the second shard used ambient media too" }
                // (The ambient collector also gathers from the air meanwhile, so only the solar one is exact)
                check(solar.media == 0L && ambient.media < 10 * DUST) { "solar ${solar.media}, ambient ${ambient.media}" }
                check(crystallizer.getItem(CrystallizerBlockEntity.OUTPUT).`is`(Items.AMETHYST_SHARD)) { "it didn't make shards" }
                helper.succeed()
            }
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun brokenCrystallizerDropsEverything(helper: GameTestHelper) {
        helper.setBlock(BlockPos(1, 2, 1), MirabiliaBlocks.CRYSTALLIZER.value)
        val crystallizer = helper.getBlockEntity(BlockPos(1, 2, 1)) as CrystallizerBlockEntity
        crystallizer.insert(ItemStack(HexItems.AMETHYST_DUST, 37))
        crystallizer.setItem(CrystallizerBlockEntity.OUTPUT, ItemStack(Items.AMETHYST_SHARD, 5))
        val pos = helper.absolutePos(BlockPos(1, 2, 1))
        helper.level.destroyBlock(pos, true)
        val dropped = helper.level.getEntitiesOfClass(ItemEntity::class.java, AABB(pos).inflate(2.0)).map { it.item }
        fun count(item: net.minecraft.world.item.Item) = dropped.filter { it.`is`(item) }.sumOf { it.count }
        check(count(HexItems.AMETHYST_DUST) == 37) { "dropped ${count(HexItems.AMETHYST_DUST)} dust, expected 37" }
        check(count(Items.AMETHYST_SHARD) == 5) { "dropped ${count(Items.AMETHYST_SHARD)} shards, expected 5" }
        check(count(MirabiliaItems.CRYSTALLIZER.value) == 1) { "the crystallizer itself didn't drop" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun collectorsAreWellsprings(helper: GameTestHelper) {
        helper.setBlock(BlockPos(1, 2, 1), MirabiliaBlocks.AMBIENT_COLLECTOR.value)
        val collector = (helper.getBlockEntity(BlockPos(1, 2, 1)) as AmbientCollectorBlockEntity)
        collector.fill(50 * DUST)
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.SURVIVAL)
        helper.absoluteVec(Vec3(3.5, 2.0, 3.5)).let { player.moveTo(it.x, it.y, it.z) }
        // Bind it, then Freeze radius 2 (2 dust): the collector pays
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = listOf(Vec3Iota(Vec3.atCenterOf(collector.blockPos))))
        vm.queueExecuteAndWrapIota(PatternIota(MirabiliaActions.SOURCE_BIND.value.prototype), helper.level)
        vm.image = vm.image.copy(stack = listOf(Vec3Iota(player.position()), DoubleIota(2.0)))
        vm.queueExecuteAndWrapIota(PatternIota(MirabiliaActions.FREEZE.value.prototype), helper.level)
        check(collector.media == 48 * DUST) { "collector has ${collector.media / DUST.toDouble()} dust, expected 48" }
        check(MirabiliaAdvancements.has(player, "supply_chain")) { "no Supply Chain" }
        check(MirabiliaAdvancements.has(player, "fully_provided")) { "no Fully Provided, though the pockets were empty" }

        // Read the Sun reads the sunlight where the cast happens
        helper.level.dayTime = 6000
        helper.level.setWeatherParameters(6000, 0, false, false)
        val read = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        read.queueExecuteAndWrapIota(PatternIota(MirabiliaActions.READ_THE_SUN.value.prototype), helper.level)
        val sun = (read.image.stack.last() as DoubleIota).double
        check(Math.abs(sun - 1.0) < 0.01) { "Read the Sun at noon gave $sun" }
        AmbientMedia.set(helper.level, ChunkPos(player.blockPosition()), 0)
        helper.succeed()
    }

    private companion object {
        const val BATCH = "mirabilia_ambient"
        const val DUST = MediaConstants.DUST_UNIT
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
