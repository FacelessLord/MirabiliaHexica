package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.DreamPull
import com.mirabiliahexica.registry.MirabiliaEffects
import com.mirabiliahexica.registry.MirabiliaActions
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.Pig
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.advancement.MirabiliaAdvancements

/** Ambient media. Each test works far off in its own chunks, as levels spread to neighbours. */
class AmbientMediaTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun castingLeavesAShareInTheAir(helper: GameTestHelper) {
        val player = caster(helper)
        val chunk = ChunkPos(player.blockPosition())
        AmbientMedia.set(helper.level, chunk, 0)
        // Freeze radius 2 at the caster's feet: 1 + 4/4 = 2 dust, so half a dust settles here
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = listOf(Vec3Iota(player.position()), DoubleIota(2.0)))
        vm.queueExecuteAndWrapIota(PatternIota(MirabiliaActions.FREEZE.value.prototype), helper.level)
        check(AmbientMedia.get(helper.level, chunk) == DUST / 2) { "ambient is ${AmbientMedia.get(helper.level, chunk) / DUST.toDouble()} dust, expected 0.5" }

        // Ambient Reflection reads it
        val read = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        read.queueExecuteAndWrapIota(PatternIota(MirabiliaActions.AMBIENT_MEDIA.value.prototype), helper.level)
        check((read.image.stack.last() as DoubleIota).double == 0.5) { "Ambient Reflection read ${read.image.stack.last()}" }
        AmbientMedia.set(helper.level, chunk, 0)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun itFadesAndSpreads(helper: GameTestHelper) {
        val level = helper.level
        val centre = ChunkPos(9000, 9000)
        AmbientMedia.set(level, centre, 1000 * DUST)
        AmbientMedia.step(level)
        val here = AmbientMedia.get(level, centre)
        val beside = AmbientMedia.get(level, ChunkPos(9001, 9000))
        check(beside > 0) { "nothing spread to the neighbour" }
        check(here < 820 * DUST && here > 780 * DUST) { "centre is ${here / DUST} dust after a step" }
        // Media is kept or lost to decay, never made: four neighbours' shares and the rest add to 98%
        val total = here + listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).sumOf { (dx, dz) -> AmbientMedia.get(level, ChunkPos(9000 + dx, 9000 + dz)) }
        check(Math.abs(total - 980 * DUST) <= 10) { "total after a step is ${total / DUST.toDouble()} dust, expected 980" }
        // Left alone it all fades away
        repeat(400) { AmbientMedia.step(level) }
        check(AmbientMedia.get(level, centre) == 0L) { "it never faded" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun stuffyAirMakesHexesMisfire(helper: GameTestHelper) {
        val player = caster(helper)
        val chunk = ChunkPos(player.blockPosition())
        fun misfires() = (1..400).count {
            !CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND)).env.isVecInRange(player.position())
        }
        AmbientMedia.set(helper.level, chunk, AmbientMedia.CHARGED)
        check(misfires() == 0) { "charged air made hexes misfire" }
        // Well past stuffy (just short of where the dream pulls): the full 15%
        AmbientMedia.set(helper.level, chunk, AmbientMedia.PULL - 1)
        val n = misfires()
        check(n in 30..100) { "expected about 60 of 400 casts to misfire, got $n" }
        check(MirabiliaAdvancements.has(player, "stuffy")) { "no Stuffy with Magic" }
        AmbientMedia.set(helper.level, chunk, 0)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun buddingAmethystBreathesMedia(helper: GameTestHelper) {
        val rel = BlockPos(2, 2, 2)
        helper.setBlock(rel, Blocks.BUDDING_AMETHYST)
        val pos = helper.absolutePos(rel)
        val chunk = ChunkPos(pos)
        AmbientMedia.set(helper.level, chunk, 0)
        val state = helper.level.getBlockState(pos)
        repeat(10) { state.randomTick(helper.level, pos, helper.level.random) }
        check(AmbientMedia.get(helper.level, chunk) == 10 * AmbientMedia.BUDDING_TICK) { "budding amethyst gave ${AmbientMedia.get(helper.level, chunk)}" }
        AmbientMedia.set(helper.level, chunk, 0)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun thickAirPullsCasterAndCompanyIntoTheDream(helper: GameTestHelper) {
        check(DreamPull.chance(AmbientMedia.PULL - 1) == 0.0) { "pulled below the threshold" }
        check(DreamPull.chance(AmbientMedia.PULL) == DreamPull.BASE_CHANCE) { "wrong chance at the threshold" }
        check(DreamPull.chance(100_000 * DUST) == DreamPull.MAX_CHANCE) { "chance not capped" }

        val player = caster(helper)
        val chunk = ChunkPos(player.blockPosition())
        val pig = helper.spawn(EntityType.PIG, BlockPos(3, 2, 1))
        val farPig = helper.spawn(EntityType.PIG, BlockPos(1, 2, 9))
        val friend = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(3.5, 2.0, 2.5)).let { friend.moveTo(it.x, it.y, it.z) }
        AmbientMedia.set(helper.level, chunk, AmbientMedia.PULL)
        check(DreamPull.pull(player)) { "couldn't pull" }
        check(Dream.isDreaming(player)) { "the caster isn't dreaming" }
        check(MirabiliaAdvancements.has(player, "fell_through")) { "no Fell Through" }
        check(AmbientMedia.get(helper.level, chunk) == AmbientMedia.PULL / 2) { "the dream didn't drink half the air" }
        check(!farPig.isRemoved && farPig.level() == helper.level) { "a pig 8 blocks off was pulled along" }
        // Everyone pulled in arrives together
        check(Dream.isDreaming(friend) && friend.distanceTo(player) <= 3) { "the friend beside them didn't arrive with them" }
        Dream.wake(friend, Dream.Waking.OTHER)
        check(!DreamPull.pull(player)) { "pulled someone already dreaming" }
        // The pig lands where the dreamer did; a real player loads that ground, a mock one needs it forced
        check(pig.isRemoved) { "the pig beside them stayed behind" }
        val dream = Dream.level(player)!!
        val there = ChunkPos(player.blockPosition())
        dream.setChunkForced(there.x, there.z, true)
        helper.runAfterDelay(20) {
            val pulledPig = dream.getEntity(pig.uuid)
            check(pulledPig != null && pulledPig.distanceTo(player) <= 3) { "the pig isn't beside them in the dream: $pulledPig" }
            // Waking, it comes back with them
            Dream.wake(player, Dream.Waking.OTHER)
            dream.setChunkForced(there.x, there.z, false)
            val home = helper.level.getEntity(pig.uuid)
            check(home != null && home.distanceTo(player) <= 3) { "the pig didn't come back: $home" }
            home!!.discard()
            farPig.discard()
            AmbientMedia.set(helper.level, chunk, 0)
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
    fun dyingInTheDreamKeepsEverything(helper: GameTestHelper) {
        val player = caster(helper)
        player.setGameMode(GameType.SURVIVAL)
        player.inventory.setItem(3, ItemStack(Items.AMETHYST_SHARD, 17))
        player.inventory.setItem(Inventory.SLOT_OFFHAND, ItemStack(Items.SHIELD))
        player.giveExperienceLevels(12)
        Dream.enter(player, null)
        player.hasChangedDimension()
        // Past the few seconds of protection players get after changing dimension
        helper.runAfterDelay(80) {
            val dream = player.serverLevel()
            val where = player.boundingBox.inflate(8.0)
            player.kill()
            check(player.isDeadOrDying) { "didn't die" }
            check(dream.getEntitiesOfClass(ItemEntity::class.java, where).isEmpty()) { "items dropped in the dream" }
            check(dream.getEntitiesOfClass(ExperienceOrb::class.java, where).isEmpty()) { "experience dropped in the dream" }
            val back = player.server.playerList.respawn(player, false)
            check(back.inventory.getItem(3).let { it.`is`(Items.AMETHYST_SHARD) && it.count == 17 }) { "shards lost: ${back.inventory.getItem(3)}" }
            check(back.offhandItem.`is`(Items.SHIELD)) { "offhand lost" }
            check(back.experienceLevel == 12) { "experience lost: level ${back.experienceLevel}" }
            check(back.hasEffect(MirabiliaEffects.CAME_BACK_WRONG.value)) { "didn't come back wrong" }
            helper.succeed()
        }
    }

    private fun caster(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.CREATIVE)
        val pos = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        player.moveTo(pos.x, pos.y, pos.z)
        return player
    }

    private companion object {
        // Apart from other tests: ambient media is per chunk, and neighbouring plots may share chunks
        const val BATCH = "mirabilia_ambient"
        const val DUST = MediaConstants.DUST_UNIT
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
