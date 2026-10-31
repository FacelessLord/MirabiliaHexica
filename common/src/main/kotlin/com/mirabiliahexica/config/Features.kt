package com.mirabiliahexica.config

import vazkii.patchouli.api.PatchouliAPI

/**
 * What of the mod is switched on (`config/mirabilia.json`, section "features"). [isOn] answers by name for the
 * places that only have a name: recipe and advancement load conditions, loot conditions and the book's flags.
 * Some names are derived (a machine needs ambient media; the Glass Man needs the dream), so asking for one
 * always gives what actually applies.
 */
object Features {
    var tanakosWorld = true
    var ambientMedia = true
    var mediaDischarger = true
    var ambientCollector = true
    var solarCollector = true
    var crystallizer = true
    var wellsprings = true
    var bloodCharge = true
    var joinPhials = true
    var imbueLens = true
    var imbueArmor = true
    var imbueTool = true
    var glassMan = true
    var macros = true
    var sunCaltrop = true
    var deepsight = true
    var keepItemsOnDreamDeath = true
    var carryCreatures = true
    var dreamSpellsNeedScrolls = true
    var wardingStones = true
    var wardArmor = true
    var standingSpells = false
    var chatReading = true
    var mindPalace = true

    /** Every name [isOn] knows, with what it means in effect. */
    val names: Map<String, () -> Boolean> = linkedMapOf(
        "tanakos_world" to { tanakosWorld },
        "ambient_media" to { ambientMedia },
        "dream_pull" to { tanakosWorld && ambientMedia },
        "media_discharger" to { ambientMedia && mediaDischarger },
        "ambient_collector" to { ambientMedia && ambientCollector },
        "solar_collector" to { ambientMedia && solarCollector },
        // Made with Glyphstone from the listening posts, or with obsidian when there's no dream to find them in
        "solar_collector_dream" to { ambientMedia && solarCollector && tanakosWorld },
        "solar_collector_plain" to { ambientMedia && solarCollector && !tanakosWorld },
        "crystallizer" to { ambientMedia && crystallizer },
        "crystal_of_light" to { ambientMedia && solarCollector && crystallizer },
        "machines" to { ambientMedia && (mediaDischarger || ambientCollector || solarCollector || crystallizer) },
        "wellsprings" to { wellsprings },
        // The notes on wellsprings are dream loot; with no dream they turn up in overworld chests instead
        "wellspring_notes_dream" to { wellsprings && tanakosWorld },
        "wellspring_notes_overworld" to { wellsprings && !tanakosWorld },
        // Ward stones are made of dream marble and glass, or of calcite and tinted glass with no dream to go to
        "warding_stones" to { wardingStones },
        "wards_dream" to { wardingStones && tanakosWorld },
        "wards_plain" to { wardingStones && !tanakosWorld },
        "ward_armor" to { wardArmor },
        "standing_spells" to { standingSpells },
        "chat_reading" to { chatReading },
        "mind_palace" to { mindPalace },
        "blood_charge" to { bloodCharge },
        "join_phials" to { joinPhials },
        "imbue_lens" to { imbueLens },
        "imbue_armor" to { imbueArmor },
        "imbue_tool" to { imbueTool },
        "imbue_armor_or_tool" to { imbueArmor || imbueTool },
        "recharge_imbued" to { imbueLens || imbueArmor || imbueTool },
        "glass_man" to { tanakosWorld && glassMan },
        "macros" to { macros },
        "sun_caltrop" to { sunCaltrop },
        "deepsight" to { deepsight },
    )

    /** Whether the named feature is on; unknown names count as on, so a typo never hides anything silently. */
    fun isOn(name: String) = names[name]?.invoke() ?: true

    /** The flag name the book uses for [feature] (Patchouli's `"flag"`). */
    fun flag(feature: String) = "mirabilia:$feature"

    /** All features as name → on, as the server tells its clients. */
    fun snapshot(): Map<String, Boolean> = names.mapValues { it.value() }

    /** Shows and hides the book's entries and pages to match ("flag": "mirabilia:<name>"). */
    fun applyToBook() {
        for ((name, on) in snapshot()) PatchouliAPI.get().setConfigFlag(flag(name), on)
    }

    /** On a client, what the server says is on (so the book matches the server, whatever the local file says). */
    fun applyFromServer(features: Map<String, Boolean>) {
        val local = { name: String -> features[name] }
        tanakosWorld = local("tanakos_world") ?: tanakosWorld
        ambientMedia = local("ambient_media") ?: ambientMedia
        mediaDischarger = local("media_discharger") ?: mediaDischarger
        ambientCollector = local("ambient_collector") ?: ambientCollector
        solarCollector = local("solar_collector") ?: solarCollector
        crystallizer = local("crystallizer") ?: crystallizer
        wellsprings = local("wellsprings") ?: wellsprings
        bloodCharge = local("blood_charge") ?: bloodCharge
        joinPhials = local("join_phials") ?: joinPhials
        imbueLens = local("imbue_lens") ?: imbueLens
        imbueArmor = local("imbue_armor") ?: imbueArmor
        imbueTool = local("imbue_tool") ?: imbueTool
        glassMan = local("glass_man") ?: glassMan
        macros = local("macros") ?: macros
        sunCaltrop = local("sun_caltrop") ?: sunCaltrop
        deepsight = local("deepsight") ?: deepsight
        wardingStones = local("warding_stones") ?: wardingStones
        wardArmor = local("ward_armor") ?: wardArmor
        standingSpells = local("standing_spells") ?: standingSpells
        chatReading = local("chat_reading") ?: chatReading
        mindPalace = local("mind_palace") ?: mindPalace
        applyToBook()
    }
}
