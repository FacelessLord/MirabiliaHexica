package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.BooleanIota
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.common.lib.hex.HexActions
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.RegistrarEntry
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.phys.Vec3

/**
 * Casts the mod's real patterns through Hex Casting's VM, as a creative-mode player holding a staff would.
 * Run with `gradlew :fabric:runGametest`.
 */
class PatternTests {
    // region frost

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun freezeTurnsWaterToIceAndLaysSnow(helper: GameTestHelper) {
        buildPond(helper)
        val player = caster(helper)

        castFrost(helper, player, MirabiliaActions.FREEZE)
        helper.assertBlockPresent(Blocks.ICE, POND)
        helper.assertBlockProperty(SHORE.above(), SnowLayerBlock.LAYERS, 1)

        // A second cast adds a layer on top of the first
        castFrost(helper, player, MirabiliaActions.FREEZE)
        helper.assertBlockProperty(SHORE.above(), SnowLayerBlock.LAYERS, 2)
        // Ice can't hold snow (vanilla rule)
        helper.assertBlockPresent(Blocks.AIR, POND.above())
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun freezeStopsAtFullSnow(helper: GameTestHelper) {
        buildPond(helper)
        helper.setBlock(SHORE.above(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 8))
        val player = caster(helper)

        castFrost(helper, player, MirabiliaActions.FREEZE)
        helper.assertBlockProperty(SHORE.above(), SnowLayerBlock.LAYERS, 8)
        helper.assertBlockPresent(Blocks.AIR, SHORE.above(2))
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun thawReversesFreeze(helper: GameTestHelper) {
        buildPond(helper)
        helper.setBlock(POND, Blocks.ICE)
        helper.setBlock(SHORE.above(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2))
        helper.setBlock(OTHER_SHORE.above(), Blocks.SNOW.defaultBlockState())
        helper.setBlock(SNOW_BLOCK_POS, Blocks.SNOW_BLOCK)
        val player = caster(helper)
        // A frozen creature in reach is warmed, enough to stop the freeze damage
        val pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, SHORE.above(2))
        pig.ticksFrozen = pig.ticksRequiredToFreeze

        castFrost(helper, player, MirabiliaActions.THAW)
        check(helper, pig.ticksFrozen == pig.ticksRequiredToFreeze - com.mirabiliahexica.config.Tuning.Spells.thawFrost) { "pig's frost is ${pig.ticksFrozen}" }
        check(helper, !pig.isFullyFrozen) { "the pig is still fully frozen" }
        pig.discard()
        helper.assertBlockPresent(Blocks.WATER, POND)
        helper.assertBlockProperty(SHORE.above(), SnowLayerBlock.LAYERS, 1)
        helper.assertBlockPresent(Blocks.AIR, OTHER_SHORE.above())
        helper.assertBlockProperty(SNOW_BLOCK_POS, SnowLayerBlock.LAYERS, 7)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun freezeOutsideAmbitMishaps(helper: GameTestHelper) {
        buildPond(helper)
        val player = caster(helper)
        // Ambit is 32 blocks; aim well past it
        player.teleportTo(player.x + 100, player.y, player.z)

        val result = cast(player,
            listOf(Vec3Iota(helper.absoluteVec(Vec3.atCenterOf(POND))), DoubleIota(RADIUS)), pattern(MirabiliaActions.FREEZE))
        check(helper, result.resolution == ResolvedPatternType.ERRORED) { "expected a mishap, got $result" }
        helper.assertBlockPresent(Blocks.WATER, POND)
        helper.succeed()
    }

    // endregion

    // region entity states

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun sneakingReflectsShiftKey(helper: GameTestHelper) {
        val player = caster(helper)

        player.isShiftKeyDown = false
        check(helper, castQuery(player, player, MirabiliaActions.SNEAKING) == false) { "standing player reported as sneaking" }

        player.isShiftKeyDown = true
        check(helper, castQuery(player, player, MirabiliaActions.SNEAKING) == true) { "sneaking player not detected" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun jumpingNeedsRisingAirborneEntity(helper: GameTestHelper) {
        val player = caster(helper)
        val pig = helper.spawn(EntityType.PIG, Vec3(2.5, 4.0, 2.5))
        pig.setNoAi(true)

        pig.setOnGround(false)
        pig.deltaMovement = Vec3(0.0, 0.4, 0.0)
        check(helper, castQuery(player, pig, MirabiliaActions.JUMPING) == true) { "rising pig not detected as jumping" }

        pig.deltaMovement = Vec3(0.0, -0.3, 0.0)
        check(helper, castQuery(player, pig, MirabiliaActions.JUMPING) == false) { "falling pig reported as jumping" }

        pig.setOnGround(true)
        pig.deltaMovement = Vec3(0.0, 0.4, 0.0)
        check(helper, castQuery(player, pig, MirabiliaActions.JUMPING) == false) { "grounded pig reported as jumping" }
        helper.succeed()
    }

    // endregion

    // region registry

    /** Guards against a signature collision silently replacing one of Hex's patterns or ours. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun signaturesAreUnique(helper: GameTestHelper) {
        val ours = MirabiliaActions.entries.associate { it.value.prototype.anglesSignature() to it.id }
        check(helper, ours.size == MirabiliaActions.entries.size) { "two Mirabilia patterns share a signature" }
        for ((id, entry) in HexActions.REGISTRY.entrySet().map { it.key.location() to it.value }) {
            if (id.namespace == "mirabilia") continue
            val clash = ours[entry.prototype.anglesSignature()]
            check(helper, clash == null) { "$clash has the same signature as $id" }
        }
        helper.succeed()
    }

    // endregion

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun axisPurificationSnapsToTheNearestAxis(helper: GameTestHelper) {
        val player = caster(helper)
        val cases = listOf(
            Vec3(0.3, -0.2, -0.9) to Vec3(0.0, 0.0, -1.0),
            Vec3(-5.0, 4.9, 1.0) to Vec3(-1.0, 0.0, 0.0),
            Vec3(0.1, -0.8, 0.2) to Vec3(0.0, -1.0, 0.0),
            // Ties stay on the ground: x before z, both before y
            Vec3(1.0, 1.0, 1.0) to Vec3(1.0, 0.0, 0.0),
            Vec3(0.0, 2.0, -2.0) to Vec3(0.0, 0.0, -1.0),
            Vec3.ZERO to Vec3.ZERO,
        )
        for ((input, expected) in cases) {
            val result = cast(player, listOf(Vec3Iota(input)), pattern(MirabiliaActions.SNAP_TO_AXIS))
            val got = (result.stack.single() as Vec3Iota).vec3
            if (got != expected) throw GameTestAssertException("$input snapped to $got, expected $expected")
        }
        helper.succeed()
    }

    private data class CastResult(val resolution: ResolvedPatternType, val stack: List<Iota>) {
        override fun toString() = "$resolution, stack=${stack.map { it.display().string }}"
    }

    /** Puts [args] on the stack (as if escaped or produced by earlier patterns), then draws [pattern]. */
    private fun cast(player: ServerPlayer, args: List<Iota>, pattern: PatternIota): CastResult {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = args)
        val view = vm.queueExecuteAndWrapIota(pattern, player.serverLevel())
        return CastResult(view.resolutionType, vm.image.stack)
    }

    private fun castFrost(helper: GameTestHelper, player: ServerPlayer, spell: RegistrarEntry<ActionRegistryEntry>) {
        val result = cast(player,
            listOf(Vec3Iota(helper.absoluteVec(Vec3.atCenterOf(POND))), DoubleIota(RADIUS)), pattern(spell))
        check(helper, result.resolution == ResolvedPatternType.EVALUATED) { "${spell.id} failed: $result" }
    }

    private fun castQuery(player: ServerPlayer, target: net.minecraft.world.entity.Entity,
                          query: RegistrarEntry<ActionRegistryEntry>): Boolean? {
        val result = cast(player, listOf(EntityIota(target)), pattern(query))
        return (result.stack.lastOrNull() as? BooleanIota)?.bool
    }

    private fun pattern(entry: RegistrarEntry<ActionRegistryEntry>) = PatternIota(entry.value.prototype)

    /** A creative player (unlimited media) standing over the test area. */
    private fun caster(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.CREATIVE)
        val pos = helper.absoluteVec(Vec3(2.5, 4.0, 2.5))
        player.teleportTo(pos.x, pos.y, pos.z)
        return player
    }

    /** Stone floor at y=1 with a single water source in the middle, sitting on stone. */
    private fun buildPond(helper: GameTestHelper) {
        for (x in 0..4) for (z in 0..4) {
            helper.setBlock(x, 0, z, Blocks.STONE)
            helper.setBlock(x, 1, z, Blocks.STONE)
        }
        helper.setBlock(POND, Blocks.WATER)
    }

    private inline fun check(helper: GameTestHelper, condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }

    companion object {
        private val POND = BlockPos(2, 1, 2)
        private val SHORE = BlockPos(1, 1, 2)
        private val OTHER_SHORE = BlockPos(3, 1, 2)
        private val SNOW_BLOCK_POS = BlockPos(2, 2, 1)
        private const val RADIUS = 2.0
    }
}
