package com.mirabiliahexica.config

import com.mirabiliahexica.registry.MirabiliaItems
import dev.architectury.event.events.common.LootEvent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
import com.mirabiliahexica.config.Tuning

/**
 * With no dream to find them in, the notes on wellsprings turn up in overworld treasure instead: a [CHANCE] in
 * each of these chests, only while "wellspring_notes_overworld" is on.
 */
object OverworldNotes {
    val CHANCE get() = Tuning.Loot.overworldNotesChance

    private val CHESTS = listOf("simple_dungeon", "stronghold_library", "ancient_city", "woodland_mansion", "abandoned_mineshaft")
        .map { ResourceLocation("minecraft", "chests/$it") }.toSet()

    fun init() {
        LootEvent.MODIFY_LOOT_TABLE.register { _, id, context, builtin ->
            if (!builtin || id !in CHESTS) return@register
            context.addPool(
                LootPool.lootPool()
                    .`when`(LootItemRandomChanceCondition.randomChance(CHANCE))
                    .`when` { FeatureCondition("wellspring_notes_overworld") }
                    .add(LootItem.lootTableItem(MirabiliaItems.MEDIA_SOURCES_NOTES.value)),
            )
        }
    }
}
