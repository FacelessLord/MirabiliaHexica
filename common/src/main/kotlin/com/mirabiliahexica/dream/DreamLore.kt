package com.mirabiliahexica.dream

import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidPattern
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import com.mirabiliahexica.registry.MirabiliaActions
import net.minecraft.server.level.ServerPlayer
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.FeatureGated

/**
 * The dream's own spells must be found before they work: each is taught by an Ancient Scroll carrying it, found
 * on Lost Dreamers and in the dream's chests. Picking one up earns an invisible advancement
 * (`mirabilia:dream_lore/<spell>`, criterion [CRITERION]), which also reveals that spell's page in the book.
 * Until then, drawing the spell is like drawing a pattern that means nothing.
 */
object DreamLore {
    const val CRITERION = "read"

    /** The spells to be found, by their pattern ids. */
    val SPELLS = listOf("wake", "will_upward", "will_apart", "will_solid", "dream_gaze", "dream_footing", "dream_bearing")

    fun knows(player: ServerPlayer, spell: String) = MirabiliaAdvancements.has(player, "dream_lore/$spell")

    fun teach(player: ServerPlayer, spell: String) = MirabiliaAdvancements.award(player, "dream_lore/$spell", CRITERION)

    /** Called first by each dream spell: one its caster hasn't learned acts as an unknown pattern. */
    fun require(env: CastingEnvironment, action: Action) {
        if (!Features.dreamSpellsNeedScrolls) return
        val entry = MirabiliaActions.entries.first { (it.value.action as? FeatureGated)?.action ?: it.value.action === action }
        val caster = env.castingEntity as? ServerPlayer
        if (caster == null || !knows(caster, entry.id.path)) throw MishapInvalidPattern(entry.value.prototype)
    }
}
