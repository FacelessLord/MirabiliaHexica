package com.mirabiliahexica

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mirabiliahexica.config.Features
import dev.architectury.platform.Platform
import java.nio.file.Files
import com.mirabiliahexica.config.Tuning

/**
 * `config/mirabilia.json`, read once at startup, so changes need a restart. Sections: "features" (what is switched
 * on, [Features]), then one per system with its numbers, and "client". Every option has a "_name" line beside it
 * explaining it. Missing keys are filled in with defaults and written back, so the file always lists every option.
 */
object MirabiliaConfig {
    private const val FILE_NAME = "${Mirabilia.MODID}.json"

    private val DEFAULT_MILESTONES = linkedMapOf(
        "hexcasting:enlightenment" to 1,
        "mirabilia:glass_dream" to 1,
        "mirabilia:listening_post" to 1,
        "mirabilia:a_deal" to 1,
    )

    /** Addons with their own string iota and patterns; with "auto", ours stay off when one is installed. */
    private val OTHER_STRING_ADDONS = listOf("moreiotas")

    /** Whether to register the string patterns ("auto", true or false in the file). */
    var stringPatterns = true
        private set

    enum class GlyphStyle { BANDS, HOOPS, OFF }

    /** How imbued items show their hex on whoever wears or holds them (client side). */
    var glyphStyle = GlyphStyle.BANDS
        private set

    val imbuedGlyphs get() = glyphStyle != GlyphStyle.OFF

    /** The Greater Lens's grid zoom, as a share of the base (the Scrying Lens gives 0.33). */
    var greaterLensZoom = 1.0
        private set

    var macroStartingCount = 4
        private set

    /** Advancements that each let a player keep more macros, and how many more. */
    var macroMilestones: Map<String, Int> = DEFAULT_MILESTONES
        private set

    fun load() {
        val path = Platform.getConfigFolder().resolve(FILE_NAME)
        val json = try {
            if (Files.exists(path)) JsonParser.parseString(Files.readString(path)).asJsonObject else JsonObject()
        } catch (e: Exception) {
            Mirabilia.LOGGER.warn("Couldn't read {}, using defaults", path, e)
            JsonObject()
        }
        // Settings that once sat at the top move into their sections
        val oldStrings = json.remove("stringPatterns"); json.remove("_stringPatterns")
        val oldGlyphs = json.remove("imbuedGlyphs"); json.remove("_imbuedGlyphs")

        val features = Section(json, "features")
        if (oldStrings != null && !features.json.has("stringPatterns")) features.json.add("stringPatterns", oldStrings)
        Features.tanakosWorld = features.bool("tanakosWorld", true,
            "The dream dimension: Lost Dreamers, the Glass Man, castles, listening posts, dream spells. When off, the wellspring notes are found in overworld chests and the Solar Collector is made with obsidian")
        Features.ambientMedia = features.bool("ambientMedia", true,
            "Media lingering in the air where hexes are cast (sparks, misfires, the dream's pull). When off, the machines are off too")
        Features.mediaDischarger = features.bool("mediaDischarger", true, "The Media Discharger (needs ambientMedia)")
        Features.ambientCollector = features.bool("ambientCollector", true, "The Ambient Collector (needs ambientMedia)")
        Features.solarCollector = features.bool("solarCollector", true, "The Solar Collector (needs ambientMedia)")
        Features.crystallizer = features.bool("crystallizer", true, "The Crystallizer (needs ambientMedia)")
        Features.wellsprings = features.bool("wellsprings", true, "Wellspring Binding, Release, Reflection and Tether, and their notes")
        Features.bloodCharge = features.bool("bloodCharge", true, "The Blood Charge great spell")
        Features.joinPhials = features.bool("joinPhials", true, "The Join Phials great spell")
        Features.imbueLens = features.bool("imbueLens", true, "The Imbue Lens great spell, and imbued lenses working")
        Features.imbueArmor = features.bool("imbueArmor", true, "The Imbue Armor great spell, and imbued armor working")
        Features.imbueTool = features.bool("imbueTool", true, "The Imbue Tool great spell, and imbued tools working (Recharge Imbued Item goes when all three imbuings are off)")
        Features.glassMan = features.bool("glassMan", true, "The Glass Man, possessed villagers and their trades (needs tanakosWorld)")
        Features.macros = features.bool("macros", true, "Macros: a player's own patterns standing for whole hexes")
        Features.sunCaltrop = features.bool("sunCaltrop", true, "The sign on the sun seen through a lens, and its advancements")
        Features.deepsight = features.bool("deepsight", true, "Deepsight enchanted books in the dream's treasure")
        Features.keepItemsOnDreamDeath = features.bool("keepItemsOnDreamDeath", true, "Dying in the dream keeps inventory, bauble slots and experience")
        Features.carryCreatures = features.bool("carryCreatures", true, "Creatures within reach go into the dream and back out with a dreamer")
        Features.wardingStones = features.bool("wardingStones", true, "Warding and Silence Stones")
        Features.wardArmor = features.bool("wardArmor", true, "The Ward Armor spell, and warded armor hiding its wearer")
        Features.standingSpells = features.bool("standingSpells", false, "Standing spells: hexes raised on a block or entity that run on their own (off by default while their place beside imbuing is decided)")
        Features.chatReading = features.bool("chatReading", true, "Echo Purification: hexes reading the caster's own chat messages (a string pattern)")
        Features.mindPalace = features.bool("mindPalace", true, "The mind palace: numbered rooms of strings each player keeps (string patterns)")
        Features.dreamSpellsNeedScrolls = features.bool("dreamSpellsNeedScrolls", true, "The dream's own spells only work once their scroll has been found")
        val strings = features.raw("stringPatterns", "auto",
            "\"auto\": on unless another string addon (${OTHER_STRING_ADDONS.joinToString()}) is installed; or true / false")
        val otherAddon = OTHER_STRING_ADDONS.firstOrNull(Platform::isModLoaded)
        stringPatterns = if (strings.isJsonPrimitive && strings.asJsonPrimitive.isBoolean) strings.asBoolean else otherAddon == null
        if (!stringPatterns) Mirabilia.LOGGER.info("String patterns are off (config: {}, other string addon: {})", strings, otherAddon)

        val lens = Section(json, "lens")
        greaterLensZoom = lens.double("greaterLensZoom", 1.0, "Grid zoom the Greater Lens adds, as a share of the base (Hex's Scrying Lens: 0.33)")

        val macros = Section(json, "macros")
        macroStartingCount = macros.int("startingCount", 4, "How many macros every player may keep")
        macroMilestones = macros.map("milestones", DEFAULT_MILESTONES, "Advancements (by id) that each let a player keep that many more macros")

        val client = Section(json, "client")
        if (oldGlyphs != null && !client.json.has("imbuedGlyphs") && oldGlyphs.isJsonPrimitive && oldGlyphs.asJsonPrimitive.isBoolean) {
            client.json.addProperty("imbuedGlyphs", if (oldGlyphs.asBoolean) "bands" else "off")
        }
        val style = client.raw("imbuedGlyphs", "bands", "How imbued armor shows its hex: \"bands\" on it, \"hoops\" turning round it, or \"off\" (also hides lens and tool glyphs)")
        glyphStyle = GlyphStyle.values().firstOrNull { it.name.equals(style.asString, ignoreCase = true) } ?: GlyphStyle.BANDS

        // Every number worth tuning, by section
        for (section in Tuning.sections) {
            val file = Section(json, section.name)
            for (option in section.options) read(file, option)
        }

        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json))
        } catch (e: Exception) {
            Mirabilia.LOGGER.warn("Couldn't write {}", path, e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun read(file: Section, option: Tuning.Option<*>) {
        when (val default = option.default) {
            is Int -> (option as Tuning.Option<Int>).value = file.int(option.key, default, option.help)
            is Double -> (option as Tuning.Option<Double>).value = file.double(option.key, default, option.help)
        }
    }

    /** One section of the file: reads each option, writing in the default and its explanation if missing. */
    private class Section(root: JsonObject, name: String) {
        val json: JsonObject = root.getAsJsonObject(name) ?: JsonObject().also { root.add(name, it) }

        fun raw(key: String, default: Any, help: String): JsonElement {
            if (!json.has(key)) when (default) {
                is Boolean -> json.addProperty(key, default)
                is Number -> json.addProperty(key, default)
                else -> json.addProperty(key, default.toString())
            }
            json.addProperty("_$key", help)
            return json.get(key)
        }

        fun bool(key: String, default: Boolean, help: String) =
            raw(key, default, help).let { if (it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) it.asBoolean else default }

        fun double(key: String, default: Double, help: String) =
            raw(key, default, help).let { if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asDouble else default }

        fun int(key: String, default: Int, help: String) =
            raw(key, default, help).let { if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asInt else default }

        fun map(key: String, default: Map<String, Int>, help: String): Map<String, Int> {
            if (!json.has(key)) json.add(key, JsonObject().apply { default.forEach { (k, v) -> addProperty(k, v) } })
            json.addProperty("_$key", help)
            val obj = json.get(key).takeIf { it.isJsonObject }?.asJsonObject ?: return default
            return obj.entrySet().mapNotNull { (k, v) -> if (v.isJsonPrimitive && v.asJsonPrimitive.isNumber) k to v.asInt else null }.toMap()
        }
    }
}
