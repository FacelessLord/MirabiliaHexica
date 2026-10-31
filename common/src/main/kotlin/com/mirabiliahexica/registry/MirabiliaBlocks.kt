package com.mirabiliahexica.registry

import com.mirabiliahexica.machines.CollectorBlock
import com.mirabiliahexica.machines.CrystallizerBlock
import com.mirabiliahexica.machines.DischargerBlock
import com.mirabiliahexica.wards.WardStoneBlock
import com.mirabiliahexica.wards.Wards
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object MirabiliaBlocks : MirabiliaRegistrar<Block>(Registries.BLOCK, { BuiltInRegistries.BLOCK }) {
    val WARDING_STONE = register("warding_stone") {
        WardStoneBlock(Wards.Kind.WARDING, stone(MapColor.COLOR_PURPLE).lightLevel { 4 })
    }
    val SILENCE_STONE = register("silence_stone") {
        WardStoneBlock(Wards.Kind.SILENCE, stone(MapColor.COLOR_CYAN))
    }

    // Tanako's World: its ground is glass, paler the higher it lies, with marble in the basins
    val DREAM_GLASS = register("dream_glass") { Block(dreamGlass(MapColor.COLOR_LIGHT_BLUE)) }
    val DUSKY_DREAM_GLASS = register("dusky_dream_glass") { Block(dreamGlass(MapColor.COLOR_PURPLE)) }
    val DEEP_DREAM_GLASS = register("deep_dream_glass") { Block(dreamGlass(MapColor.COLOR_BLACK)) }
    val MARBLE = register("marble") { Block(marble()) }
    val POLISHED_MARBLE = register("polished_marble") { Block(marble()) }
    val MARBLE_BRICKS = register("marble_bricks") { Block(marble()) }
    val MARBLE_PEBBLES = register("marble_pebbles") {
        Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0.6f).sound(SoundType.GRAVEL))
    }

    // Machines of media
    val MEDIA_DISCHARGER = register("media_discharger") { DischargerBlock(machine(MapColor.COLOR_ORANGE, SoundType.COPPER)) }
    val AMBIENT_COLLECTOR = register("ambient_collector") { CollectorBlock(false, machine(MapColor.COLOR_PURPLE, SoundType.AMETHYST).lightLevel { 5 }) }
    val SOLAR_COLLECTOR = register("solar_collector") { CollectorBlock(true, machine(MapColor.GOLD, SoundType.GLASS)) }
    val CRYSTALLIZER = register("crystallizer") { CrystallizerBlock(machine(MapColor.METAL, SoundType.METAL)) }

    private fun machine(color: MapColor, sound: SoundType) = BlockBehaviour.Properties.of()
        .mapColor(color)
        .strength(2f, 6f)
        .requiresCorrectToolForDrops()
        .sound(sound)

    private fun marble() = BlockBehaviour.Properties.of()
        .mapColor(MapColor.QUARTZ)
        .strength(1.5f, 6f)
        .requiresCorrectToolForDrops()
        .sound(SoundType.CALCITE)

    // The listening post's black stone, plain and inscribed (the inscriptions glow faintly)
    val INKSTONE = register("inkstone") { Block(inkstone()) }
    val GLYPHSTONE = register("glyphstone") { Block(inkstone().lightLevel { 7 }) }

    private fun inkstone() = BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_BLACK)
        .strength(3f, 9f)
        .requiresCorrectToolForDrops()
        .sound(SoundType.DEEPSLATE_TILES)

    // Opaque to the renderer: a whole world of see-through terrain would be far too slow to draw
    private fun dreamGlass(color: MapColor) = BlockBehaviour.Properties.of()
        .mapColor(color)
        .strength(1.2f, 6f)
        .requiresCorrectToolForDrops()
        .sound(SoundType.AMETHYST)

    // As tough as deepslate to mine, but it shrugs off explosions
    private fun stone(color: MapColor) = BlockBehaviour.Properties.of()
        .mapColor(color)
        .strength(4.5f, 1200f)
        .requiresCorrectToolForDrops()
        .sound(SoundType.DEEPSLATE_TILES)
}
