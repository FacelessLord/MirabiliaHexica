package com.mirabiliahexica.fabric.gametest

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.iota.PatternIota
import com.google.gson.JsonParser
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.MirabiliaConfig
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.config.ConfigText
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.config.Tuning
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.registry.MirabiliaActions
import dev.architectury.platform.Platform
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import java.nio.file.Files

/** The config file and the feature switches. They change global state, so these run in a batch of their own. */
class FeatureTests {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun theConfigFileListsEveryOption(helper: GameTestHelper) {
        val file = Platform.getConfigFolder().resolve("${Mirabilia.MODID}.json")
        check(Files.exists(file)) { "no config file was written" }
        val json = JsonParser.parseString(Files.readString(file)).asJsonObject
        val features = json.getAsJsonObject("features")
        for (key in listOf("tanakosWorld", "ambientMedia", "solarCollector", "wellsprings", "imbueLens", "glassMan", "macros", "keepItemsOnDreamDeath", "stringPatterns")) {
            check(features.has(key) && features.has("_$key")) { "the file lacks features.$key or its explanation" }
        }
        check(json.getAsJsonObject("macros").has("milestones")) { "no macro milestones in the file" }
        check(json.getAsJsonObject("lens").has("greaterLensZoom")) { "no Greater Lens zoom in the file" }
        check(json.getAsJsonObject("client").get("imbuedGlyphs").asString in listOf("bands", "hoops", "off")) { "bad glyph style" }
        check(MirabiliaConfig.macroStartingCount == 4 && MirabiliaConfig.greaterLensZoom == 1.0) { "defaults changed" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun derivedFeaturesFollowTheirParts(helper: GameTestHelper) {
        switched({ Features.ambientMedia = false }, { Features.ambientMedia = true }) {
            check(!Features.isOn("solar_collector") && !Features.isOn("machines") && !Features.isOn("dream_pull")) { "machines or the pull survived ambient media going off" }
        }
        switched({ Features.tanakosWorld = false }, { Features.tanakosWorld = true }) {
            check(!Features.isOn("glass_man") && Features.isOn("solar_collector_plain") && !Features.isOn("solar_collector_dream")) { "the dream's switch didn't carry" }
            check(Features.isOn("wellspring_notes_overworld") && Features.isOn("wards_plain")) { "notes or wards have nowhere to come from" }
        }
        switched({ Features.imbueLens = false; Features.imbueArmor = false }, { Features.imbueLens = true; Features.imbueArmor = true }) {
            check(Features.isOn("recharge_imbued")) { "Recharge went while imbuing tools was still on" }
            Features.imbueTool = false
            check(!Features.isOn("recharge_imbued")) { "Recharge stayed with every imbuing off" }
            Features.imbueTool = true
        }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun switchedOffThingsStopWorking(helper: GameTestHelper) {
        val player = helper.makeMockServerPlayerInLevel()
        helper.absoluteVec(Vec3(1.5, 2.0, 1.5)).let { player.moveTo(it.x, it.y, it.z) }
        fun cast(entry: com.mirabiliahexica.registry.RegistrarEntry<at.petrak.hexcasting.api.casting.ActionRegistryEntry>) =
            CastingVM.empty(StaffCastEnv(player, InteractionHand.MAIN_HAND))
                .queueExecuteAndWrapIota(PatternIota(entry.value.prototype), helper.level).resolutionType
        check(cast(MirabiliaActions.MACRO_LIST) == ResolvedPatternType.EVALUATED) { "Macro Reflection failed while on" }
        switched({ Features.macros = false }, { Features.macros = true }) {
            check(cast(MirabiliaActions.MACRO_LIST) == ResolvedPatternType.INVALID) { "Macro Reflection worked with macros off" }
        }
        val chunk = ChunkPos(player.blockPosition())
        switched({ Features.ambientMedia = false }, { Features.ambientMedia = true }) {
            AmbientMedia.set(helper.level, chunk, 0)
            AmbientMedia.add(helper.level, player.blockPosition(), 100 * at.petrak.hexcasting.api.misc.MediaConstants.DUST_UNIT)
            check(AmbientMedia.get(helper.level, chunk) == 0L) { "ambient media gathered while off" }
        }
        switched({ Features.tanakosWorld = false }, { Features.tanakosWorld = true }) {
            check(!Dream.enter(player, null) && !Dream.isDreaming(player)) { "entered the dream with it off" }
        }
        // Standing spells are off by default
        check(!Features.standingSpells && cast(MirabiliaActions.STANDING_REMAINING) != ResolvedPatternType.EVALUATED) { "standing spells work by default" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun theRightRecipesLoad(helper: GameTestHelper) {
        // Defaults: the dream's Solar Collector and ward stones, not the overworld ones
        val recipes = helper.level.server.recipeManager
        fun has(name: String) = recipes.byKey(ResourceLocation(Mirabilia.MODID, name)).isPresent
        check(has("solar_collector") && !has("solar_collector_obsidian")) { "wrong Solar Collector recipes loaded" }
        check(has("warding_stone") && !has("warding_stone_plain")) { "wrong ward recipes loaded" }
        check(has("media_discharger") && has("crystallizer") && has("brainsweep/ambient_collector")) { "a machine recipe is missing" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun everyNumberIsInTheFileAndTakesEffect(helper: GameTestHelper) {
        val json = JsonParser.parseString(Files.readString(Platform.getConfigFolder().resolve("${Mirabilia.MODID}.json"))).asJsonObject
        for (section in Tuning.sections) {
            val written = json.getAsJsonObject(section.name) ?: throw GameTestAssertException("no section ${section.name}")
            for (option in section.options) {
                check(written.has(option.key) && written.has("_${option.key}")) { "${section.name}.${option.key} or its explanation is missing" }
            }
        }
        // Changing a number changes the game: here, the share of a cast that lingers in the air
        @Suppress("UNCHECKED_CAST")
        val share = Tuning.Ambient.options.first { it.key == "castShare" } as Tuning.Option<Double>
        check(AmbientMedia.CAST_SHARE == 0.25) { "castShare default is ${AmbientMedia.CAST_SHARE}" }
        share.value = 0.5
        try {
            check(AmbientMedia.CAST_SHARE == 0.5) { "a changed castShare didn't reach ambient media" }
        } finally {
            share.value = share.default
        }
        // Dust amounts come out in Hex's units
        check(Tuning.Spells.macroCallCost == at.petrak.hexcasting.api.misc.MediaConstants.DUST_UNIT) { "dust isn't converted" }
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    fun textsQuoteTheConfigsNumbers(helper: GameTestHelper) {
        check(ConfigText.fill("a sphere of #{ward.radius|block|blocks}") == "a sphere of 8 blocks") { ConfigText.fill("#{ward.radius|block|blocks}") }
        check(ConfigText.fill("#{macros.start} macros, up to #{macros.max}") == "4 macros, up to 8") { "macro counts: ${ConfigText.fill("#{macros.start} #{macros.max}")}" }
        check(ConfigText.fill("#{ambient.castPercent}%") == "25%") { "percent" }
        // Russian's three forms, and fractions
        check(ConfigText.fill("в #{ward.radius|блоке|блоках|блоках}") == "в 8 блоках") { ConfigText.fill("в #{ward.radius|блоке|блоках|блоках}") }
        check(ConfigText.fill("меньше #{blood.floorHearts|сердца|сердец|сердец|сердца}") == "меньше 0.5 сердца") { ConfigText.fill("#{blood.floorHearts|сердца|сердец|сердец|сердца}") }
        check(ConfigText.fill("#{nonsense.key}") == "#{nonsense.key}") { "unknown names should be left as they are" }
        // A changed number shows in the text
        @Suppress("UNCHECKED_CAST")
        val radius = Tuning.Spells.options.first { it.key == "wardRadius" } as Tuning.Option<Double>
        radius.value = 12.0
        try {
            check(ConfigText.fill("#{ward.radius|block|blocks}") == "12 blocks") { "a changed ward radius didn't reach the text" }
        } finally {
            radius.value = radius.default
        }
        // Every name the texts use exists
        val values = ConfigText.values()
        for (lang in listOf("en_us", "ru_ru")) {
            val text = javaClass.getResourceAsStream("/assets/mirabilia/lang/$lang.json")!!.reader(Charsets.UTF_8).readText()
            for (name in Regex("""#\{([A-Za-z.]+)""").findAll(text).map { it.groupValues[1] }.toSet()) {
                check(name in values) { "$lang quotes #{$name}, which isn't a config value" }
            }
        }
        helper.succeed()
    }

    /** Runs [body] with a switch flipped by [off], putting it back with [on] whatever happens. */
    private inline fun switched(off: () -> Unit, on: () -> Unit, body: () -> Unit) {
        off()
        try { body() } finally { on() }
    }

    private companion object {
        const val BATCH = "mirabilia_features"
    }

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}
