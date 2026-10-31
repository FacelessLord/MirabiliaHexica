package com.mirabiliahexica.registry

import com.mirabiliahexica.Mirabilia
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block

object MirabiliaTags {
    /** The three shades of dream glass. */
    val DREAM_GLASS: TagKey<Block> = TagKey.create(Registries.BLOCK, Mirabilia.id("dream_glass"))

    /** What the dream's structures are built of; pillars never grow into it. */
    val DREAM_BUILT: TagKey<Block> = TagKey.create(Registries.BLOCK, Mirabilia.id("dream_built"))

    /** The dream's own ground: what the willing patterns and the pillars may shape. */
    val DREAM_SHAPEABLE: TagKey<Block> = TagKey.create(Registries.BLOCK, Mirabilia.id("dream_shapeable"))
}
