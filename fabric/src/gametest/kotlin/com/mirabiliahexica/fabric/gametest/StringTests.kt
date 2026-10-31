package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.BooleanIota
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import com.mirabiliahexica.MirabiliaConfig
import com.mirabiliahexica.casting.iota.StringIota
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.MirabiliaArithmetics
import com.mirabiliahexica.registry.RegistrarEntry
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.phys.Vec3

class StringTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun healthPurificationsReadLivingThingsOnly(helper: GameTestHelper) {
        val player = caster(helper)
        val pig = helper.spawn(EntityType.PIG, Vec3(1.5, 2.0, 1.5))
        pig.setNoAi(true)
        pig.health = 7f
        val pos = helper.absoluteVec(Vec3(3.5, 2.0, 3.5))
        val item = ItemEntity(helper.level, pos.x, pos.y, pos.z, ItemStack(Items.STICK)).also { helper.level.addFreshEntity(it) }

        check(number(top(player, listOf(EntityIota(pig)), MirabiliaActions.HEALTH)) == 7.0) { "pig health wrong" }
        check(number(top(player, listOf(EntityIota(pig)), MirabiliaActions.MAX_HEALTH)) == 10.0) { "pig max health wrong" }
        check(top(player, listOf(EntityIota(item)), MirabiliaActions.HEALTH) is NullIota) { "an item has no health" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun stringPatternsAreOnByDefaultWithoutMoreIotas(helper: GameTestHelper) {
        check(MirabiliaConfig.stringPatterns) { "string patterns should default to on when MoreIotas is absent" }
        check(MirabiliaActions.STRING_NAME != null) { "string patterns weren't registered" }
        check(MirabiliaArithmetics.STRING != null) { "string arithmetic wasn't registered" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun stringPatternsMakeAndReadStrings(helper: GameTestHelper) {
        val player = caster(helper)
        val pig = helper.spawn(EntityType.PIG, Vec3(1.5, 2.0, 1.5))
        pig.customName = Component.literal("Wilbur")

        check(string(top(player, listOf(EntityIota(pig)), MirabiliaActions.STRING_NAME!!)) == "Wilbur") { "name wrong" }
        // Numbers are written plainly, whatever the machine's locale
        check(string(top(player, listOf(DoubleIota(7.0)), MirabiliaActions.STRING_FROM!!)) == "7") { "7 should read as \"7\"" }
        check(string(top(player, listOf(DoubleIota(2.5)), MirabiliaActions.STRING_FROM!!)) == "2.5") { "2.5 should read as \"2.5\"" }

        val split = top(player, listOf(StringIota("a,b,,c"), StringIota(",")), MirabiliaActions.STRING_SPLIT!!)
        check(split is ListIota && split.list.toList().map(::string) == listOf("a", "b", "", "c")) { "split wrong: $split" }
        check(number(top(player, listOf(StringIota(" 12.5 ")), MirabiliaActions.STRING_PARSE!!)) == 12.5) { "parse wrong" }
        check(top(player, listOf(StringIota("twelve")), MirabiliaActions.STRING_PARSE!!) is NullIota) { "non-number should parse to null" }

        // A non-string where a string is needed is a mishap, not a crash
        val (bad, _) = cast(player, listOf(DoubleIota(1.0), StringIota(",")), MirabiliaActions.STRING_SPLIT!!)
        check(bad == ResolvedPatternType.ERRORED) { "splitting a number should mishap, got $bad" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun hexArithmeticPatternsWorkOnStrings(helper: GameTestHelper) {
        val player = caster(helper)
        // Additive Distillation: string + string, and string + anything else
        check(string(hex(player, listOf(StringIota("ab"), StringIota("cd")), Arithmetic.ADD)) == "abcd") { "string + string" }
        check(string(hex(player, listOf(StringIota("HP "), DoubleIota(7.0)), Arithmetic.ADD)) == "HP 7") { "string + number" }
        check(string(hex(player, listOf(DoubleIota(7.0), StringIota(" HP")), Arithmetic.ADD)) == "7 HP") { "number + string" }
        // Numbers still add as numbers
        check(number(hex(player, listOf(DoubleIota(2.0), DoubleIota(3.0)), Arithmetic.ADD)) == 5.0) { "number addition broke" }

        check(number(hex(player, listOf(StringIota("hello")), Arithmetic.ABS)) == 5.0) { "length" }
        check(string(hex(player, listOf(StringIota("hello"), DoubleIota(1.0)), Arithmetic.INDEX)) == "e") { "index" }
        check(hex(player, listOf(StringIota("hello"), DoubleIota(9.0)), Arithmetic.INDEX) is NullIota) { "index out of range" }
        check(string(hex(player, listOf(StringIota("hello"), DoubleIota(1.0), DoubleIota(3.0)), Arithmetic.SLICE)) == "el") { "slice" }
        check(string(hex(player, listOf(StringIota("hello"), DoubleIota(3.0), DoubleIota(1.0)), Arithmetic.SLICE)) == "el") { "slice, reversed indices" }
        check(string(hex(player, listOf(StringIota("abc")), Arithmetic.REV)) == "cba") { "reverse" }
        check(number(hex(player, listOf(StringIota("hello"), StringIota("ll")), Arithmetic.INDEX_OF)) == 2.0) { "index of" }
        check(number(hex(player, listOf(StringIota("hello"), StringIota("z")), Arithmetic.INDEX_OF)) == -1.0) { "index of, missing" }

        // As with lists, slice indices past the end are a mishap
        val (bad, _) = castPattern(player, listOf(StringIota("hi"), DoubleIota(0.0), DoubleIota(9.0)), Arithmetic.SLICE)
        check(bad == ResolvedPatternType.ERRORED) { "out-of-range slice should mishap, got $bad" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun blockNamesAndStringComparisons(helper: GameTestHelper) {
        val player = caster(helper)
        val logPos = BlockPos(1, 1, 1)
        helper.setBlock(logPos, Blocks.OAK_LOG)
        check(string(top(player, listOf(Vec3Iota(helper.absoluteVec(Vec3.atCenterOf(logPos)))), MirabiliaActions.STRING_BLOCK_NAME!!)) == "Oak Log") {
            "block name wrong"
        }

        fun same(a: String, b: String, entry: RegistrarEntry<ActionRegistryEntry>) =
            (top(player, listOf(StringIota(a), StringIota(b)), entry) as? BooleanIota)?.bool
                ?: throw GameTestAssertException("${entry.id} didn't give a boolean")
        check(same(" Wilbur ", "wilbur", MirabiliaActions.STRING_EQUALS!!)) { "lenient should ignore case and edge spaces" }
        check(!same("Wilbur", "Wilbur2", MirabiliaActions.STRING_EQUALS!!)) { "lenient matched different strings" }
        check(same("Wilbur", "Wilbur", MirabiliaActions.STRING_EQUALS_STRICT!!)) { "strict should match identical strings" }
        check(!same("Wilbur", "wilbur", MirabiliaActions.STRING_EQUALS_STRICT!!)) { "strict should care about case" }

        // Hex's Maximus/Minimus Distillation: alphabetical, case only breaking ties
        fun order(a: String, b: String, pattern: HexPattern) =
            (hex(player, listOf(StringIota(a), StringIota(b)), pattern) as? BooleanIota)?.bool
                ?: throw GameTestAssertException("$pattern didn't give a boolean")
        check(order("apple", "Banana", Arithmetic.LESS)) { "apple < Banana" }
        check(order("banana", "Banana", Arithmetic.GREATER)) { "banana > Banana (tie broken by case)" }
        check(order("same", "same", Arithmetic.LESS_EQ) && order("same", "same", Arithmetic.GREATER_EQ)) { "<= and >= on equal strings" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun masonTellsSolidBlocksApart(helper: GameTestHelper) {
        val player = caster(helper)
        helper.setBlock(1, 1, 1, Blocks.STONE)
        helper.setBlock(1, 1, 2, Blocks.GLASS)
        helper.setBlock(2, 1, 1, Blocks.STONE)
        helper.setBlock(2, 2, 1, Blocks.TORCH)
        helper.setBlock(3, 1, 1, Blocks.WATER)
        fun solid(x: Int, y: Int, z: Int) =
            (top(player, listOf(Vec3Iota(helper.absoluteVec(Vec3(x + 0.5, y + 0.5, z + 0.5)))), MirabiliaActions.BLOCK_SOLID) as? BooleanIota)?.bool
                ?: throw GameTestAssertException("Mason's Purification didn't give a boolean")
        check(solid(1, 1, 1)) { "stone should be solid" }
        check(solid(1, 1, 2)) { "glass should be solid" }
        check(!solid(1, 3, 1)) { "air shouldn't be solid" }
        check(!solid(2, 2, 1)) { "a torch shouldn't be solid" }
        check(!solid(3, 1, 1)) { "water shouldn't be solid" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun inscriptionReadsASign(helper: GameTestHelper) {
        val player = caster(helper)
        val signPos = BlockPos(2, 2, 2)
        helper.setBlock(signPos, Blocks.OAK_SIGN)
        val sign = helper.level.getBlockEntity(helper.absolutePos(signPos)) as SignBlockEntity
        sign.setText(sign.frontText.setMessage(0, Component.literal("Beware")).setMessage(2, Component.literal("of pigs")), true)

        val read = top(player, listOf(Vec3Iota(helper.absoluteVec(Vec3.atCenterOf(signPos)))), MirabiliaActions.STRING_SIGN!!)
        check(string(read) == "Beware of pigs") { "sign read as '${(read as? StringIota)?.string}'" }
        val none = top(player, listOf(Vec3Iota(helper.absoluteVec(Vec3(0.5, 2.5, 0.5)))), MirabiliaActions.STRING_SIGN!!)
        check(none is NullIota) { "air should read as null" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun stringsSurviveSaving(helper: GameTestHelper) {
        val original = StringIota("Wilbur ❤ 7")
        val copy = IotaType.deserialize(IotaType.serialize(original), helper.level)
        check(copy is StringIota && copy.string == original.string) { "string changed in a save/load round trip: $copy" }
        check(StringIota("x".repeat(5000)).string.length == StringIota.MAX_LENGTH) { "strings aren't capped" }
        helper.succeed()
    }

    private fun string(iota: Iota?) = (iota as? StringIota)?.string ?: throw GameTestAssertException("expected a string, got $iota")

    private fun number(iota: Iota?) = (iota as? DoubleIota)?.double ?: throw GameTestAssertException("expected a number, got $iota")

    private fun caster(helper: GameTestHelper): ServerPlayer {
        val player = helper.makeMockServerPlayerInLevel()
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        player.moveTo(pos.x, pos.y, pos.z)
        return player
    }

    private fun top(player: ServerPlayer, vararg argsAndPattern: Any): Iota? {
        @Suppress("UNCHECKED_CAST")
        val entry = argsAndPattern.last() as RegistrarEntry<ActionRegistryEntry>
        val args = argsAndPattern.dropLast(1).flatMap { if (it is List<*>) it.filterIsInstance<Iota>() else listOf(it as Iota) }
        val (result, stack) = cast(player, args, entry)
        if (result != ResolvedPatternType.EVALUATED) throw GameTestAssertException("${entry.id} failed: $result")
        return stack.lastOrNull()
    }

    /** Runs one of Hex's own patterns (e.g. an arithmetic one) and returns the top of the stack. */
    private fun hex(player: ServerPlayer, args: List<Iota>, pattern: HexPattern): Iota? {
        val (result, stack) = castPattern(player, args, pattern)
        if (result != ResolvedPatternType.EVALUATED) throw GameTestAssertException("$pattern failed: $result")
        return stack.lastOrNull()
    }

    private fun castPattern(player: ServerPlayer, args: List<Iota>, pattern: HexPattern): Pair<ResolvedPatternType, List<Iota>> {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = args)
        val view = vm.queueExecuteAndWrapIota(PatternIota(pattern), player.serverLevel())
        return view.resolutionType to vm.image.stack
    }

    private fun cast(player: ServerPlayer, args: List<Iota>, entry: RegistrarEntry<ActionRegistryEntry>): Pair<ResolvedPatternType, List<Iota>> {
        val vm = CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
        vm.image = vm.image.copy(stack = args)
        val view = vm.queueExecuteAndWrapIota(PatternIota(entry.value.prototype), player.serverLevel())
        return view.resolutionType to vm.image.stack
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
