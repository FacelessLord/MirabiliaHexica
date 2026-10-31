package com.mirabiliahexica.casting.actions.strings

import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getBlockPos
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import com.mirabiliahexica.casting.iota.StringIota
import net.minecraft.world.level.block.entity.SignBlockEntity
import java.math.BigDecimal
import java.math.RoundingMode

// String patterns. All free, like Hex's other non-spell patterns. Turned off together by the config option
// `stringPatterns` (see MirabiliaConfig), e.g. when MoreIotas provides its own strings. Joining, length,
// slicing and the like reuse Hex's arithmetic patterns instead: see StringArithmetic.

private fun List<Iota>.getString(idx: Int, argc: Int): String {
    val iota = this[idx]
    return (iota as? StringIota)?.string ?: throw MishapInvalidIota.ofType(iota, argc - idx - 1, "mirabilia.string")
}

private fun stringResult(value: String) = listOf(StringIota(value))

/**
 * Any iota as text: a string stays itself; a number is written plainly ("7", "2.5"); anything else as Hex
 * would display it. Numbers get their own rule because Hex's display uses two decimals in the machine's locale
 * ("7,00" on some systems), which reads badly in a label.
 */
fun textOf(iota: Iota): String = when (iota) {
    is StringIota -> iota.string
    is DoubleIota -> plainNumber(iota.double)
    else -> iota.display().string
}

private fun plainNumber(value: Double): String =
    BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

/** Scribe's Purification: any → string, the way Hex displays it. */
object OpStringify : ConstMediaAction {
    override val argc = 1
    override fun execute(args: List<Iota>, env: CastingEnvironment) = stringResult(textOf(args[0]))
}

/** Nominal Purification: entity → its name as a string. */
object OpEntityName : ConstMediaAction {
    override val argc = 1
    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val entity = args.getEntity(0, argc)
        env.assertEntityInRange(entity)
        return stringResult(entity.displayName.string)
    }
}

/** Splitting Distillation: string, separator → list of the pieces between separators. */
object OpStringSplit : ConstMediaAction {
    override val argc = 2
    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val s = args.getString(0, argc)
        val separator = args.getString(1, argc)
        val pieces = if (separator.isEmpty()) s.map { it.toString() } else s.split(separator)
        return ListIota(pieces.map { StringIota(it) }).let(::listOf)
    }
}

/** Numeral Purification: string → the number it spells, or null if it isn't one. */
object OpParseNumber : ConstMediaAction {
    override val argc = 1
    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val number = args.getString(0, argc).trim().replace(',', '.').toDoubleOrNull()
        return number?.asActionResult ?: listOf(NullIota())
    }
}

/**
 * Inscription Purification: position → the text on the front of the sign there (non-blank lines joined by spaces),
 * or null if there's no sign. This is how players write their own text for a hex to use.
 */
object OpReadSign : ConstMediaAction {
    override val argc = 1
    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val pos = args.getBlockPos(0, argc)
        env.assertPosInRange(pos)
        val sign = env.world.getBlockEntity(pos) as? SignBlockEntity ?: return listOf(NullIota())
        val lines = sign.frontText.getMessages(false).map { it.string }
        return stringResult(lines.filter { it.isNotBlank() }.joinToString(" "))
    }
}

/** Nominal Purification II: position → the name of the block there ("Oak Log", "Air"…). */
object OpBlockName : ConstMediaAction {
    override val argc = 1
    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val pos = args.getBlockPos(0, argc)
        env.assertPosInRange(pos)
        return stringResult(env.world.getBlockState(pos).block.name.string)
    }
}

/**
 * Scholar's Distillation (`strict = false`): string, string → whether they match, ignoring letter case and spaces
 * at either end. Copyist's Distillation (`strict = true`): whether they are exactly the same.
 */
class OpStringEquality(private val strict: Boolean) : ConstMediaAction {
    override val argc = 2
    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val a = args.getString(0, argc)
        val b = args.getString(1, argc)
        val same = if (strict) a == b else a.trim().equals(b.trim(), ignoreCase = true)
        return same.asActionResult
    }
}
