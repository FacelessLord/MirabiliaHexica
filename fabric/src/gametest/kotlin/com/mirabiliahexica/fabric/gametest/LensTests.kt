package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.common.lib.HexAttributes
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.lens.SunSign
import com.mirabiliahexica.registry.MirabiliaItems
import dev.emi.trinkets.api.TrinketsApi
import kotlin.math.abs
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f

class LensTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun greaterLensOnHeadDoublesZoom(helper: GameTestHelper) {
        val player = player(helper)
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack(MirabiliaItems.GREATER_LENS.value))
        player.doTick()
        assertZoom(player, 2.0)
        check(player.getAttributeValue(HexAttributes.SCRY_SIGHT) > 0.0) { "Greater Lens doesn't grant scrying sight" }

        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY)
        player.doTick()
        assertZoom(player, 1.0)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun greaterLensStacksWithScryingLens(helper: GameTestHelper) {
        val player = player(helper)
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack(MirabiliaItems.GREATER_LENS.value))
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack(HexItems.SCRYING_LENS))
        player.doTick()
        assertZoom(player, 2.33)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun greaterLensWorksInTrinketFaceSlot(helper: GameTestHelper) {
        val player = player(helper)
        val face = TrinketsApi.getTrinketComponent(player).orElseThrow().inventory["head"]?.get("face")
            ?: throw GameTestAssertException("no head/face trinket slot")
        val lens = ItemStack(MirabiliaItems.GREATER_LENS.value)
        check(lens.`is`(TagKey.create(Registries.ITEM, ResourceLocation("trinkets", "head/face")))) {
            "Greater Lens is not allowed in the face slot (check data/trinkets/tags/items/head/face.json)"
        }
        face.setItem(0, lens)
        player.doTick()
        assertZoom(player, 2.0)
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun greaterLensIsNotCraftable(helper: GameTestHelper) {
        // Only the great spell makes one: there is no crafting recipe
        check(helper.level.recipeManager.byKey(Mirabilia.id("greater_lens")).isEmpty) { "Greater Lens shouldn't be craftable" }
        helper.succeed()
    }

    private fun player(helper: GameTestHelper): ServerPlayer = helper.makeMockServerPlayerInLevel()

    private fun assertZoom(player: ServerPlayer, expected: Double) {
        val actual = player.getAttributeValue(HexAttributes.GRID_ZOOM)
        check(abs(actual - expected) < 1e-6) { "grid zoom is $actual, expected $expected" }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun sunSignIsACaltropTurnedDaily(helper: GameTestHelper) {
        // Morning: up the sky is +z in the sun's frame; evening -z; at noon it has turned over to south (+x).
        // Always leaning 25° towards the viewer (-y)
        val lean = Math.toRadians(25.0)
        val c = Math.cos(lean).toFloat()
        val s = -Math.sin(lean).toFloat()
        check(SunSign.up(-1.2f).distance(Vector3f(0f, s, c)) < 1e-4f) { "morning up is ${SunSign.up(-1.2f)}" }
        check(SunSign.up(1.2f).distance(Vector3f(0f, s, -c)) < 1e-4f) { "evening up is ${SunSign.up(1.2f)}" }
        check(SunSign.up(0f).distance(Vector3f(c, s, 0f)) < 1e-4f) { "noon up is ${SunSign.up(0f)}" }
        val up = SunSign.up(-1.2f)
        for (day in listOf(0L, 1L, 2L, 365L)) {
            val spikes = SunSign.spikes(day, up)
            check(spikes.size == 4 && spikes.all { Math.abs(it.length() - 1f) < 1e-4f }) { "day $day: not four unit spikes" }
            check(spikes[0].distance(up) < 1e-4f) { "day $day: the first spike doesn't point up" }
            // A caltrop: every pair of spikes meets at the tetrahedral angle (cos = -1/3)
            for (i in 0 until 4) for (j in i + 1 until 4) {
                val cos = spikes[i].dot(spikes[j])
                check(Math.abs(cos + 1f / 3f) < 1e-4f) { "day $day: spikes $i and $j at cos $cos" }
            }
        }
        // The same all day, different from one day to the next
        check(SunSign.day(23999) == 0L && SunSign.day(24000) == 1L) { "days split in the wrong place" }
        check(SunSign.spikes(5, up)[1].distance(SunSign.spikes(5, up)[1]) == 0f) { "a day's sign changed" }
        // Each day the lower spikes turn a little (12°) about the up spike, and nothing else changes
        for (day in 0L until 30L) {
            val today = SunSign.spikes(day, up)
            val tomorrow = SunSign.spikes(day + 1, up)
            for (i in 1..3) {
                val turned = Math.toDegrees(Math.acos(tidy(project(today[i], up).dot(project(tomorrow[i], up))))).toFloat()
                check(Math.abs(turned - 12f) < 0.1f) { "day $day: spike $i turned $turned°, expected 12°" }
            }
        }
        helper.succeed()
    }

    /** [v] with its part along [axis] removed, normalised: where it points around the axis. */
    private fun project(v: Vector3f, axis: Vector3f): Vector3f {
        val a = Vector3f(axis).normalize()
        return Vector3f(v).sub(Vector3f(a).mul(v.dot(a))).normalize()
    }

    private fun tidy(cos: Float) = cos.toDouble().coerceIn(-1.0, 1.0)

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
