package com.mirabiliahexica.casting.actions.queryentity

import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import com.mirabiliahexica.ambient.AmbientMedia
import com.mirabiliahexica.machines.Sunlight
import net.minecraft.core.BlockPos

/** Read the Sun: nothing → number. How strongly the Sun thinks where the cast happens, 0 to 1 (see [Sunlight]). */
object OpReadTheSun : ConstMediaAction {
    override val argc = 0

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> =
        listOf(DoubleIota(Sunlight.intensity(env.world, BlockPos.containing(AmbientMedia.castPos(env)))))
}
