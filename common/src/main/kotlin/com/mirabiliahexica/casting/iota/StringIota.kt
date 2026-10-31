package com.mirabiliahexica.casting.iota

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.utils.downcast
import net.minecraft.ChatFormatting
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel

/**
 * A piece of text. Shown without quotes, so it reads naturally on a lens label; the colour marks it as a string
 * on the stack. Capped at [MAX_LENGTH] characters so a hex can't build huge strings.
 */
class StringIota(value: String) : Iota(TYPE, value.take(MAX_LENGTH)) {
    val string: String get() = payload as String

    override fun isTruthy() = string.isNotEmpty()

    override fun toleratesOther(that: Iota) = that is StringIota && that.string == string

    override fun serialize(): Tag = StringTag.valueOf(string)

    companion object {
        const val MAX_LENGTH = 1024
        private const val COLOR = 0xff_ffd27f.toInt()

        fun display(value: String): Component = Component.literal(value).withStyle(ChatFormatting.GOLD)

        val TYPE = object : IotaType<StringIota>() {
            override fun deserialize(tag: Tag, world: ServerLevel) = StringIota(tag.downcast(StringTag.TYPE).asString)

            override fun display(tag: Tag): Component = display(tag.downcast(StringTag.TYPE).asString)

            override fun color() = COLOR
        }
    }
}
