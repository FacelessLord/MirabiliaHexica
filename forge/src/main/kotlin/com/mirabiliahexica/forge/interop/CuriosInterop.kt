package com.mirabiliahexica.forge.interop

import com.mirabiliahexica.dream.Dream
import net.minecraft.world.entity.player.Player
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import top.theillusivec4.curios.api.event.DropRulesEvent
import top.theillusivec4.curios.api.type.capability.ICurio

/** Nothing is lost to the dream: a player who dies dreaming keeps what's in their Curios slots too. */
object CuriosInterop {
    fun init() {
        FORGE_BUS.addListener { event: DropRulesEvent ->
            val entity = event.entity
            if (entity is Player && Dream.isDreaming(entity)) event.addOverride({ true }, ICurio.DropRule.ALWAYS_KEEP)
        }
    }
}
