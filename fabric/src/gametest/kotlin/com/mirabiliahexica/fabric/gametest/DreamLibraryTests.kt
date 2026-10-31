package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.DreamLore
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.lens.LensImbuement
import com.mirabiliahexica.source.MediaSource
import com.mirabiliahexica.source.MediaSources
import com.mirabiliahexica.source.Tether
import com.mirabiliahexica.worldgen.DreamLibrary
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.phys.Vec3

/** Every hex on the listening post's shelves, cast as a player would after reading it. */
class DreamLibraryTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun eachListeningPostShelvesItsOwnMix(helper: GameTestHelper) {
        val ours = DreamLibrary.ENTRIES.map { it.name }.toSet()
        val picks = (1L..20L).map { DreamLibrary.pick(it * 7717) }
        for (shelves in picks) {
            check(shelves.size == DreamLibrary.SHELVED) { "shelved ${shelves.size} hexes" }
            check(shelves.map { it.name }.toSet().size == shelves.size) { "a hex shelved twice: ${shelves.map { it.name }}" }
            check(shelves.map { it.key.anglesSignature() }.toSet().size == shelves.size) { "two shelves share a key" }
            val mine = shelves.count { it.name in ours }
            check(mine in 2..4) { "expected 2 to 4 of ours, got $mine" }
            // Hex Casting's own hexes come through as lists of patterns
            for (shelf in shelves.filter { it.name !in ours }) {
                val hex = IotaType.deserialize(shelf.iotaTag, helper.level) as? ListIota
                check(hex != null && hex.list.size() > 0 && hex.list.all { it is PatternIota }) { "${shelf.name} didn't come through as a hex" }
            }
        }
        check(DreamLibrary.pick(42) .map { it.name } == DreamLibrary.pick(42).map { it.name }) { "the same post shelved different hexes" }
        check(picks.map { s -> s.map { it.name }.toSet() }.toSet().size > 10) { "listening posts barely differ" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun sightHexesLabelWhatTheyShould(helper: GameTestHelper) {
        val player = caster(helper)
        val pig = helper.spawnWithNoFreeWill(EntityType.PIG, Vec3(4.5, 2.0, 4.5))
        val zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3(5.5, 2.0, 1.5))

        val vitality = cast(player, "sight_of_vitality")
        val seen = pairedEntities(vitality)
        check(pig in seen && zombie in seen) { "Sight of Vitality missed something: $seen" }
        check(LensImbuement.labelsFrom(vitality.image.stack.last(), vitality.env).size == seen.size) { "lens can't show Sight of Vitality" }

        val danger = pairedEntities(cast(player, "sight_of_danger"))
        check(zombie in danger && pig !in danger) { "Sight of Danger should show the zombie only: $danger" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun wellspringHexesBindAndGauge(helper: GameTestHelper) {
        val player = caster(helper)
        // A chest straight ahead (south) of the caster's eyes
        val rel = BlockPos(1, 3, 4)
        helper.setBlock(rel, Blocks.CHEST)
        (helper.getBlockEntity(rel) as ChestBlockEntity).setItem(0, ItemStack(HexItems.AMETHYST_DUST, 7))
        player.moveTo(player.x, player.y, player.z, 0f, 0f)

        val bound = cast(player, "wellspring_at_gaze")
        val source = MediaSources.current(bound.env)
        check(source == MediaSource.Block(helper.absolutePos(rel))) { "Wellspring at Gaze bound $source" }

        // The gauge, cast in the same hex afterwards, reports the 7 dust at the caster's feet
        bound.queueExecuteAndWrapIotas(DreamLibrary.ENTRIES.single { it.name == "wellspring_gauge" }.hex, player.serverLevel())
        val pairs = (bound.image.stack.last() as ListIota).list.toList()
        val gauge = (pairs.single() as ListIota).list.toList()
        check((gauge[1] as DoubleIota).double == 7.0) { "the gauge read $gauge" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "mirabilia_dream")
    fun dreamStairRisesAheadAndTunnelBoresAlongTheGaze(helper: GameTestHelper) {
        val player = caster(helper)
        player.setGameMode(GameType.SURVIVAL)
        // Its hexes are built of dream spells, which must be found first
        DreamLore.SPELLS.forEach { DreamLore.teach(player, it) }
        Dream.enter(player, null)
        val dream = player.serverLevel()
        val feet = player.blockPosition()

        // Facing roughly south and looking a little down: the stair still runs straight south
        player.moveTo(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5, 20f, 35f)
        for (pos in BlockPos.betweenClosed(feet.offset(-1, 0, 1), feet.offset(1, 9, 9))) dream.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
        check(cast(player, "dream_stair").image.stack.isEmpty()) { "Dream Stair left something on the stack" }
        for (step in 1..8) {
            val pos = feet.offset(0, step - 1, step)
            check(!dream.getBlockState(pos).isAir) { "step $step missing at $pos" }
            check(dream.getBlockState(pos.above()).isAir) { "no headroom over step $step" }
        }

        // Facing west, level, into a solid wall of glass: a tunnel sixteen long at body height
        player.moveTo(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5, 90f, 0f)
        val glass = MirabiliaBlocks.DREAM_GLASS.value.defaultBlockState()
        for (pos in BlockPos.betweenClosed(feet.offset(-17, -1, -2), feet.offset(-1, 3, 2))) dream.setBlock(pos, glass, 2)
        cast(player, "dream_tunnel")
        for (k in 1..16) {
            val pos = feet.offset(-k, 1, 0)
            check(dream.getBlockState(pos).isAir) { "tunnel blocked at $k blocks: $pos" }
        }
        Dream.wake(player, Dream.Waking.OTHER)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun everyWakingHexCasts(helper: GameTestHelper) {
        // The dream's own hexes are tested in the dream; the wellspring one with its chest, above
        val skip = setOf("dream_stair", "dream_tunnel", "wellspring_at_gaze", "wellspring_gauge")
        for (entry in DreamLibrary.ENTRIES.filter { it.name !in skip }) {
            val player = caster(helper)
            // Looking straight down at the block underfoot (stone, or a chest for the tether); the movement
            // hexes look level instead, or they'd send the caster into the floor
            val level = entry.name in setOf("blink_forward", "leap")
            player.moveTo(player.x, player.y, player.z, 0f, if (level) 0f else 90f)
            val below = BlockPos(1, 1, 1)
            helper.setBlock(below, if (entry.name == "tether_at_gaze") Blocks.CHEST else Blocks.STONE)
            if (entry.name == "tether_at_gaze") player.setItemInHand(InteractionHand.OFF_HAND, ItemStack(HexItems.SCRYING_LENS))
            val vm = cast(player, entry.name)
            when (entry.name) {
                "break_gaze" -> check(helper.getBlockState(below).isAir) { "Break Gaze didn't break the block" }
                "light_gaze" -> check(!helper.getBlockState(below.above()).isAir) { "Light Gaze made no light" }
                "tether_at_gaze" -> check(Tether.of(player.offhandItem) != null) { "Tether at Gaze didn't tether the lens" }
                "sight_of_players", "sight_of_stealth" -> check(player in pairedEntities(vm)) { "${entry.name} missed the caster" }
            }
            player.discard()
            helper.setBlock(below.above(), Blocks.AIR)
        }
        helper.succeed()
    }

    private fun caster(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        player.setGameMode(GameType.CREATIVE)
        val pos = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        player.moveTo(pos.x, pos.y, pos.z)
        return player
    }

    private fun cast(player: ServerPlayer, name: String): CastingVM {
        val entry = DreamLibrary.ENTRIES.single { it.name == name }
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        val view = vm.queueExecuteAndWrapIotas(entry.hex, player.serverLevel())
        check(view.resolutionType != ResolvedPatternType.ERRORED && view.resolutionType != ResolvedPatternType.INVALID) {
            "$name mishapped: ${view.resolutionType}"
        }
        return vm
    }

    private fun pairedEntities(vm: CastingVM) = (vm.image.stack.last() as ListIota).list.map { pair ->
        ((pair as ListIota).list.first() as EntityIota).entity
    }.toSet()

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
