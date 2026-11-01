package com.mirabiliahexica

import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.dream.DreamPull
import com.mirabiliahexica.dream.DreamRules
import com.mirabiliahexica.dream.Possession
import com.mirabiliahexica.imbue.ArmorImbuement
import com.mirabiliahexica.lens.LensImbuement
import com.mirabiliahexica.registry.MirabiliaActions
import com.mirabiliahexica.registry.MirabiliaBlockEntities
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaEffects
import com.mirabiliahexica.registry.MirabiliaEnchantments
import com.mirabiliahexica.registry.MirabiliaEntities
import com.mirabiliahexica.registry.MirabiliaFeatures
import com.mirabiliahexica.registry.MirabiliaArithmetics
import com.mirabiliahexica.registry.MirabiliaIotaTypes
import com.mirabiliahexica.registry.MirabiliaItems
import com.mirabiliahexica.registry.MirabiliaStructurePieces
import com.mirabiliahexica.registry.MirabiliaStructures
import com.mirabiliahexica.source.MediaSources
import com.mirabiliahexica.standing.StandingSpells
import com.mirabiliahexica.wards.Wards
import dev.architectury.event.EventResult
import dev.architectury.event.events.common.EntityEvent
import dev.architectury.event.events.common.TickEvent
import net.minecraft.resources.ResourceLocation
import vazkii.patchouli.api.PatchouliAPI
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import com.mirabiliahexica.lens.SunSighting
import com.mirabiliahexica.registry.MirabiliaSpecialHandlers
import com.mirabiliahexica.registry.MirabiliaLootConditions
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.OverworldNotes
import dev.architectury.event.events.common.PlayerEvent
import com.mirabiliahexica.network.MirabiliaNetworking
import com.mirabiliahexica.strings.ChatMemory

object Mirabilia {
    const val MODID = "mirabilia"
    const val STRING_PATTERNS_FLAG = "$MODID:string_patterns"

    @JvmField
    val LOGGER: Logger = LogManager.getLogger(MODID)

    @JvmStatic
    fun id(path: String) = ResourceLocation(MODID, path)

    /** Called once by each loader's entrypoint, on both sides. */
    fun init() {
        // First: it decides which patterns get registered
        MirabiliaConfig.load()
        // Hides the book's Strings entry when the string patterns are off
        PatchouliAPI.get().setConfigFlag(STRING_PATTERNS_FLAG, MirabiliaConfig.stringPatterns)
        initRegistries(
            MirabiliaBlocks,
            MirabiliaItems,
            MirabiliaBlockEntities,
            MirabiliaEffects,
            MirabiliaEnchantments,
            MirabiliaFeatures,
            MirabiliaStructures,
            MirabiliaStructurePieces,
            MirabiliaEntities,
            MirabiliaIotaTypes,
            MirabiliaArithmetics,
            MirabiliaActions,
            MirabiliaSpecialHandlers,
            MirabiliaLootConditions,
        )
        Features.applyToBook()
        OverworldNotes.init()
        PlayerEvent.PLAYER_JOIN.register { MirabiliaNetworking.sendFeatures(it) }
        MirabiliaItems.initCreativeTab()
        MirabiliaEntities.initAttributes()
        Wards.init()
        DreamRules.init()
        // Before MediaSources: it notes each cast's whole cost, before a wellspring pays any of it
        AmbientMedia.init()
        DreamPull.init()
        SunSighting.init()
        ChatMemory.init()
        MirabiliaCommands.init()
        MediaSources.init()
        ArmorImbuement.init()
        Possession.init()
        TickEvent.SERVER_POST.register { server -> StandingSpells.get(server).tick(server) }
        TickEvent.SERVER_POST.register(LensImbuement::tick)
        EntityEvent.LIVING_DEATH.register { entity, _ ->
            entity.server?.let { StandingSpells.get(it).onEntityDeath(entity) }
            EventResult.pass()
        }
    }
}
