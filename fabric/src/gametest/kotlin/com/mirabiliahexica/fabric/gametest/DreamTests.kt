package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.common.lib.HexBlocks
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.casting.actions.spells.OpWill
import com.mirabiliahexica.client.DreamerMutation
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.DreamSpawner
import com.mirabiliahexica.dream.DreamTicker
import com.mirabiliahexica.dream.DreamVisits
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaEffects
import com.mirabiliahexica.registry.MirabiliaFeatures
import com.mirabiliahexica.registry.MirabiliaTags
import com.mirabiliahexica.registry.RegistrarEntry
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.resources.ResourceKey
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.util.RandomSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.phys.Vec3
import java.util.UUID
import com.mirabiliahexica.dream.DreamLore

/** Dreamers are teleported away from their plots, so these run in a batch of their own. */
class DreamTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 400)
    fun dreamGroundIsDreamStuff(helper: GameTestHelper) {
        val dream = helper.level.server.getLevel(Dream.LEVEL) ?: throw GameTestAssertException("no Tanako's World")
        val dreamBlocks = setOf(
            MirabiliaBlocks.DREAM_GLASS.value, MirabiliaBlocks.DUSKY_DREAM_GLASS.value,
            MirabiliaBlocks.DEEP_DREAM_GLASS.value, MirabiliaBlocks.MARBLE.value, MirabiliaBlocks.MARBLE_PEBBLES.value,
        )
        repeat(4) {
            val spot = Dream.arrival(dream)
            val ground = dream.getBlockState(BlockPos.containing(spot).below()).block
            // Geodes can surface too; anything vanilla-overworldly means the noise settings didn't apply
            check(ground in dreamBlocks || ground == Blocks.CALCITE || ground == Blocks.AMETHYST_BLOCK || ground == Blocks.SMOOTH_BASALT) {
                "arrived on $ground at $spot"
            }
            check(dream.getBlockState(BlockPos.containing(spot)).isAir) { "arrived inside a block at $spot" }
        }
        // No water anywhere: a column straight down from the arrival point is glass, marble or air
        val column = BlockPos.containing(Dream.arrival(dream))
        for (y in dream.minBuildHeight until column.y) {
            val state = dream.getBlockState(column.atY(y))
            check(state.fluidState.isEmpty) { "found ${state.block} at y=$y" }
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun wakePatternBringsDreamerHome(helper: GameTestHelper) {
        val player = player(helper)
        val home = player.position()
        check(Dream.enter(player, null)) { "couldn't enter the dream" }
        check(Dream.isDreaming(player)) { "not in the dream after entering" }
        // Hexes reach nothing there, not even the caster's own feet
        check(!env(player).isVecInRange(player.position())) { "a hex reached something in the dream" }

        check(castWake(player) == ResolvedPatternType.EVALUATED) { "Wake failed in the dream" }
        check(!Dream.isDreaming(player)) { "still dreaming after Wake" }
        check(player.level() == helper.level && player.position().distanceTo(home) < 0.01) {
            "woke at ${player.position()} in ${player.level().dimension().location()}, expected $home"
        }
        check(DreamVisits.get(player.server)[player.uuid] == null) { "visit outlived the dream" }
        check(castWake(player) == ResolvedPatternType.ERRORED) { "Wake should fail when awake" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun willingPatternsShapeTheDream(helper: GameTestHelper) {
        val player = player(helper)
        player.setGameMode(GameType.SURVIVAL)
        Dream.enter(player, null)
        val dream = player.serverLevel()
        val ground = player.blockPosition().below()
        val groundBlock = dream.getBlockState(ground).block
        val visit = DreamVisits.get(player.server)[player.uuid]!!
        // Somewhere beside the dreamer, so the raised ground doesn't land on their feet
        val spotGround = listOf(4 to 0, -4 to 0, 0 to 4, 0 to -4, 5 to 5).map { (dx, dz) ->
            val x = ground.x + dx
            val z = ground.z + dz
            BlockPos(x, dream.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z)
        }.first { dream.getBlockState(it).`is`(MirabiliaTags.DREAM_SHAPEABLE) && it.distSqr(ground) < 14 * 14 }

        val coldBefore = visit.cold
        check(cast(player, MirabiliaActions.WILL_UPWARD, spotGround) == ResolvedPatternType.EVALUATED) { "Will Upward failed" }
        check(dream.getBlockState(spotGround.above()).block == dream.getBlockState(spotGround).block) { "ground didn't rise at $spotGround" }
        check(visit.cold >= coldBefore + OpWill.COLD) { "willing should chill the dreamer" }

        check(cast(player, MirabiliaActions.WILL_APART, spotGround) == ResolvedPatternType.EVALUATED) { "Will Apart failed" }
        check(dream.getBlockState(spotGround).isAir && dream.getBlockState(spotGround.above()).isAir) { "ground didn't part at $spotGround" }

        check(cast(player, MirabiliaActions.WILL_UPWARD, ground.offset(40, 0, 0)) == ResolvedPatternType.ERRORED) { "willing beyond reach should fail" }
        check(dream.getBlockState(ground).block == groundBlock) { "the dreamer's own footing changed" }
        // Dreamer's Gaze finds the block being looked at: straight down, the dreamer's own footing
        player.moveTo(player.x, player.y, player.z, 0f, 90f)
        val gazeVm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        gazeVm.queueExecuteAndWrapIota(GreatSpells.patternFor(player, MirabiliaActions.DREAM_GAZE), dream)
        val seen = (gazeVm.image.stack.lastOrNull() as? Vec3Iota)?.vec3
        check(seen != null && BlockPos.containing(seen) == ground) { "gaze saw $seen, expected the block underfoot $ground" }

        Dream.wake(player, Dream.Waking.OTHER)
        check(cast(player, MirabiliaActions.WILL_APART, player.blockPosition().below()) == ResolvedPatternType.ERRORED) { "willing awake should fail" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun lostDreamersComeMoreOftenOverTime(helper: GameTestHelper) {
        check(DreamSpawner.chance(0) < DreamSpawner.chance(5 * 1200)) { "spawn chance should grow" }
        check(DreamSpawner.chance(10 * 1200) == DreamSpawner.chance(100 * 1200)) { "spawn chance should level off" }
        check(DreamSpawner.cap(0) == 1 && DreamSpawner.cap(100 * 1200) == 6) { "cap should run from 1 to 6" }

        val player = player(helper)
        player.setGameMode(GameType.SURVIVAL)
        Dream.enter(player, null)
        // Real players keep the chunks around them loaded; mock ones don't
        val centre = player.chunkPosition()
        for (dx in -3..3) for (dz in -3..3) player.serverLevel().getChunk(centre.x + dx, centre.z + dz)
        val dreamer = (1..20).firstNotNullOfOrNull { DreamSpawner.spawnNear(player) }
            ?: throw GameTestAssertException("no Lost Dreamer could be placed around ${player.position()}")
        // 16–32 blocks across the ground; hills can add some height
        val distance = Math.hypot(dreamer.x - player.x, dreamer.z - player.z)
        check(distance in 15.0..33.0) { "spawned $distance blocks away" }
        check(!dreamer.isBaby && dreamer.vehicle == null) { "spawned a baby or a jockey" }
        check(dreamer.mainHandItem.isEmpty) { "spawned holding ${dreamer.mainHandItem}" }
        check(dreamer.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE) == 0.0) { "would call reinforcements" }
        check(DreamSpawner.nearby(player) >= 1) { "the new dreamer isn't counted" }

        val loot = player.server.lootData.getLootTable(dreamer.lootTable)
        check(loot !== LootTable.EMPTY) { "no loot table at ${dreamer.lootTable}" }

        // Past curing: weakness and a golden apple do nothing
        dreamer.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 600))
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.GOLDEN_APPLE))
        dreamer.mobInteract(player, InteractionHand.MAIN_HAND)
        check(!dreamer.isConverting) { "a Lost Dreamer started turning back into a villager" }
        check(player.mainHandItem.count == 1) { "the golden apple was used up" }
        dreamer.discard()
        Dream.wake(player, Dream.Waking.OTHER)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun dreamerMutationsAreStableAndVaried(helper: GameTestHelper) {
        val uuids = (1..600).map { UUID.randomUUID() }
        val picks = uuids.map { DreamerMutation.of(it) }
        check(uuids.zip(picks).all { (uuid, set) -> DreamerMutation.of(UUID(uuid.mostSignificantBits, uuid.leastSignificantBits)) == set }) {
            "the same creature changed shape"
        }
        check(picks.all { set -> DreamerMutation.Slot.entries.all { slot -> set.count { it.slot == slot } <= 1 } }) {
            "more than one change to the same slot"
        }
        val unchanged = picks.count { it.isEmpty() }
        check(unchanged in 60..150) { "expected about 1 in 6 unchanged, got $unchanged of 600" }
        val seen = picks.flatten().toSet()
        check(seen == DreamerMutation.entries.toSet()) { "never picked ${DreamerMutation.entries - seen}" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun pillarsAreAllDifferent(helper: GameTestHelper) {
        val dream = helper.level.server.getLevel(Dream.LEVEL)!!
        val origin = BlockPos.containing(Dream.arrival(dream))
        val generator = dream.chunkSource.generator
        val shapes = (0 until 6).map { i ->
            // Side by side, far enough apart not to touch
            val at = origin.offset(i * 40, 0, 0)
            dream.getChunk(at)
            val top = BlockPos(at.x, dream.getHeight(Heightmap.Types.WORLD_SURFACE, at.x, at.z), at.z)
            val random = RandomSource.create(1000L + i)
            MirabiliaFeatures.DREAM_PILLAR.value.place(NoneFeatureConfiguration.INSTANCE, dream, generator, random, top)
            // The pillar's footprint: count marble and deep glass above the old ground, column by column
            BlockPos.betweenClosed(top.offset(-14, 0, -14), top.offset(14, 30, 14)).count {
                val state = dream.getBlockState(it)
                state.`is`(MirabiliaBlocks.MARBLE.value) || state.`is`(MirabiliaBlocks.DEEP_DREAM_GLASS.value)
            }
        }
        check(shapes.any { it > 0 }) { "no pillar placed anything" }
        check(shapes.toSet().size >= 4) { "pillars look alike: $shapes" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun noSnowInTheDream(helper: GameTestHelper) {
        val dream = helper.level.server.getLevel(Dream.LEVEL)!!
        val spot = BlockPos.containing(Dream.arrival(dream))
        check(!dream.getBiome(spot).value().shouldSnow(dream, spot)) { "snow could settle in the dream" }

        // Old snow from before the biomes were warm melts around a dreamer
        val player = player(helper)
        Dream.enter(player, null)
        val centre = player.blockPosition()
        val snowed = (-3..3).flatMap { dx -> (-3..3).map { dz -> centre.offset(dx, 0, dz) } }.mapNotNull { column ->
            val top = BlockPos(column.x, dream.getHeight(Heightmap.Types.WORLD_SURFACE, column.x, column.z), column.z)
            top.takeIf { dream.getBlockState(it).isAir }?.also { dream.setBlock(it, Blocks.SNOW.defaultBlockState(), 2) }
        }
        // Just the melting, not the whole dream tick (which would also chill the other tests' dreamers);
        // each column is checked about once every 500 ticks, so this many leaves almost no chance of a miss
        repeat(20000) { if (snowed.any { dream.getBlockState(it).`is`(Blocks.SNOW) }) DreamTicker.meltOldSnow(player) }
        check(snowed.none { dream.getBlockState(it).`is`(Blocks.SNOW) }) { "old snow didn't melt" }
        Dream.wake(player, Dream.Waking.OTHER)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun crouchingStillWakes(helper: GameTestHelper) {
        val player = player(helper)
        player.setGameMode(GameType.CREATIVE)
        Dream.enter(player, null)
        player.isShiftKeyDown = true
        repeat(DreamTicker.STILL_TICKS) { if (Dream.isDreaming(player)) DreamTicker.tick(player.server) }
        check(!Dream.isDreaming(player)) { "still dreaming after crouching still" }
        player.isShiftKeyDown = false
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun coldThrowsDreamerOut(helper: GameTestHelper) {
        val player = player(helper)
        player.setGameMode(GameType.SURVIVAL)
        player.health = 20f
        Dream.enter(player, null)
        // A real client confirms the dimension change; until then players can't be hurt
        player.hasChangedDimension()
        val visit = DreamVisits.get(player.server)[player.uuid]!!
        visit.cold = DreamTicker.MAX_COLD / 2
        DreamTicker.tick(player.server)
        check(player.ticksFrozen in 60..70) { "half-frozen should show about half frost, got ${player.ticksFrozen}" }

        // Past the few seconds of protection players get after changing dimension
        helper.runAfterDelay(80) {
            visit.cold = DreamTicker.MAX_COLD - 1
            DreamTicker.tick(player.server)
            check(!Dream.isDreaming(player)) { "a frozen dreamer should be thrown out" }
            check(player.health < 20f) { "being thrown out by the cold should hurt" }
            check(player.ticksFrozen == 0) { "frost should melt on waking" }
            helper.succeed()
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun cameBackWrongSometimesSilencesHexes(helper: GameTestHelper) {
        val player = player(helper)
        player.addEffect(MobEffectInstance(MirabiliaEffects.CAME_BACK_WRONG.value, 200))
        val silenced = (1..400).count { !env(player).isVecInRange(player.position()) }
        // 20% of 400 casts; generous bounds so the test isn't flaky
        check(silenced in 40..130) { "expected about 80 of 400 casts silenced, got $silenced" }
        player.removeAllEffects()
        check((1..50).none { !env(player).isVecInRange(player.position()) }) { "cured caster still misfires" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun akashicRecordNearBedIsFound(helper: GameTestHelper) {
        val bed = helper.absolutePos(BlockPos(1, 2, 1))
        check(!Dream.recordNear(helper.level, bed)) { "found a record that isn't there" }
        helper.setBlock(BlockPos(4, 2, 4), HexBlocks.AKASHIC_RECORD)
        check(Dream.recordNear(helper.level, bed)) { "missed the record 3 blocks away" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun shrineTemplateAndLootLoad(helper: GameTestHelper) {
        val server = helper.level.server
        val template = server.structureManager.get(Mirabilia.id("dream/shrine"))
            .orElseThrow { GameTestAssertException("no shrine template") }
        check(template.size.x == 11 && template.size.y == 7) { "unexpected shrine size ${template.size}" }
        val loot = server.lootData.getLootTable(Mirabilia.id("chests/dream_shrine"))
        check(loot !== LootTable.EMPTY) { "shrine loot table didn't load" }
        val structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE)
        check(structures.containsKey(Mirabilia.id("dream_shrine"))) { "shrine structure isn't registered" }

        // And the dream can place them. (The GameTest world has structure generation switched off, so ask the
        // structure directly whether it would start at the spots its structure set offers.)
        val dream = server.getLevel(Dream.LEVEL)!!
        val shrine = structures.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, Mirabilia.id("dream_shrine")))
        val set = dream.chunkSource.generatorState.possibleStructureSets().single { it.`is`(Mirabilia.id("dream_shrines")) }
        val placement = set.value().placement() as RandomSpreadStructurePlacement
        val generator = dream.chunkSource.generator
        val valid = (0 until 4).count { region ->
            val chunk = placement.getPotentialStructureChunk(dream.seed, region * placement.spacing(), 0)
            val context = Structure.GenerationContext(
                server.registryAccess(), generator, generator.biomeSource, dream.chunkSource.randomState(),
                server.structureManager, dream.seed, chunk, dream,
            ) { shrine.value().biomes().contains(it) }
            shrine.value().findValidGenerationPoint(context).isPresent
        }
        check(valid > 0) { "the shrine wouldn't start in any of 4 candidate spots" }
        helper.succeed()
    }

    private fun player(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        val pos = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        player.moveTo(pos.x, pos.y, pos.z)
        // These tests are about what the spells do, not finding them
        DreamLore.SPELLS.forEach { DreamLore.teach(player, it) }
        return player
    }

    private fun env(player: ServerPlayer) = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND)).env

    private fun cast(player: ServerPlayer, entry: RegistrarEntry<ActionRegistryEntry>, target: BlockPos): ResolvedPatternType {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = listOf(Vec3Iota(Vec3.atCenterOf(target))))
        return vm.queueExecuteAndWrapIota(GreatSpells.patternFor(player, entry), player.serverLevel()).resolutionType
    }

    private fun castWake(player: ServerPlayer): ResolvedPatternType {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        return vm.queueExecuteAndWrapIota(GreatSpells.patternFor(player, MirabiliaActions.WAKE), player.serverLevel()).resolutionType
    }

    private companion object {
        const val BATCH = "mirabilia_dream"
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
