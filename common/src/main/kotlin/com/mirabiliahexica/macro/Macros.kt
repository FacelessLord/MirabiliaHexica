package com.mirabiliahexica.macro

import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.castables.SpecialHandler
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.OperationResult
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate
import at.petrak.hexcasting.api.casting.eval.vm.FrameFinishEval
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID
import com.mirabiliahexica.config.Features
import com.mirabiliahexica.MirabiliaConfig
import com.mirabiliahexica.config.Tuning

/**
 * Macros: a player's own short patterns, each standing for a whole hex. Drawn in any hex that player casts, a
 * macro's hex runs in its place as Hermes' Gambit would run it, for [CALL_COST]. Hex looks up its own patterns
 * and the per-world great spells first, so a macro can never hide one; binding one that already means something
 * is refused (see [OpBindMacro]). Each player may hold [baseLimit], plus more per [milestones] advancement.
 */
object Macros {
    val CALL_COST get() = Tuning.Spells.macroCallCost

    /** How many every player may keep (config). */
    val baseLimit get() = MirabiliaConfig.macroStartingCount

    /** Advancements that each raise the limit, and by how much (config). */
    val milestones: Map<ResourceLocation, Int> get() = MirabiliaConfig.macroMilestones.mapNotNull { (id, n) -> ResourceLocation.tryParse(id)?.let { it to n } }.toMap()

    /** The most anyone can keep, with every milestone. */
    val maxLimit get() = baseLimit + milestones.values.sum()

    fun limit(player: ServerPlayer) = baseLimit + milestones.entries.sumOf { (id, n) ->
        val done = player.server.advancements.getAdvancement(id)?.let { player.advancements.getOrStartProgress(it).isDone } == true
        if (done) n else 0
    }

    /** [player]'s macros: the pattern each was bound with, by angle signature. */
    fun of(player: ServerPlayer): Map<String, HexPattern> =
        Data.get(player.server).macros[player.uuid]?.mapValues { it.value.pattern }.orEmpty()

    fun find(server: MinecraftServer, owner: UUID, pattern: HexPattern): Macro? =
        Data.get(server).macros[owner]?.get(pattern.anglesSignature())

    fun bind(player: ServerPlayer, pattern: HexPattern, hex: ListIota) {
        val data = Data.get(player.server)
        val mine = data.macros.getOrPut(player.uuid) { LinkedHashMap() }
        mine[pattern.anglesSignature()] = Macro(pattern, IotaType.serialize(hex))
        data.setDirty()
        MirabiliaAdvancements.award(player, "short_speech")
        if (mine.size >= maxLimit) MirabiliaAdvancements.award(player, "full_vocabulary")
    }

    /** Forgets [player]'s macro for [pattern]; false if there was none. */
    fun release(player: ServerPlayer, pattern: HexPattern): Boolean {
        val data = Data.get(player.server)
        val removed = data.macros[player.uuid]?.remove(pattern.anglesSignature()) != null
        if (removed) data.setDirty()
        return removed
    }

    class Macro(val pattern: HexPattern, val hex: CompoundTag)

    /** Recognises the caster's own macros among the patterns Hex doesn't know. */
    class Factory : SpecialHandler.Factory<Handler> {
        override fun tryMatch(pattern: HexPattern, env: CastingEnvironment): Handler? {
            if (!Features.macros) return null
            val caster = env.castingEntity as? ServerPlayer ?: return null
            val macro = find(caster.server, caster.uuid, pattern) ?: return null
            return Handler(macro)
        }
    }

    class Handler(private val macro: Macro) : SpecialHandler {
        override fun act(): Action = Call(macro)

        override fun getName(): Component = Component.translatable("hexcasting.special.mirabilia:macro")
    }

    /** Runs the macro's hex in place, like Hermes' Gambit given that list. */
    private class Call(private val macro: Macro) : Action {
        override fun operate(env: CastingEnvironment, image: CastingImage, continuation: SpellContinuation): OperationResult {
            val hex = IotaType.deserialize(macro.hex, env.world as ServerLevel) as? ListIota
                ?: return OperationResult(image.withUsedOp(), listOf(), continuation, HexEvalSounds.MISHAP)
            // A break boundary after it, as Hermes' Gambit makes
            val next = if (continuation is SpellContinuation.NotDone && continuation.frame is FrameFinishEval) continuation
                else continuation.pushFrame(FrameFinishEval)
            return OperationResult(
                image.withUsedOp(),
                listOf(OperatorSideEffect.ConsumeMedia(CALL_COST)),
                next.pushFrame(FrameEvaluate(hex.list, true)),
                HexEvalSounds.HERMES,
            )
        }
    }

    /** Every player's macros. Saved with the overworld. */
    class Data : SavedData() {
        val macros = HashMap<UUID, LinkedHashMap<String, Macro>>()

        override fun save(tag: CompoundTag): CompoundTag {
            tag.put("players", ListTag().apply {
                macros.forEach { (owner, mine) ->
                    add(CompoundTag().apply {
                        putUUID("owner", owner)
                        put("macros", ListTag().apply {
                            mine.values.forEach { add(CompoundTag().apply { put("pattern", it.pattern.serializeToNBT()); put("hex", it.hex) }) }
                        })
                    })
                }
            })
            return tag
        }

        companion object {
            fun get(server: MinecraftServer): Data = server.overworld().dataStorage.computeIfAbsent(::load, ::Data, "${Mirabilia.MODID}_macros")

            private fun load(tag: CompoundTag) = Data().apply {
                for (p in tag.getList("players", Tag.TAG_COMPOUND.toInt())) {
                    p as CompoundTag
                    val mine = LinkedHashMap<String, Macro>()
                    for (m in p.getList("macros", Tag.TAG_COMPOUND.toInt())) {
                        m as CompoundTag
                        val pattern = HexPattern.fromNBT(m.getCompound("pattern"))
                        mine[pattern.anglesSignature()] = Macro(pattern, m.getCompound("hex"))
                    }
                    macros[p.getUUID("owner")] = mine
                }
            }
        }
    }
}
