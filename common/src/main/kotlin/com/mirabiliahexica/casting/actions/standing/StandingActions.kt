package com.mirabiliahexica.casting.actions.standing

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.getList
import at.petrak.hexcasting.api.casting.getPositiveDouble
import at.petrak.hexcasting.api.casting.getVec3
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.misc.MediaConstants
import com.mirabiliahexica.standing.StandingSpell
import com.mirabiliahexica.standing.StandingSpells
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/** Converts a dust amount from the stack into media units. */
private fun dust(amount: Double): Long = (amount * MediaConstants.DUST_UNIT).toLong()

private fun spells(env: CastingEnvironment) = StandingSpells.get(env.world.server)

/** The owner of a new standing spell: the casting player. Circles and other casterless hexes can't raise one. */
private fun ownerOf(env: CastingEnvironment): ServerPlayer = env.castingEntity as? ServerPlayer ?: throw MishapBadCaster()

private fun checkQuota(env: CastingEnvironment, owner: ServerPlayer, at: Vec3) {
    if (spells(env).countOwnedBy(owner.uuid) >= StandingSpells.MAX_PER_PLAYER) {
        throw MishapBadLocation(at, "mirabilia.too_many_standing")
    }
}

/**
 * Finds the standing spell an iota names: a vector names the spell on that block, an entity names the spell
 * riding on it. Null if there is none.
 */
private fun findSpell(args: List<Iota>, idx: Int, argc: Int, env: CastingEnvironment): StandingSpell? =
    when (val iota = args[idx]) {
        is Vec3Iota -> {
            val pos = BlockPos.containing(iota.vec3)
            env.assertPosInRange(pos)
            spells(env).atBlock(env.world.dimension(), pos)
        }
        is EntityIota -> {
            env.assertEntityInRange(iota.entity)
            spells(env).onEntity(iota.entity.uuid)
        }
        else -> throw MishapInvalidIota.of(iota, argc - idx - 1, "mirabilia.standing_anchor")
    }

/** Raise Standing Spell: hex, position, dust → nothing. Binds the hex to that block, funded with that much dust. */
object OpRaiseStanding : SpellAction {
    override val argc = 3

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val hex = args.getList(0, argc).toList()
        val pos = BlockPos.containing(args.getVec3(1, argc))
        val funding = dust(args.getPositiveDouble(2, argc))
        env.assertPosInRange(pos)
        val owner = ownerOf(env)
        if (spells(env).atBlock(env.world.dimension(), pos) != null) {
            throw MishapBadLocation(Vec3.atCenterOf(pos), "mirabilia.standing_occupied")
        }
        checkQuota(env, owner, Vec3.atCenterOf(pos))

        val anchor = StandingSpell.Anchor.Block(env.world.dimension(), pos)
        return SpellAction.Result(
            Raise(StandingSpell.create(owner.uuid, anchor, hex, funding)),
            StandingSpells.STARTUP_COST + funding,
            listOf(ParticleSpray.burst(Vec3.atCenterOf(pos), 1.0)),
        )
    }
}

/**
 * Raise Standing Spell on Entity: hex, entity, dust → nothing. Like Raise Standing Spell, but the spell rides on
 * that entity (the caster included) and follows it. It ends when the entity dies or is destroyed.
 */
object OpRaiseStandingOnEntity : SpellAction {
    override val argc = 3

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val hex = args.getList(0, argc).toList()
        val target = args.getEntity(1, argc)
        val funding = dust(args.getPositiveDouble(2, argc))
        env.assertEntityInRange(target)
        val owner = ownerOf(env)
        if (spells(env).onEntity(target.uuid) != null) {
            throw MishapBadLocation(target.position(), "mirabilia.standing_occupied")
        }
        checkQuota(env, owner, target.position())

        return SpellAction.Result(
            Raise(StandingSpell.create(owner.uuid, StandingSpell.Anchor.Entity(target.uuid), hex, funding)),
            StandingSpells.STARTUP_COST + funding,
            listOf(ParticleSpray.cloud(target.position().add(0.0, target.bbHeight / 2.0, 0.0), 1.0)),
        )
    }
}

private data class Raise(val spell: StandingSpell) : RenderedSpell {
    override fun cast(env: CastingEnvironment) {
        // Re-check: two raises in one hex could otherwise stack on the same anchor
        val spells = spells(env)
        val taken = when (val anchor = spell.anchor) {
            is StandingSpell.Anchor.Block -> spells.atBlock(anchor.dimension, anchor.pos) != null
            is StandingSpell.Anchor.Entity -> spells.onEntity(anchor.uuid) != null
        }
        if (!taken) spells.add(spell)
    }
}

/** Vigil Purification: position or entity → seconds of upkeep left, or null if there's no spell there. */
object OpStandingRemaining : ConstMediaAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> {
        val spell = findSpell(args, 0, argc, env) ?: return listOf(NullIota())
        return spell.secondsRemaining().asActionResult
    }
}

/** Feed Standing Spell: position or entity, dust → nothing. Adds that much of the caster's media to the spell. */
object OpFeedStanding : SpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val spell = findSpell(args, 0, argc, env)
            ?: throw MishapBadLocation(targetPos(args[0], env), "mirabilia.no_standing")
        val amount = dust(args.getPositiveDouble(1, argc))
        return SpellAction.Result(Feed(spell, amount), amount, listOf(ParticleSpray.burst(targetPos(args[0], env), 0.5)))
    }

    private data class Feed(val spell: StandingSpell, val amount: Long) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            spell.media += amount
            spells(env).setDirty()
        }
    }
}

/** Dispel Standing Spell: position or entity → nothing. Ends the spell; its remaining media is lost. */
object OpDispelStanding : SpellAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val spell = findSpell(args, 0, argc, env)
            ?: throw MishapBadLocation(targetPos(args[0], env), "mirabilia.no_standing")
        return SpellAction.Result(Dispel(spell), MediaConstants.DUST_UNIT, listOf(ParticleSpray.burst(targetPos(args[0], env), 1.0)))
    }

    private data class Dispel(val spell: StandingSpell) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            spells(env).end(env.world, spell)
        }
    }
}

private fun targetPos(iota: Iota, env: CastingEnvironment): Vec3 = when (iota) {
    is Vec3Iota -> iota.vec3
    is EntityIota -> iota.entity.position()
    else -> env.mishapSprayPos()
}
