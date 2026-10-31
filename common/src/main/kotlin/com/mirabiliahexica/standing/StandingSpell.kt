package com.mirabiliahexica.standing

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * A hex that keeps running on its own (inspired by Ra's persistent spells): bound either to a block or to an
 * entity, paid for from its own media pool. See [StandingSpells] for the rules.
 */
class StandingSpell(
    val id: UUID,
    val owner: UUID,
    val anchor: Anchor,
    /** The hex, serialized as a list iota; deserialized lazily because entity references need a level. */
    private val hexTag: CompoundTag,
    var media: Long,
    /** Carried between runs so the hex can keep state in its ravenmind. */
    var userData: CompoundTag = CompoundTag(),
    var ticksUntilRun: Int = 0,
) {
    sealed interface Anchor {
        data class Block(val dimension: ResourceKey<Level>, val pos: BlockPos) : Anchor
        /** Rides on an entity (any kind, the caster included), following it across dimensions. */
        data class Entity(val uuid: UUID) : Anchor
    }

    private var cachedHex: List<Iota>? = null

    /** The anchor entity as last seen; kept so we can tell an entity that was destroyed from one that unloaded. */
    var entityRef: net.minecraft.world.entity.Entity? = null

    fun hex(level: ServerLevel): List<Iota> = cachedHex ?: run {
        val iota = IotaType.deserialize(hexTag, level)
        (if (iota is ListIota) iota.list.toList() else listOf(iota)).also { cachedHex = it }
    }

    /** Seconds left at the base upkeep, not counting what the hex itself spends. */
    fun secondsRemaining(): Double = media.toDouble() / (StandingSpells.UPKEEP_PER_TICK * 20)

    fun save(): CompoundTag = CompoundTag().apply {
        // Qualified: inside apply, a bare `id` would be CompoundTag.getId()
        putUUID(TAG_ID, this@StandingSpell.id)
        putUUID(TAG_OWNER, owner)
        when (anchor) {
            is Anchor.Block -> {
                putString(TAG_DIMENSION, anchor.dimension.location().toString())
                put(TAG_POS, NbtUtils.writeBlockPos(anchor.pos))
            }
            is Anchor.Entity -> putUUID(TAG_ENTITY, anchor.uuid)
        }
        put(TAG_HEX, hexTag)
        putLong(TAG_MEDIA, media)
        put(TAG_USERDATA, userData)
        putInt(TAG_TICKS, ticksUntilRun)
    }

    companion object {
        private const val TAG_ID = "id"
        private const val TAG_OWNER = "owner"
        private const val TAG_DIMENSION = "dimension"
        private const val TAG_POS = "pos"
        private const val TAG_ENTITY = "entity"
        /** Saves from before spells could ride on any entity: it meant the owner. */
        private const val TAG_LEGACY_ON_CASTER = "on_caster"
        private const val TAG_HEX = "hex"
        private const val TAG_MEDIA = "media"
        private const val TAG_USERDATA = "userdata"
        private const val TAG_TICKS = "ticks_until_run"

        fun create(owner: UUID, anchor: Anchor, hex: List<Iota>, media: Long) =
            StandingSpell(UUID.randomUUID(), owner, anchor, IotaType.serialize(ListIota(hex)), media)

        fun load(tag: CompoundTag): StandingSpell {
            val anchor = when {
                tag.hasUUID(TAG_ENTITY) -> Anchor.Entity(tag.getUUID(TAG_ENTITY))
                tag.getBoolean(TAG_LEGACY_ON_CASTER) -> Anchor.Entity(tag.getUUID(TAG_OWNER))
                else -> Anchor.Block(
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation(tag.getString(TAG_DIMENSION))),
                    NbtUtils.readBlockPos(tag.getCompound(TAG_POS)),
                )
            }
            return StandingSpell(
                tag.getUUID(TAG_ID), tag.getUUID(TAG_OWNER), anchor, tag.getCompound(TAG_HEX),
                tag.getLong(TAG_MEDIA), tag.getCompound(TAG_USERDATA), tag.getInt(TAG_TICKS),
            )
        }
    }
}
