package com.mirabiliahexica.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getBlockPos
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation
import com.mirabiliahexica.casting.mishaps.MishapNotDreaming
import com.mirabiliahexica.dream.Dream
import com.mirabiliahexica.dream.DreamVisits
import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaTags
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.dream.DreamLore
import com.mirabiliahexica.config.Tuning

/**
 * The willing patterns: dream-only, and the dream's silence doesn't touch them, because they don't reach out with
 * media. They bend the dream's own ground (glass, marble, pebbles; see the `mirabilia:dream_shapeable` tag) by
 * will alone, within about [REACH] blocks of the dreamer. They cost no media; each one chills the dreamer, by
 * [COLD] or (for small work) less.
 */
abstract class OpWill : SpellAction {
    override val argc = 1

    protected open val cold = COLD

    protected abstract fun shape(level: ServerLevel, target: BlockPos)

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        DreamLore.require(env, this)
        val target = args.getBlockPos(0, argc)
        val caster = env.castingEntity as? ServerPlayer
        if (caster == null || !Dream.isDreaming(caster)) throw MishapNotDreaming("will")
        // Measured from the dreamer's middle, with a little slack, so a full reach's work along the gaze fits
        if (caster.position().add(0.0, 1.0, 0.0).distanceToSqr(Vec3.atCenterOf(target)) > (REACH + 1.5) * (REACH + 1.5)) {
            throw MishapBadLocation(Vec3.atCenterOf(target), "too_far")
        }
        return SpellAction.Result(Spell(caster, target), 0, listOf(ParticleSpray.cloud(Vec3.atCenterOf(target), 1.5)))
    }

    private inner class Spell(val caster: ServerPlayer, val target: BlockPos) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            shape(caster.serverLevel(), target)
            DreamVisits.get(caster.server)[caster.uuid]?.let { it.cold += cold }
        }
    }

    companion object {
        val REACH get() = Tuning.Dream.willReach
        /** Five seconds' worth of unprotected cold. */
        val COLD get() = Tuning.Dream.willCold

        fun shapeable(level: ServerLevel, pos: BlockPos) = level.getBlockState(pos).`is`(MirabiliaTags.DREAM_SHAPEABLE)
    }
}

/** Will Upward: position → nothing. The dream's ground rises one block in a disc of radius 2 around the target. */
object OpWillUpward : OpWill() {
    override fun shape(level: ServerLevel, target: BlockPos) {
        for (dx in -2..2) for (dz in -2..2) {
            if (dx * dx + dz * dz > 5) continue
            val ground = target.offset(dx, 0, dz)
            if (shapeable(level, ground) && level.getBlockState(ground.above()).isAir) {
                level.setBlockAndUpdate(ground.above(), level.getBlockState(ground))
            }
        }
    }
}

/**
 * Will Solid: position → nothing. A single block of dream glass forms there, if it's empty: the shade the ground
 * would have at that height. Small work, so it chills the dreamer only a quarter as much.
 */
object OpWillSolid : OpWill() {
    override val cold = COLD / 4

    override fun shape(level: ServerLevel, target: BlockPos) {
        if (!level.getBlockState(target).canBeReplaced()) return
        val glass = when {
            target.y < 8 -> MirabiliaBlocks.DEEP_DREAM_GLASS
            target.y < 84 -> MirabiliaBlocks.DUSKY_DREAM_GLASS
            else -> MirabiliaBlocks.DREAM_GLASS
        }
        level.setBlockAndUpdate(target, glass.value.defaultBlockState())
    }
}

/** Will Apart: position → nothing. The dream's ground opens: a 3×3×3 hollow around the target, leaving nothing behind. */
object OpWillApart : OpWill() {
    override fun shape(level: ServerLevel, target: BlockPos) {
        for (pos in BlockPos.betweenClosed(target.offset(-1, -1, -1), target.offset(1, 1, 1))) {
            if (shapeable(level, pos)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
        }
    }
}
