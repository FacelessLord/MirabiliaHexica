package com.mirabiliahexica.wards

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv
import com.mirabiliahexica.dream.DreamRules
import com.mirabiliahexica.standing.StandingSpellEnv
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.WeakHashMap
import com.mirabiliahexica.config.Tuning
import com.mirabiliahexica.config.Features

/**
 * Magic suppression. Every casting environment (staff, circle, lens, standing spell, other addons' wisps...) gets
 * a range hook when it is created, so these rules apply to all of them on top of their own ambit:
 *
 * * **Warding Stone**: anything within [RADIUS] blocks of it is out of range.
 * * **Silence Stone**: a cast happening within [RADIUS] blocks of it has nothing in range at all. Stack and math
 *   patterns still work; anything that touches the world fails.
 * * **Warded armor**: the wearer can't be found or targeted by anyone else's casts, even through a player's
 *   entity iota (see `MixinCastingEnvironment`; the stones win over that too). Aiming at a spot near them still
 *   works.
 *
 * A stone spares the casts of whoever placed it, unless they toggle that off.
 */
object Wards {
    enum class Kind { WARDING, SILENCE }

    val RADIUS get() = Tuning.Spells.wardRadius

    private val loaded = WeakHashMap<Level, MutableSet<WardStoneBlockEntity>>()

    fun add(level: Level, stone: WardStoneBlockEntity) {
        loaded.getOrPut(level, ::mutableSetOf).add(stone)
    }

    fun remove(level: Level, stone: WardStoneBlockEntity) {
        loaded[level]?.remove(stone)
    }

    fun init() {
        CastingEnvironment.addCreateEventListener { env, _ -> env.addExtension(RangeHook(env)) }
    }

    /** Whether the wards refuse [env] reaching [vec]. */
    fun refuses(env: CastingEnvironment, vec: Vec3): Boolean {
        if (!Features.wardingStones) return false
        val stones = loaded[env.world].orEmpty()
        if (stones.isNotEmpty()) {
            val caster = casterId(env)
            val castPos by lazy { castPos(env) }
            for (stone in stones) {
                if (stone.isRemoved || stone.spares(caster)) continue
                val checked = when (stone.kind) {
                    Kind.WARDING -> vec
                    Kind.SILENCE -> castPos
                }
                if (checked.distanceToSqr(Vec3.atCenterOf(stone.blockPos)) <= RADIUS * RADIUS) return true
            }
        }
        return false
    }

    /**
     * Whether [env] is kept from [entity]: by warded armor (from everyone but the wearer), by standing in a
     * guarded or silenced place, or because the whole cast is silenced (see [DreamRules]). Checked on top of the
     * range hook because a player's entity iota skips range.
     */
    @JvmStatic
    fun refusesEntity(env: CastingEnvironment, entity: Entity) = DreamRules.silenced(env) ||
        (entity is LivingEntity && entity.uuid != casterId(env) && ArmorWard.protects(entity)) ||
        refuses(env, entity.position())

    /** Who a cast acts for: the casting entity, or the owner of a standing spell that has no body. */
    private fun casterId(env: CastingEnvironment): UUID? =
        env.castingEntity?.uuid ?: (env as? StandingSpellEnv)?.ownerId

    /** Where the cast happens: the caster for player casts, otherwise the circle's impetus, the standing spell... */
    private fun castPos(env: CastingEnvironment): Vec3 =
        (env as? PlayerBasedCastEnv)?.castingEntity?.position() ?: env.mishapSprayPos()

    private class RangeHook(private val env: CastingEnvironment) : CastingEnvironmentComponent.IsVecInRange {
        override fun getKey() = KEY

        override fun onIsVecInRange(vec: Vec3, current: Boolean) = current && !refuses(env, vec)
    }

    private val KEY = object : CastingEnvironmentComponent.Key<RangeHook> {}
}
