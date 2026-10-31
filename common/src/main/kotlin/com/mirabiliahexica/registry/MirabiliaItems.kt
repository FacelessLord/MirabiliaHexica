package com.mirabiliahexica.registry

import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.mirabiliahexica.items.ItemGreaterLens
import com.mirabiliahexica.items.ItemLoreNotes
import dev.architectury.registry.CreativeTabRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.block.Block
import java.util.function.Supplier
import com.mirabiliahexica.Mirabilia
import dev.architectury.core.item.ArchitecturySpawnEggItem
import dev.architectury.registry.registries.RegistrarManager
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.CreativeModeTabs
import com.mirabiliahexica.config.Features

object MirabiliaItems : MirabiliaRegistrar<Item>(Registries.ITEM, { BuiltInRegistries.ITEM }) {
    val GREATER_LENS = register("greater_lens") {
        // addEquipSlotFabric makes it wearable on the head on Fabric; on Forge ItemLens handles that itself
        ItemGreaterLens(IXplatAbstractions.INSTANCE.addEquipSlotFabric(EquipmentSlot.HEAD).stacksTo(1).rarity(Rarity.UNCOMMON))
    }
    /** Found in the dream's treasure; picking it up reveals the Wellsprings entry of the Hex notebook. */
    val MEDIA_SOURCES_NOTES = register("media_sources_notes") { ItemLoreNotes(Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)) }
    val WARDING_STONE = register("warding_stone") { BlockItem(MirabiliaBlocks.WARDING_STONE.value, Item.Properties()) }
    val SILENCE_STONE = register("silence_stone") { BlockItem(MirabiliaBlocks.SILENCE_STONE.value, Item.Properties()) }
    val DREAM_GLASS = blockItem("dream_glass") { MirabiliaBlocks.DREAM_GLASS.value }
    val DUSKY_DREAM_GLASS = blockItem("dusky_dream_glass") { MirabiliaBlocks.DUSKY_DREAM_GLASS.value }
    val DEEP_DREAM_GLASS = blockItem("deep_dream_glass") { MirabiliaBlocks.DEEP_DREAM_GLASS.value }
    val MARBLE = blockItem("marble") { MirabiliaBlocks.MARBLE.value }
    val POLISHED_MARBLE = blockItem("polished_marble") { MirabiliaBlocks.POLISHED_MARBLE.value }
    val MARBLE_BRICKS = blockItem("marble_bricks") { MirabiliaBlocks.MARBLE_BRICKS.value }
    val INKSTONE = blockItem("inkstone") { MirabiliaBlocks.INKSTONE.value }
    val GLYPHSTONE = blockItem("glyphstone") { MirabiliaBlocks.GLYPHSTONE.value }
    val MARBLE_PEBBLES = blockItem("marble_pebbles") { MirabiliaBlocks.MARBLE_PEBBLES.value }

    val MEDIA_DISCHARGER = blockItem("media_discharger") { MirabiliaBlocks.MEDIA_DISCHARGER.value }
    val AMBIENT_COLLECTOR = blockItem("ambient_collector") { MirabiliaBlocks.AMBIENT_COLLECTOR.value }
    val SOLAR_COLLECTOR = blockItem("solar_collector") { MirabiliaBlocks.SOLAR_COLLECTOR.value }
    val CRYSTALLIZER = blockItem("crystallizer") { MirabiliaBlocks.CRYSTALLIZER.value }

    val LOST_DREAMER_SPAWN_EGG = register("lost_dreamer_spawn_egg") {
        ArchitecturySpawnEggItem(entity("lost_dreamer"), 0x9DB4C8, 0x8A5CC8, Item.Properties())
    }
    val GLASS_MAN_SPAWN_EGG = register("glass_man_spawn_egg") {
        ArchitecturySpawnEggItem(entity("glass_man"), 0xEEF6FA, 0xA6E3F0, Item.Properties())
    }

    /** Resolved once entity types are registered, which on Forge is after items. */
    @Suppress("UNCHECKED_CAST")
    private fun entity(name: String) = RegistrarManager.get(Mirabilia.MODID).get(Registries.ENTITY_TYPE)
        .delegate(Mirabilia.id(name)) as RegistrySupplier<EntityType<out Mob>>

    private fun blockItem(name: String, block: () -> Block) = register(name) { BlockItem(block(), Item.Properties()) }

    private val HEX_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation("hexcasting", "hexcasting"))

    /** Puts our items in Hex Casting's creative tab rather than adding a tab of our own. */
    fun initCreativeTab() {
        val shown = entries.filter { entry -> FEATURE_OF[entry.id.path]?.let(Features::isOn) ?: true }
        CreativeTabRegistry.append(HEX_TAB, *shown.map { entry -> Supplier { entry.value } }.toTypedArray())
        val eggs = listOf(LOST_DREAMER_SPAWN_EGG, GLASS_MAN_SPAWN_EGG).filter { it in shown }
        CreativeTabRegistry.append(CreativeModeTabs.SPAWN_EGGS, *eggs.map { egg -> Supplier { egg.value } }.toTypedArray())
    }

    /** Items that belong to a feature, by name: left out of the creative tabs while it's off (they stay registered). */
    private val FEATURE_OF = mapOf(
        "media_sources_notes" to "wellsprings",
        "warding_stone" to "warding_stones", "silence_stone" to "warding_stones",
        "dream_glass" to "tanakos_world", "dusky_dream_glass" to "tanakos_world", "deep_dream_glass" to "tanakos_world",
        "marble" to "tanakos_world", "polished_marble" to "tanakos_world", "marble_bricks" to "tanakos_world",
        "marble_pebbles" to "tanakos_world", "inkstone" to "tanakos_world", "glyphstone" to "tanakos_world",
        "lost_dreamer_spawn_egg" to "tanakos_world", "glass_man_spawn_egg" to "glass_man",
        "media_discharger" to "media_discharger", "ambient_collector" to "ambient_collector",
        "solar_collector" to "solar_collector", "crystallizer" to "crystallizer",
    )

}
