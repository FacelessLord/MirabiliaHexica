package com.mirabiliahexica.registry

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.common.lib.hex.HexActions
import at.petrak.hexcasting.common.lib.HexRegistries
import com.mirabiliahexica.MirabiliaConfig
import com.mirabiliahexica.config.FeatureGated
import com.mirabiliahexica.casting.actions.math.OpSnapToAxis
import com.mirabiliahexica.casting.actions.queryblock.OpBlockSolid
import com.mirabiliahexica.casting.actions.queryentity.OpAmbientMedia
import com.mirabiliahexica.casting.actions.queryentity.OpDreamBearing
import com.mirabiliahexica.casting.actions.queryentity.OpReadTheSun
import com.mirabiliahexica.casting.actions.queryentity.OpDreamFooting
import com.mirabiliahexica.casting.actions.queryentity.OpDreamGaze
import com.mirabiliahexica.casting.actions.queryentity.OpHealth
import com.mirabiliahexica.casting.actions.queryentity.OpJumping
import com.mirabiliahexica.casting.actions.queryentity.OpSneaking
import com.mirabiliahexica.casting.actions.source.OpBindSource
import com.mirabiliahexica.casting.actions.source.OpSourceMedia
import com.mirabiliahexica.casting.actions.source.OpTether
import com.mirabiliahexica.casting.actions.source.OpUnbindSource
import com.mirabiliahexica.casting.actions.spells.OpBloodCharge
import com.mirabiliahexica.casting.actions.spells.OpImbueItem
import com.mirabiliahexica.casting.actions.spells.OpRechargeImbued
import com.mirabiliahexica.casting.actions.spells.OpFreeze
import com.mirabiliahexica.casting.actions.spells.OpThaw
import com.mirabiliahexica.casting.actions.spells.OpWake
import com.mirabiliahexica.casting.actions.spells.OpWardArmor
import com.mirabiliahexica.casting.actions.spells.OpWillApart
import com.mirabiliahexica.casting.actions.spells.OpWillSolid
import com.mirabiliahexica.casting.actions.spells.OpWillUpward
import com.mirabiliahexica.casting.actions.spells.great.OpCraftGreaterLens
import com.mirabiliahexica.casting.actions.spells.great.OpJoinPhials
import com.mirabiliahexica.casting.actions.standing.OpDispelStanding
import com.mirabiliahexica.casting.actions.standing.OpFeedStanding
import com.mirabiliahexica.casting.actions.standing.OpRaiseStanding
import com.mirabiliahexica.casting.actions.standing.OpRaiseStandingOnEntity
import com.mirabiliahexica.casting.actions.standing.OpStandingRemaining
import com.mirabiliahexica.casting.actions.strings.OpBlockName
import com.mirabiliahexica.casting.actions.strings.OpEntityName
import com.mirabiliahexica.casting.actions.strings.OpStringEquality
import com.mirabiliahexica.casting.actions.strings.OpParseNumber
import com.mirabiliahexica.casting.actions.strings.OpReadSign
import com.mirabiliahexica.casting.actions.strings.OpStringSplit
import com.mirabiliahexica.casting.actions.strings.OpStringify
import com.mirabiliahexica.macro.OpBindMacro
import com.mirabiliahexica.macro.OpMacroList
import com.mirabiliahexica.macro.OpReleaseMacro
import com.mirabiliahexica.casting.actions.strings.OpChatEcho
import com.mirabiliahexica.casting.actions.strings.OpMemoryRead
import com.mirabiliahexica.casting.actions.strings.OpMemoryWrite
import com.mirabiliahexica.casting.actions.strings.OpPalaceSize

/**
 * Every pattern this mod adds; ids become `mirabilia:<name>`.
 *
 * Hex identifies patterns by angle signature alone (start direction only affects how the book draws them),
 * and a duplicate signature silently replaces the earlier pattern. Before adding one, check it against
 * HexActions.java and Hex's two shape-matched families: Numerical Reflection (starts with aqaa/dedd) and
 * Bookkeeper's Gambit (only straight lines and right-left dips).
 */
object MirabiliaActions : MirabiliaRegistrar<ActionRegistryEntry>(
    HexRegistries.ACTION,
    { HexActions.REGISTRY },
) {
    // Entity queries: Stadiometer's Purification (awq) plus a tail
    val SNEAKING = make("get_entity_sneaking", HexDir.NORTH_EAST, "awqaa", OpSneaking)
    val JUMPING = make("get_entity_jumping", HexDir.NORTH_EAST, "awqwqw", OpJumping)
    val HEALTH = make("get_entity_health", HexDir.NORTH_EAST, "awqwaq", OpHealth(max = false))
    val MAX_HEALTH = make("get_entity_max_health", HexDir.NORTH_EAST, "awqwaqw", OpHealth(max = true))

    // The ambient media where the cast happens
    val AMBIENT_MEDIA = make("ambient_media", HexDir.EAST, "aawqqwaa", OpAmbientMedia, "ambient_media")

    // Macros: a player's own patterns standing for whole hexes
    val MACRO_BIND = make("macro/bind", HexDir.EAST, "wqwaqawqw", OpBindMacro, "macros")
    val MACRO_RELEASE = make("macro/release", HexDir.EAST, "ewdwe", OpReleaseMacro, "macros")
    val MACRO_LIST = make("macro/list", HexDir.EAST, "eadwdae", OpMacroList, "macros")

    // How strongly the Sun thinks where the cast happens
    val READ_THE_SUN = make("read_the_sun", HexDir.EAST, "qaqqwqaq", OpReadTheSun, "ambient_media")

    // Vector arithmetic: snap to the nearest axis
    val SNAP_TO_AXIS = make("snap_to_axis", HexDir.EAST, "waqaww", OpSnapToAxis)

    // Block query: a closed hexagon (a block) with a stub on each side
    val BLOCK_SOLID = make("get_block_solid", HexDir.EAST, "wqqqqqw", OpBlockSolid)

    // Mirror images of each other
    val FREEZE = make("freeze", HexDir.EAST, "wqwqwqwqw", OpFreeze)
    val THAW = make("thaw", HexDir.EAST, "wewewewew", OpThaw)

    // A shield: wards the armor piece in the other hand against other casters
    val WARD_ARMOR = make("ward_armor", HexDir.EAST, "qwqqqwqaeaq", OpWardArmor, "ward_armor")

    // Tanako's World: an eye opening (eeqee) and what the eye wills. Only work in the dream.
    val WAKE = make("wake", HexDir.EAST, "eeqeeweeqee", OpWake, "tanakos_world")
    val DREAM_GAZE = make("dream_gaze", HexDir.EAST, "eeqeeww", OpDreamGaze, "tanakos_world")
    val DREAM_FOOTING = make("dream_footing", HexDir.EAST, "eeqeedw", OpDreamFooting, "tanakos_world")
    val DREAM_BEARING = make("dream_bearing", HexDir.EAST, "eeqeewe", OpDreamBearing, "tanakos_world")
    val WILL_SOLID = make("will_solid", HexDir.EAST, "eeqeeqa", OpWillSolid, "tanakos_world")
    val WILL_UPWARD = make("will_upward", HexDir.EAST, "eeqeewaq", OpWillUpward, "tanakos_world")
    val WILL_APART = make("will_apart", HexDir.EAST, "eeqeeqwe", OpWillApart, "tanakos_world")

    // Great spells: listed in the per_world_pattern / requires_enlightenment / can_start_enlighten tags
    // (data/hexcasting/tags/...), so each world scrambles their shape and only the enlightened can cast them.
    // These signatures are only the prototypes the registry needs.
    val CRAFT_GREATER_LENS = make("craft/greater_lens", HexDir.EAST, "wqqqqqwdeeeee", OpCraftGreaterLens)
    val JOIN_PHIALS = make("join_phials", HexDir.SOUTH_WEST, "aqqqaqwwaqqqwqwqwaa", OpJoinPhials, "join_phials")
    val BLOOD_CHARGE = make("blood_charge", HexDir.NORTH_WEST, "qqqqqeawwaeq", OpBloodCharge, "blood_charge")
    // The imbuing great spells are double hexagons like Craft Artifact's, larger, told apart by their spikes:
    // a six-pointed star for the lens, one spike up and down for the tool, two for the armor.
    // Stores a hex in a lens; the worn lens runs it and draws its [where, what] pairs on screen
    val IMBUE_LENS = make("imbue_lens", HexDir.EAST, "wwwdeeeeeqwawwqwwqwwqwwqwwqwqwwwwawwwwwawwwqwwwawwwwwawwwwwawewae", OpImbueItem(OpImbueItem.Kind.LENS), "imbue_lens")
    // Stores a hex in armor (runs while worn) or a tool (casts on use), paid from their own media
    val IMBUE_ARMOR = make("imbue_armor", HexDir.NORTH_EAST, "wwweedwwdwwdwqwweqawwawwawewwewwweedwwdwwdwqwweqawwawwawewwdwwwwwawqwqwqwqwqwew", OpImbueItem(OpImbueItem.Kind.ARMOR), "imbue_armor")
    val IMBUE_TOOL = make("imbue_tool", HexDir.NORTH_EAST, "wwwewwedwwdwwdwqewwwewwwewwedwwdwwdwqewwwdwwwwwawqwqwqwqwqwew", OpImbueItem(OpImbueItem.Kind.TOOL), "imbue_tool")

    // Standing spells: a small base, a pillar (qwaeawq + www), then a tail per pattern
    val STANDING_RAISE = make("standing/raise", HexDir.EAST, "qwaeawqwww", OpRaiseStanding, "standing_spells")
    val STANDING_RAISE_ENTITY = make("standing/raise_entity", HexDir.EAST, "qwaeawqwwwqaq", OpRaiseStandingOnEntity, "standing_spells")
    val STANDING_REMAINING = make("standing/remaining", HexDir.EAST, "qwaeawqwwwd", OpStandingRemaining, "standing_spells")
    val STANDING_FEED = make("standing/feed", HexDir.EAST, "qwaeawqwwwew", OpFeedStanding, "standing_spells")
    val STANDING_DISPEL = make("standing/dispel", HexDir.EAST, "qwaeawqwwwaa", OpDispelStanding, "standing_spells")

    // Media sources: a wellspring (wawqwaw) plus a tail. The bound source pays first for the rest of the hex
    val SOURCE_BIND = make("source/bind", HexDir.EAST, "wawqwawa", OpBindSource, "wellsprings")
    val SOURCE_UNBIND = make("source/unbind", HexDir.EAST, "wawqwaww", OpUnbindSource, "wellsprings")
    val SOURCE_MEDIA = make("source/media", HexDir.EAST, "wawqwawe", OpSourceMedia, "wellsprings")
    // Tethers a lens, imbued item or casting item to a wellspring, for all its hexes
    val SOURCE_TETHER = make("source/tether", HexDir.EAST, "wawqwawaq", OpTether, "wellsprings")
    // Refills an imbued armor piece or tool from a dropped stack of amethyst. The imbuing figure's plain double
    // hexagon, with a small hexagon on either side like cells of a battery
    val RECHARGE_IMBUED = make("recharge_imbued", HexDir.NORTH_EAST, "eewwqwqwweeeeewwqwqwweedwwwwdeeeeeqww", OpRechargeImbued, "recharge_imbued")

    // Strings: a quill stroke (ddwddw) plus a tail. Null when the config turns them off (e.g. MoreIotas is present).
    // Joining, length, slicing etc. use Hex's own arithmetic patterns (MirabiliaArithmetics)
    private val strings = MirabiliaConfig.stringPatterns
    val STRING_FROM = makeIf(strings, "string/from", HexDir.EAST, "ddwddw", OpStringify)
    val STRING_NAME = makeIf(strings, "string/name", HexDir.EAST, "ddwddwqq", OpEntityName)
    val STRING_BLOCK_NAME = makeIf(strings, "string/block_name", HexDir.EAST, "ddwddwqqa", OpBlockName)
    val STRING_EQUALS = makeIf(strings, "string/equals", HexDir.EAST, "ddwddwew", OpStringEquality(strict = false))
    val STRING_EQUALS_STRICT = makeIf(strings, "string/equals_strict", HexDir.EAST, "ddwddwwew", OpStringEquality(strict = true))
    val STRING_SPLIT = makeIf(strings, "string/split", HexDir.EAST, "ddwddwqe", OpStringSplit)
    val STRING_PARSE = makeIf(strings, "string/parse", HexDir.EAST, "ddwddweq", OpParseNumber)
    val STRING_SIGN = makeIf(strings, "string/sign", HexDir.EAST, "ddwddwaw", OpReadSign)
    // The caster's own chat, and their mind palace
    val CHAT_ECHO = makeIf(strings, "string/chat", HexDir.EAST, "ddwddwww", OpChatEcho, "chat_reading")
    val MEMORY_READ = makeIf(strings, "string/memory_read", HexDir.EAST, "ddwddwwq", OpMemoryRead, "mind_palace")
    val MEMORY_WRITE = makeIf(strings, "string/memory_write", HexDir.EAST, "ddwddwwe", OpMemoryWrite, "mind_palace")
    val PALACE_SIZE = makeIf(strings, "string/palace_size", HexDir.EAST, "ddwddwqw", OpPalaceSize, "mind_palace")

    private fun makeIf(enabled: Boolean, name: String, startDir: HexDir, signature: String, action: Action, feature: String? = null) =
        if (enabled) make(name, startDir, signature, action, feature) else null

    /** A pattern; with a [feature], one that means nothing while that feature is off ([FeatureGated]). */
    private fun make(name: String, startDir: HexDir, signature: String, action: Action, feature: String? = null) =
        register(name) {
            val pattern = HexPattern.fromAngles(signature, startDir)
            ActionRegistryEntry(pattern, if (feature == null) action else FeatureGated(feature, action, pattern))
        }
}
