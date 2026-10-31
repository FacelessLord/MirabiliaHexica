package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.mod.HexTags
import at.petrak.hexcasting.common.casting.PatternRegistryManifest
import at.petrak.hexcasting.common.lib.hex.HexActions
import com.mirabiliahexica.registry.RegistrarEntry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** Helpers for casting great spells in tests. */
object GreatSpells {
    /** Grants Hex's enlightenment advancement, which great spells require. */
    fun enlighten(player: ServerPlayer) {
        val advancement = player.server.advancements.getAdvancement(ResourceLocation("hexcasting", "enlightenment"))
            ?: error("Hex's enlightenment advancement is missing")
        val progress = player.advancements.getOrStartProgress(advancement)
        for (criterion in progress.remainingCriteria.toList()) player.advancements.award(advancement, criterion)
    }

    /** The shape to draw for [entry]: this world's shape for great spells, the prototype otherwise. */
    fun patternFor(player: ServerPlayer, entry: RegistrarEntry<ActionRegistryEntry>): PatternIota {
        val great = HexActions.REGISTRY.getHolderOrThrow(entry.key).`is`(HexTags.Actions.PER_WORLD_PATTERN)
        return if (great) worldPattern(player, entry) else PatternIota(entry.value.prototype)
    }

    /** The great spell's shape in this world (scrambled per world; the registry prototype doesn't cast it). */
    fun worldPattern(player: ServerPlayer, entry: RegistrarEntry<ActionRegistryEntry>) =
        PatternIota(PatternRegistryManifest.getCanonicalStrokesPerWorld(entry.key, player.server.overworld())
            ?: error("${entry.id} has no per-world shape (not in the per_world_pattern tag?)"))
}
