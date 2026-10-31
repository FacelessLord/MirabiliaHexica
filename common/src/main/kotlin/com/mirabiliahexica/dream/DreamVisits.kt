package com.mirabiliahexica.dream

import com.mirabiliahexica.Mirabilia
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Where a dreamer came from, so they can wake there; and how cold the dream has made them. */
class DreamVisit(
    val player: UUID,
    val returnDim: ResourceKey<Level>,
    val returnPos: Vec3,
    /** The bed they fell asleep in; they wake beside it if it's still there. */
    val bed: BlockPos?,
    var cold: Int = 0,
    /** How long they've been dreaming, in ticks. The longer, the more Lost Dreamers find them. */
    var ticks: Int = 0,
) {
    fun save() = CompoundTag().apply {
        putUUID("player", player)
        putString("dim", returnDim.location().toString())
        putDouble("x", returnPos.x)
        putDouble("y", returnPos.y)
        putDouble("z", returnPos.z)
        bed?.let { put("bed", NbtUtils.writeBlockPos(it)) }
        putInt("cold", cold)
        putInt("ticks", ticks)
    }

    companion object {
        fun load(tag: CompoundTag) = DreamVisit(
            tag.getUUID("player"),
            ResourceKey.create(Registries.DIMENSION, ResourceLocation(tag.getString("dim"))),
            Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
            if (tag.contains("bed")) NbtUtils.readBlockPos(tag.getCompound("bed")) else null,
            tag.getInt("cold"),
            tag.getInt("ticks"),
        )
    }
}

/** Every dream in progress, plus who died in one and has yet to come back wrong. Saved with the overworld. */
class DreamVisits : SavedData() {
    private val visits = mutableMapOf<UUID, DreamVisit>()
    private val diedDreaming = mutableSetOf<UUID>()
    /** What those who died dreaming carried (inventory and experience), given back when they respawn. */
    private val kept = mutableMapOf<UUID, CompoundTag>()

    operator fun get(player: UUID) = visits[player]

    fun start(visit: DreamVisit) {
        visits[visit.player] = visit
        setDirty()
    }

    fun end(player: UUID): DreamVisit? = visits.remove(player)?.also { setDirty() }

    fun markDied(player: UUID) {
        diedDreaming += player
        setDirty()
    }

    fun keep(player: UUID, belongings: CompoundTag) {
        kept[player] = belongings
        setDirty()
    }

    fun takeKept(player: UUID): CompoundTag? = kept.remove(player)?.also { setDirty() }

    /** True once per death in a dream. */
    fun takeDied(player: UUID) = diedDreaming.remove(player).also { if (it) setDirty() }

    override fun save(tag: CompoundTag): CompoundTag {
        tag.put("visits", ListTag().apply { visits.values.forEach { add(it.save()) } })
        tag.put("died", ListTag().apply { diedDreaming.forEach { add(NbtUtils.createUUID(it)) } })
        tag.put("kept", ListTag().apply {
            kept.forEach { (player, items) -> add(CompoundTag().apply { putUUID("player", player); put("kept", items) }) }
        })
        return tag
    }

    companion object {
        private const val DATA_NAME = "${Mirabilia.MODID}_dreams"

        fun get(server: MinecraftServer): DreamVisits =
            server.overworld().dataStorage.computeIfAbsent(::load, ::DreamVisits, DATA_NAME)

        private fun load(tag: CompoundTag) = DreamVisits().apply {
            for (entry in tag.getList("visits", Tag.TAG_COMPOUND.toInt())) {
                val visit = DreamVisit.load(entry as CompoundTag)
                visits[visit.player] = visit
            }
            for (entry in tag.getList("died", Tag.TAG_INT_ARRAY.toInt())) diedDreaming += NbtUtils.loadUUID(entry)
            for (entry in tag.getList("kept", Tag.TAG_COMPOUND.toInt())) {
                entry as CompoundTag
                kept[entry.getUUID("player")] = entry.getCompound("kept")
            }
        }
    }
}
