package com.mirabiliahexica.config

import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.MirabiliaConfig
import com.mirabiliahexica.config.Tuning.Advancements
import com.mirabiliahexica.config.Tuning.Ambient
import com.mirabiliahexica.config.Tuning.Dream
import com.mirabiliahexica.config.Tuning.GlassMan
import com.mirabiliahexica.config.Tuning.Imbuing
import com.mirabiliahexica.config.Tuning.Machines
import com.mirabiliahexica.config.Tuning.Spells
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * Numbers from the config, written into texts (book pages, advancement descriptions): a translation may hold
 * `#{name}`, filled in from [values] when it is shown. A noun after the number agrees with it:
 * `#{name|block|blocks}` (one / other), or with Russian's three forms `#{name|блок|блока|блоков}` and optionally a
 * fourth for fractions (whose form depends on the sentence); the number is then written before the chosen word. On a client joined to a server, the server's numbers are used ([synced]).
 */
object ConfigText {
    private const val DUST = MediaConstants.DUST_UNIT.toDouble()
    private const val CHARGED = MediaConstants.CRYSTAL_UNIT.toDouble()

    /** Every value a text can name, worked out from the config as it stands. */
    fun values(): Map<String, Double> = mapOf(
            "frost.maxRadius" to Spells.frostMaxRadius,
            "frost.baseCost" to Spells.frostBaseCost / DUST,
            "frost.perSquare" to Spells.frostCostPerSquare / DUST,
            "thaw.frostSeconds" to Spells.thawFrost / 20.0,
            "blood.floorHearts" to Spells.bloodHealthFloor / 2.0,
            "standing.startup" to Spells.standingStartup / DUST,
            "standing.upkeep" to Spells.standingUpkeepPerSecond / DUST,
            "standing.runs" to 20.0 / Spells.standingPeriod,
            "standing.range" to Spells.standingRange,
            "standing.max" to Spells.standingMax.toDouble(),
            "phial.max" to Spells.phialMaxCapacity / DUST,
            "joinPhials.charged" to Spells.joinPhialsCost / CHARGED,
            "greaterLens.charged" to Spells.greaterLensCost / CHARGED,
            "ward.radius" to Spells.wardRadius,
            "wardArmor.charged" to Spells.wardArmorCost / CHARGED,
            "macros.callCost" to Spells.macroCallCost / DUST,
            "macros.bindCharged" to Spells.macroBindCost / CHARGED,
            "macros.start" to MirabiliaConfig.macroStartingCount.toDouble(),
            "macros.max" to (MirabiliaConfig.macroStartingCount + MirabiliaConfig.macroMilestones.values.sum()).toDouble(),
            "imbue.charged" to Imbuing.imbueCost / CHARGED,
            "lens.runs" to 20.0 / Imbuing.lensPeriod,
            "lens.upkeep" to Imbuing.lensUpkeep / DUST,
            "armor.runs" to 20.0 / Imbuing.armorPeriod,
            "tool.dustPerWear" to Imbuing.dustPerWear.toDouble(),
            "dream.recordRadius" to Dream.recordRadius.toDouble(),
            "dream.stillSeconds" to Dream.stillTicks / 20.0,
            "dream.coldMinutes" to Dream.maxCold / 1200.0,
            "dream.wrongMinutes" to Dream.cameBackWrongMinutes.toDouble(),
            "dream.wrongPercent" to Dream.cameBackWrongMisfire * 100.0,
            "will.reach" to Dream.willReach,
            "will.coldSeconds" to Dream.willCold / 20.0,
            "possessed.waresMin" to GlassMan.waresMin.toDouble(),
            "possessed.waresMax" to GlassMan.waresMax.toDouble(),
            "ambient.castPercent" to Ambient.castShare * 100,
            // How long the air takes to lose half of it, by fading alone
            "ambient.halfLifeMinutes" to (ln(0.5) / ln(Ambient.decay) * Ambient.stepTicks / 1200.0).let { if (it.isFinite()) it else 0.0 },
            "discharger.min" to Machines.dischargerMinAmbient / DUST,
            "discharger.percent" to Machines.dischargerShare * 100,
            "discharger.range" to Machines.dischargerRange,
            "collector.percent" to Machines.ambientShare * 100,
            "collector.capacity" to Machines.ambientCapacity / DUST,
            "solar.atNoon" to Machines.solarAtNoon / DUST,
            "solar.capacity" to Machines.solarCapacity / DUST,
            "crystallizer.media" to Machines.crystallizerMedia / DUST,
            "crystallizer.dust" to Machines.crystallizerDust.toDouble(),
            "crystallizer.seconds" to Machines.crystallizerTicks / 20.0,
            "crystallizer.range" to Machines.crystallizerRange.toDouble(),
            "chat.history" to Tuning.Strings.chatHistory.toDouble(),
            "palace.rooms" to Tuning.Strings.palaceRooms.toDouble(),
            "palace.lastRoom" to (Tuning.Strings.palaceRooms - 1).toDouble(),
            "palace.writeCost" to Tuning.Strings.palaceWriteCost / DUST,
            "adv.kindThoughts" to Advancements.kindThoughtsDust.toDouble(),
            "adv.days" to Advancements.differentDays.toDouble(),
            "adv.village" to Advancements.glassVillage.toDouble(),
            "adv.justInTime" to Advancements.justInTime,
        )

    /** The server's values, on a client joined to one; null otherwise. */
    @Volatile
    var synced: Map<String, Double>? = null

    private val TOKEN = Regex("""#\{([A-Za-z.]+)((?:\|[^|}]*)*)}""")

    fun fill(text: String): String {
        val values = synced ?: values()
        return TOKEN.replace(text) { match ->
            val value = values[match.groupValues[1]] ?: return@replace match.value
            val forms = match.groupValues[2].split('|').drop(1)
            val number = format(value)
            if (forms.isEmpty()) number else "$number ${form(value, forms)}"
        }
    }

    /** Whole numbers plainly; others with up to two decimals, trailing zeros dropped. */
    fun format(value: Double): String {
        if (abs(value - value.roundToLong()) < 1e-9) return value.roundToLong().toString()
        return String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    /** The noun form for [value]: two forms are one / other; three are Russian's one / few / many (+ fraction). */
    private fun form(value: Double, forms: List<String>): String {
        val whole = abs(value - value.roundToLong()) < 1e-9
        if (forms.size < 3) return if (whole && value.roundToLong() == 1L) forms[0] else forms.getOrElse(1) { forms[0] }
        // Fractions take the genitive singular in Russian, which is the "few" form unless a fourth is given
        if (!whole) return forms.getOrElse(3) { forms[1] }
        val n = abs(value.roundToLong())
        return when {
            n % 10 == 1L && n % 100 != 11L -> forms[0]
            n % 10 in 2..4 && n % 100 !in 12..14 -> forms[1]
            else -> forms[2]
        }
    }
}
