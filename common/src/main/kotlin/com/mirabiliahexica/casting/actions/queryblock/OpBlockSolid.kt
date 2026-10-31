package com.mirabiliahexica.casting.actions.queryblock

import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getBlockPos
import at.petrak.hexcasting.api.casting.iota.Iota

/**
 * Mason's Purification: position → whether the block there is solid (Minecraft's own `BlockState.isSolid`):
 * stone, logs and glass are; air, water, grass, torches and flowers aren't.
 */
object OpBlockSolid : ConstMediaAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val pos = args.getBlockPos(0, argc)
        env.assertPosInRange(pos)
        @Suppress("DEPRECATION") // still the vanilla meaning of "solid" in 1.20.1
        return env.world.getBlockState(pos).isSolid.asActionResult
    }
}
