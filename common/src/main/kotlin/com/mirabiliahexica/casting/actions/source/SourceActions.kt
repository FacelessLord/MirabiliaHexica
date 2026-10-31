package com.mirabiliahexica.casting.actions.source

import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.OperationResult
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadBlock
import at.petrak.hexcasting.api.casting.mishaps.MishapBadEntity
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.mirabiliahexica.source.MediaSource
import com.mirabiliahexica.source.MediaSources
import net.minecraft.core.BlockPos
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import net.minecraft.server.level.ServerPlayer

/** Pops [argc] iotas, binds what [source] makes of them (null clears the binding), pushes nothing. */
abstract class SourceBindingAction(private val argc: Int) : Action {
    protected abstract fun source(args: List<Iota>, env: CastingEnvironment): MediaSource?

    override fun operate(env: CastingEnvironment, image: CastingImage, continuation: SpellContinuation): OperationResult {
        val stack = image.stack.toMutableList()
        if (argc > stack.size) throw MishapNotEnoughArgs(argc, stack.size)
        val args = stack.takeLast(argc)
        repeat(argc) { stack.removeLast() }
        val source = source(args, env)
        if (source != null) (env.castingEntity as? ServerPlayer)?.let { MirabiliaAdvancements.award(it, "supply_chain") }
        val bound = MediaSources.bind(env, image, source)
        return OperationResult(bound.copy(stack = stack, opsConsumed = image.opsConsumed + 1), listOf(), continuation, HexEvalSounds.NORMAL_EXECUTE)
    }
}

/** Wellspring Binding: vector | entity → nothing. Binds a container block, a dropped stack or a chest minecart. */
object OpBindSource : SourceBindingAction(1) {
    override fun source(args: List<Iota>, env: CastingEnvironment): MediaSource = when (val iota = args[0]) {
        is Vec3Iota -> {
            val pos = BlockPos.containing(iota.vec3)
            env.assertPosInRange(pos)
            if (!MediaSource.canHoldAt(env.world, pos)) throw MishapBadBlock.of(pos, "mirabilia.media_container")
            MediaSource.Block(pos)
        }
        is EntityIota -> {
            val entity = iota.entity
            env.assertEntityInRange(entity)
            if (!MediaSource.canHold(entity)) throw MishapBadEntity.of(entity, "mirabilia.media_source")
            MediaSource.Entity(entity.uuid)
        }
        else -> throw MishapInvalidIota.of(iota, 0, "mirabilia.vector_or_entity")
    }
}

/** Unbinding: nothing → nothing. The hex goes back to paying the usual way. */
object OpUnbindSource : SourceBindingAction(0) {
    override fun source(args: List<Iota>, env: CastingEnvironment): MediaSource? = null
}

/** Wellspring Reflection: nothing → number. The media left in the bound source, in dust; null if none is bound. */
object OpSourceMedia : ConstMediaAction {
    override val argc = 0

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val source = MediaSources.current(env) ?: return listOf(NullIota())
        return listOf(DoubleIota(source.available(env).toDouble() / MediaConstants.DUST_UNIT))
    }
}
