package com.mirabiliahexica.casting.actions.queryentity

import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.ambient.AmbientMedia
import net.minecraft.core.BlockPos

/** Ambient Reflection: nothing → number. The ambient media hanging where the cast happens, in dust. */
object OpAmbientMedia : ConstMediaAction {
    override val argc = 0

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val here = AmbientMedia.get(env.world, BlockPos.containing(AmbientMedia.castPos(env)))
        return listOf(DoubleIota(here.toDouble() / MediaConstants.DUST_UNIT))
    }
}
