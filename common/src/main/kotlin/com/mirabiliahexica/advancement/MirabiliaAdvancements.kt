package com.mirabiliahexica.advancement

import com.mirabiliahexica.Mirabilia
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3
import java.util.UUID
import com.mirabiliahexica.config.Tuning

/**
 * Our advancements (`data/mirabilia/advancements`). Those vanilla triggers can't see have a single
 * `minecraft:impossible` criterion, [DONE], which the code that notices the deed awards here.
 */
object MirabiliaAdvancements {
    const val DONE = "done"

    /** Players within this many blocks of a machine share in what it does. */
    val NEARBY get() = Tuning.Advancements.nearby

    /** How many dust a player's Solar Collectors must gather for "Kind Thoughts". */
    val KIND_THOUGHTS_DUST get() = Tuning.Advancements.kindThoughtsDust.toLong()
    val DIFFERENT_DAYS get() = Tuning.Advancements.differentDays
    val GLASS_VILLAGE get() = Tuning.Advancements.glassVillage

    fun award(player: ServerPlayer, name: String, criterion: String = DONE) {
        val advancement = player.server.advancements.getAdvancement(Mirabilia.id(name)) ?: return
        player.advancements.award(advancement, criterion)
    }

    fun awardNear(level: ServerLevel, pos: BlockPos, name: String) {
        val centre = Vec3.atCenterOf(pos)
        for (player in level.players()) if (player.position().distanceTo(centre) <= NEARBY) award(player, name)
    }

    fun has(player: ServerPlayer, name: String): Boolean {
        val advancement = player.server.advancements.getAdvancement(Mirabilia.id(name)) ?: return false
        return player.advancements.getOrStartProgress(advancement).isDone
    }

    /** A player's Solar Collectors gathered [media]. */
    fun solarGathered(server: MinecraftServer, owner: UUID, media: Long) {
        val records = Records.get(server)
        val total = records.solar.merge(owner, media, Long::plus)!!
        records.setDirty()
        if (total >= KIND_THOUGHTS_DUST * at.petrak.hexcasting.api.misc.MediaConstants.DUST_UNIT) {
            server.playerList.getPlayer(owner)?.let { award(it, "kind_thoughts") }
        }
    }

    /** [player] saw the sign on the sun on day [day]. */
    fun sawCaltrop(player: ServerPlayer, day: Long) {
        award(player, "caltrop")
        val records = Records.get(player.server)
        val days = records.caltropDays.getOrPut(player.uuid) { mutableSetOf() }
        if (days.add(day)) records.setDirty()
        if (days.size >= DIFFERENT_DAYS) award(player, "different_every_day")
    }

    /** The Glass Man followed [player] out into a villager. */
    fun possessed(player: ServerPlayer) {
        award(player, "new_shell")
        val records = Records.get(player.server)
        val count = records.possessions.merge(player.uuid, 1, Int::plus)!!
        records.setDirty()
        if (count >= GLASS_VILLAGE) award(player, "glass_village")
    }

    /** The tallies some advancements need, per player. Saved with the overworld. */
    class Records : SavedData() {
        val solar = HashMap<UUID, Long>()
        val caltropDays = HashMap<UUID, MutableSet<Long>>()
        val possessions = HashMap<UUID, Int>()

        override fun save(tag: CompoundTag): CompoundTag {
            tag.put("solar", ListTag().apply { solar.forEach { (id, n) -> add(CompoundTag().apply { putUUID("id", id); putLong("n", n) }) } })
            tag.put("caltrop", ListTag().apply {
                caltropDays.forEach { (id, days) ->
                    add(CompoundTag().apply { putUUID("id", id); put("days", ListTag().apply { days.forEach { add(LongTag.valueOf(it)) } }) })
                }
            })
            tag.put("possessions", ListTag().apply { possessions.forEach { (id, n) -> add(CompoundTag().apply { putUUID("id", id); putInt("n", n) }) } })
            return tag
        }

        companion object {
            fun get(server: MinecraftServer): Records =
                server.overworld().dataStorage.computeIfAbsent(::load, ::Records, "${Mirabilia.MODID}_advancement_records")

            private fun load(tag: CompoundTag) = Records().apply {
                for (e in tag.getList("solar", Tag.TAG_COMPOUND.toInt())) (e as CompoundTag).let { solar[it.getUUID("id")] = it.getLong("n") }
                for (e in tag.getList("caltrop", Tag.TAG_COMPOUND.toInt())) (e as CompoundTag).let {
                    caltropDays[it.getUUID("id")] = it.getList("days", Tag.TAG_LONG.toInt()).map { d -> (d as LongTag).asLong }.toMutableSet()
                }
                for (e in tag.getList("possessions", Tag.TAG_COMPOUND.toInt())) (e as CompoundTag).let { possessions[it.getUUID("id")] = it.getInt("n") }
            }
        }
    }
}
