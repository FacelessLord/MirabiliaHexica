package com.mirabiliahexica.casting.arithmetic

import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic
import at.petrak.hexcasting.api.casting.arithmetic.engine.InvalidOperatorException
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator
import at.petrak.hexcasting.api.casting.arithmetic.operator.OperatorBasic
import at.petrak.hexcasting.api.casting.arithmetic.operator.OperatorBinary
import at.petrak.hexcasting.api.casting.arithmetic.operator.OperatorUnary
import at.petrak.hexcasting.api.casting.arithmetic.predicates.IotaMultiPredicate
import at.petrak.hexcasting.api.casting.arithmetic.predicates.IotaPredicate
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.BooleanIota
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.common.casting.arithmetic.operator.nextPositiveIntUnderInclusive
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes
import com.mirabiliahexica.casting.actions.strings.textOf
import com.mirabiliahexica.casting.iota.StringIota
import kotlin.math.max
import kotlin.math.min

/**
 * Teaches Hex's own arithmetic patterns about strings, the same way Hex's list arithmetic works for lists:
 *
 * * Additive Distillation: a + b, joined. If only one side is a string, the other is written out first.
 * * Length Purification: number of characters.
 * * Selection Distillation: string, index → that one character as a string (null if out of range).
 * * Selection Exaltation: string, start, end → the characters between them (indices as for lists).
 * * Retrograde Purification: the string reversed.
 * * Locator's Distillation: string, piece → where the piece first appears, or -1.
 * * Maximus / Minimus Distillation (and their II forms): alphabetical order, ignoring letter case first.
 */
object StringArithmetic : Arithmetic {
    // StringIota.TYPE, not the registry entry: on Forge the arithmetic registry fills before the iota-type one
    private val STRING = IotaPredicate.ofType(StringIota.TYPE)
    private val NUMBER = IotaPredicate.ofType(HexIotaTypes.DOUBLE)

    private val OPS = listOf(
        Arithmetic.ADD, Arithmetic.ABS, Arithmetic.INDEX, Arithmetic.SLICE, Arithmetic.REV, Arithmetic.INDEX_OF,
        Arithmetic.GREATER, Arithmetic.LESS, Arithmetic.GREATER_EQ, Arithmetic.LESS_EQ,
    )

    /** Alphabetical, ignoring case; exact order only breaks ties, so "apple" < "Banana" < "banana". */
    private val ALPHABETICAL = String.CASE_INSENSITIVE_ORDER.thenComparing(naturalOrder())

    override fun arithName() = "mirabilia_string_ops"

    override fun opTypes(): Iterable<at.petrak.hexcasting.api.casting.math.HexPattern> = OPS

    override fun getOperator(pattern: at.petrak.hexcasting.api.casting.math.HexPattern): Operator = when (pattern) {
        Arithmetic.ADD -> OperatorBinary(IotaMultiPredicate.any(STRING, IotaPredicate.TRUE)) { a, b -> StringIota(textOf(a) + textOf(b)) }
        Arithmetic.ABS -> OperatorUnary(IotaMultiPredicate.all(STRING)) { s -> DoubleIota(str(s).length.toDouble()) }
        Arithmetic.REV -> OperatorUnary(IotaMultiPredicate.all(STRING)) { s -> StringIota(str(s).reversed()) }
        Arithmetic.INDEX -> OperatorBinary(IotaMultiPredicate.pair(STRING, NUMBER)) { s, i ->
            val index = (i as DoubleIota).double
            val text = str(s)
            val at = index.toInt()
            if (index != at.toDouble() || at !in text.indices) NullIota() else StringIota(text[at].toString())
        }
        Arithmetic.INDEX_OF -> OperatorBinary(IotaMultiPredicate.pair(STRING, STRING)) { s, piece ->
            DoubleIota(str(s).indexOf(str(piece)).toDouble())
        }
        Arithmetic.SLICE -> OperatorSlice
        Arithmetic.GREATER -> compare { it > 0 }
        Arithmetic.LESS -> compare { it < 0 }
        Arithmetic.GREATER_EQ -> compare { it >= 0 }
        Arithmetic.LESS_EQ -> compare { it <= 0 }
        else -> throw InvalidOperatorException("$pattern is not a valid operator in Arithmetic $this.")
    }

    private fun str(iota: Iota) = (iota as StringIota).string

    private fun compare(test: (Int) -> Boolean) = OperatorBinary(IotaMultiPredicate.pair(STRING, STRING)) { a, b ->
        BooleanIota(test(ALPHABETICAL.compare(str(a), str(b))))
    }

    /** Same rules as Hex's list slice: both indices within 0..length, in either order. */
    private object OperatorSlice : OperatorBasic(3, IotaMultiPredicate.triple(STRING, NUMBER, NUMBER)) {
        override fun apply(iotas: Iterable<Iota>, env: CastingEnvironment): Iterable<Iota> {
            val it = iotas.iterator().withIndex()
            val text = str(it.next().value)
            val index0 = it.nextPositiveIntUnderInclusive(text.length, arity)
            val index1 = it.nextPositiveIntUnderInclusive(text.length, arity)
            return listOf(StringIota(text.substring(min(index0, index1), max(index0, index1))))
        }
    }
}
