package com.mirabiliahexica.config

import at.petrak.hexcasting.api.misc.MediaConstants
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Every number of the mod worth tuning, by section of `config/mirabilia.json`. Each option names its default and
 * explains itself; [com.mirabiliahexica.MirabiliaConfig] reads them all at startup. Media amounts are written in
 * dust (an Amethyst Dust is one) and come out in Hex's units; times are in ticks (20 a second) unless they say
 * otherwise. The systems read these through their own named constants.
 */
object Tuning {
    /** One option: a default, an explanation, and the value read from the file. */
    class Option<T : Any>(val key: String, val default: T, val help: String, private val convert: (T) -> Any = { it }) : ReadOnlyProperty<Any?, Any> {
        var value: T = default
        override fun getValue(thisRef: Any?, property: KProperty<*>): Any = convert(value)
    }

    abstract class Section(val name: String) {
        val options = mutableListOf<Option<*>>()
        private fun <T : Any> add(option: Option<T>) = option.also { options += it }

        protected fun double(key: String, default: Double, help: String) = Typed<Double>(add(Option(key, default, help)))
        protected fun float(key: String, default: Double, help: String) = Typed<Float>(add(Option(key, default, help) { it.toFloat() }))
        protected fun int(key: String, default: Int, help: String) = Typed<Int>(add(Option(key, default, help)))
        /** An amount written in dust, used in Hex's media units. */
        protected fun dust(key: String, default: Double, help: String) =
            Typed<Long>(add(Option(key, default, "$help (in dust)") { (it * MediaConstants.DUST_UNIT).toLong() }))
    }

    /** Gives an [Option] its proper type for the property it backs. */
    class Typed<V>(private val option: Option<*>) : ReadOnlyProperty<Any?, V> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(thisRef: Any?, property: KProperty<*>): V = option.getValue(thisRef, property) as V
    }

    object Ambient : Section("ambientMedia") {
        val castShare by double("castShare", 0.25, "Share of every hex's cost that lingers in the air where it was cast")
        val buddingTick by dust("buddingTick", 0.1, "Ambient media budding amethyst breathes out per random tick")
        val stepTicks by int("stepTicks", 400, "How often ambient media fades and spreads (ticks)")
        val decay by double("decay", 0.98, "Share kept at each step")
        val spread by double("spread", 0.05, "Share of the difference that flows into each of the four neighbouring chunks at each step")
        val charged by dust("charged", 500.0, "From this much the air sparks round players")
        val stuffy by dust("stuffy", 1500.0, "Above this, casts may misfire")
        val pull by dust("pull", 3000.0, "From this much, casts may pull the caster into the dream")
        val misfirePer by dust("misfirePer", 10000.0, "Misfire chance per cast is (level - stuffy) / this")
        val maxMisfire by double("maxMisfire", 0.15, "Highest misfire chance")
    }

    object Pull : Section("dreamPull") {
        val baseChance by double("baseChance", 0.05, "Chance per cast to be pulled at the threshold")
        val maxChance by double("maxChance", 0.25, "Highest chance per cast")
        val chancePer by dust("chancePer", 20000.0, "Each this much above the threshold adds 1 (100%) to the chance")
        val warnAt by dust("warnAt", 2500.0, "From this much, a player is warned the dream is close")
        val warnEvery by int("warnEvery", 1200, "Ticks between warnings")
        val carryRadius by double("carryRadius", 4.0, "Blocks round a dreamer within which others go into the dream and back out with them")
    }

    object Machines : Section("machines") {
        val dischargerShare by double("dischargerShare", 0.1, "Media Discharger: share of its chunk's ambient media taken each second")
        val dischargerMinAmbient by dust("dischargerMinAmbient", 50.0, "Media Discharger: rests while its chunk holds less than this")
        val dischargerRange by double("dischargerRange", 8.0, "Media Discharger: how far its arcs reach (blocks)")
        val dischargerMaxDamage by float("dischargerMaxDamage", 8.0, "Media Discharger: most damage an arc does (2 = a heart)")
        val dischargerDustPerDamage by double("dischargerDustPerDamage", 20.0, "Media Discharger: an arc does 1 damage, plus 1 per this many dust taken")
        val ambientShare by double("ambientCollectorShare", 0.0001, "Ambient Collector: share of its chunk's ambient media drawn each second")
        val ambientCapacity by dust("ambientCollectorCapacity", 1000.0, "Ambient Collector: how much it holds")
        val solarAtNoon by dust("solarAtNoon", 0.1, "Solar Collector: gathered each second at noon in clear weather")
        val solarCapacity by dust("solarCapacity", 500.0, "Solar Collector: how much it holds")
        val solarRain by double("solarRain", 0.3, "Solar Collector: share of sunlight left in rain")
        val solarThunder by double("solarThunder", 0.05, "Solar Collector: share of sunlight left in a thunderstorm")
        val crystallizerDust by int("crystallizerDustIn", 4, "Crystallizer: Amethyst Dust pressed into each shard")
        val crystallizerMedia by dust("crystallizerMedia", 2.0, "Crystallizer: media drawn from collectors for each shard")
        val crystallizerTicks by int("crystallizerTicks", 100, "Crystallizer: ticks per shard")
        val crystallizerRange by int("crystallizerRange", 8, "Crystallizer: how far it reaches for collectors (blocks)")
    }

    object Dream : Section("dream") {
        val recordRadius by int("recordRadius", 8, "How close to an Akashic Record a bed must be to dream (blocks)")
        val arrivalSpread by int("arrivalSpread", 4000, "Arrival spots are picked within this many blocks of the dream's centre")
        val stillTicks by int("stillTicks", 200, "Crouching still this long wakes a dreamer")
        val maxCold by int("maxColdTicks", 6000, "Ticks of cold before a dreamer is thrown out (5 minutes)")
        val warmthRadius by int("warmthRadius", 3, "A fire this close warms a dreamer (blocks)")
        val warmingPerSecond by int("warmingPerSecond", 100, "Ticks of cold a fire takes away each second")
        val cameBackWrongMinutes by int("cameBackWrongMinutes", 20, "How long Came Back Wrong lasts (minutes)")
        val cameBackWrongMisfire by float("cameBackWrongMisfire", 0.2, "Chance a cast misfires while Came Back Wrong")
        val willReach by double("willReach", 16.0, "How far the willing patterns reach (blocks)")
        val willCold by int("willCold", 100, "Ticks of cold each willing pattern costs (Will Solid: a quarter)")
        val dangerDustPerStep by int("dangerDustPerStep", 400, "Danger grows by 1 for each this many dust a dreamer carries")
        val dangerMax by float("dangerMax", 4.0, "Most danger carried media can bring (times as fast)")
        val lostPeriod by int("lostDreamerPeriod", 40, "Lost Dreamers: ticks between spawn tries")
        val lostBaseChance by float("lostDreamerBaseChance", 0.03, "Lost Dreamers: chance per try on arrival")
        val lostChancePerMinute by float("lostDreamerChancePerMinute", 0.04, "Lost Dreamers: added per minute dreaming")
        val lostChanceCap by float("lostDreamerChanceCap", 0.4, "Lost Dreamers: highest chance from time alone")
        val lostMaxChance by float("lostDreamerMaxChance", 0.6, "Lost Dreamers: highest chance with danger")
        val lostMaxNearby by int("lostDreamerMaxNearby", 6, "Lost Dreamers: most round a dreamer from time alone (danger adds more)")
        val lostMinDistance by int("lostDreamerMinDistance", 16, "Lost Dreamers: nearest they appear (blocks)")
        val lostMaxDistance by int("lostDreamerMaxDistance", 32, "Lost Dreamers: farthest they appear (blocks)")
    }

    object GlassMan : Section("glassMan") {
        val period by int("periodTicks", 200, "Ticks between tries")
        val graceMinutes by int("graceMinutes", 3, "Minutes in the dream before it can come (shortened by danger)")
        val fullMinutes by int("fullMinutes", 15, "Minutes by which the chance is full")
        val maxChance by float("maxChance", 0.2, "Chance per try once full, carrying little")
        val carryingMax by float("carryingMax", 0.5, "Highest chance per try with danger")
        val postMax by float("postMax", 0.6, "Highest chance per try inside a listening post (three times as likely)")
        val warning by int("warningTicks", 60, "Ticks of chime and shimmer before it appears")
        val minDistance by int("minDistance", 32, "Nearest it appears (blocks)")
        val maxDistance by int("maxDistance", 48, "Farthest it appears (blocks)")
        val holdRange by double("holdRange", 24.0, "Within this, a dreamer can't wake by keeping still (blocks)")
        val followRange by double("followRange", 8.0, "Within this when a dreamer wakes, it follows them into an empty villager (blocks)")
        val adoptRange by float("adoptRange", 48.0, "A spawn-egg Glass Man in the dream takes a dreamer this close (blocks)")
        val touchInterval by int("touchInterval", 40, "Ticks between touches")
        val touchMedia by dust("touchMedia", 10.0, "Media a touch takes")
        val touchCold by int("touchCold", 600, "Ticks of cold a touch adds")
        val touchDamage by float("touchDamage", 2.0, "Damage a touch does (2 = a heart)")
        val maskHealth by float("maskHealth", 6.0, "At or below this health, its touch throws the dreamer out instead (2 = a heart)")
        val stuckTicks by int("stuckTicks", 40, "Ticks without getting closer before it steps through space")
        val teleportCooldown by int("teleportCooldown", 200, "Ticks between its steps through space")
        val teleportMin by double("teleportMin", 8.0, "Nearest it lands after a step (blocks)")
        val teleportMax by double("teleportMax", 12.0, "Farthest it lands after a step (blocks)")
        val waresMin by int("waresMin", 2, "Possessed villagers: fewest Foci offered a day")
        val waresMax by int("waresMax", 3, "Possessed villagers: most Foci offered a day")
        val cypherPrice by int("cypherPrice", 8, "Possessed villagers: Charged Amethyst for a Focus of an ancient cypher's hex")
    }

    object Imbuing : Section("imbuing") {
        val imbueCost by dust("imbueCost", 30.0, "Imbue Lens, Armor or Tool")
        val lensUpkeep by dust("lensUpkeep", 0.1, "Taken from an imbued lens's store each run")
        val lensPeriod by int("lensPeriod", 10, "Ticks between an imbued lens's runs")
        val armorUpkeep by dust("armorUpkeep", 0.1, "Taken from imbued armor's store each run")
        val armorPeriod by int("armorPeriod", 10, "Ticks between imbued armor's runs")
        val toolCooldown by int("toolCooldown", 10, "Ticks before an imbued tool can cast again")
        val dustPerWear by int("dustPerWear", 10, "An imbued tool wears by 1 per cast, plus 1 per this many dust its hex spends")
    }

    object Spells : Section("spells") {
        val frostMaxRadius by double("frostMaxRadius", 8.0, "Freeze and Thaw: largest radius (blocks)")
        val frostBaseCost by dust("frostBaseCost", 1.0, "Freeze and Thaw: cost before the radius")
        val frostCostPerSquare by dust("frostCostPerSquare", 0.25, "Freeze and Thaw: added per square block of radius")
        val thawFrost by int("thawFrost", 60, "Ticks of frost Thaw takes off each creature in its sphere (fully frozen is 140; powder snow adds 1 a tick)")
        val wardRadius by double("wardRadius", 8.0, "Warding and Silence Stones: radius of the sphere they guard (blocks)")
        val wardArmorCost by dust("wardArmorCost", 30.0, "Ward Armor")
        val standingStartup by dust("standingStartup", 3.0, "Raising a standing spell")
        val standingUpkeepPerSecond by dust("standingUpkeepPerSecond", 0.5, "A standing spell's upkeep each second")
        val standingPeriod by int("standingPeriod", 10, "Ticks between a standing spell's runs")
        val standingRange by double("standingRange", 16.0, "How far a standing spell reaches from its anchor (blocks)")
        val standingMax by int("standingMax", 8, "Standing spells a player may keep at once")
        val phialMaxCapacity by dust("phialMaxCapacity", 2560.0, "Join Phials: the largest a Phial can grow")
        val joinPhialsCost by dust("joinPhialsCost", 10.0, "Join Phials")
        val bloodHealthFloor by float("bloodHealthFloor", 1.0, "Blood Charge never takes health below this (2 = a heart)")
        val greaterLensCost by dust("greaterLensCost", 100.0, "Craft Greater Lens")
        val macroCallCost by dust("macroCallCost", 1.0, "Each call of a macro")
        val macroBindCost by dust("macroBindCost", 10.0, "Macro Binding")
    }

    object Strings : Section("strings") {
        val chatHistory by int("chatHistory", 32, "Echo Purification: how many of a player's own chat messages are remembered")
        val palaceRooms by int("palaceRooms", 64, "Mind palace: rooms each player has (numbered from 0)")
        val palaceWriteCost by dust("palaceWriteCost", 0.1, "Memory's Gambit: writing into a room")
    }

    object Loot : Section("loot") {
        val overworldNotesChance by float("overworldNotesChance", 0.15, "With no dream: chance of the wellspring notes in each dungeon, stronghold library, ancient city, mansion and mineshaft chest")
    }

    object Advancements : Section("advancements") {
        val nearby by double("nearby", 16.0, "Players this close to a machine share in what it earns (blocks)")
        val kindThoughtsDust by int("kindThoughtsDust", 1000, "Kind Thoughts: dust a player's Solar Collectors must gather")
        val differentDays by int("differentDays", 7, "Different Every Day: days the sign must be seen")
        val glassVillage by int("glassVillage", 5, "Glass Village: villagers possessed through a player")
        val justInTime by double("justInTime", 4.0, "Just in Time: waking with the Glass Man this close (blocks)")
        val sunWithinDegrees by double("sunWithinDegrees", 8.0, "Caltrop: how near the sun the gaze must be (degrees)")
    }

    object Client : Section("client") {
        val caltropSize by float("caltropSize", 7.5, "The sun sign's spike length (the sun's face is about 15)")
        val caltropBacking by float("caltropBacking", 7.5, "Half the side of the dimmed square behind the sun sign")
        val caltropBrightness by float("caltropBrightness", 0.6, "The sun sign's opacity, 0 to 1")
        val glyphBrightness by float("glyphBrightness", 0.85, "Imbued glyphs' opacity while the item has media, 0 to 1")
        val glyphSpent by float("glyphSpent", 0.22, "Imbued glyphs' opacity once the item's media is spent, 0 to 1")
    }

    val sections: List<Section> = listOf(Ambient, Pull, Machines, Dream, GlassMan, Imbuing, Spells, Strings, Loot, Advancements, Client)
}
