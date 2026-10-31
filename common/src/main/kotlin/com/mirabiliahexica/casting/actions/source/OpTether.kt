package com.mirabiliahexica.casting.actions.source

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadBlock
import at.petrak.hexcasting.api.casting.mishaps.MishapBadEntity
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import com.mirabiliahexica.source.MediaSource
import com.mirabiliahexica.source.Tether
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import net.minecraft.server.level.ServerPlayer

/**
 * Wellspring Tether: vector | entity | null → nothing. Tethers the hex-running item in the caster's other hand
 * (a lens, imbued armor or tool, cypher, trinket or artifact) to a container, dropped stack or chest minecart,
 * so its hexes draw from that first whenever it is in reach ([Tether]). Null cuts the tether. Free.
 */
object OpTether : SpellAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val item = env.getHeldItemToOperateOn(Tether::canTether)?.stack
            ?: throw MishapBadOffhandItem.of(ItemStack.EMPTY.copy(), "mirabilia.tetherable")
        val source = when (val iota = args[0]) {
            is NullIota -> null
            is Vec3Iota -> {
                val pos = BlockPos.containing(iota.vec3)
                env.assertPosInRange(pos)
                if (!MediaSource.canHoldAt(env.world, pos)) throw MishapBadBlock.of(pos, "mirabilia.media_container")
                MediaSource.Block(pos)
            }
            is EntityIota -> {
                env.assertEntityInRange(iota.entity)
                if (!MediaSource.canHold(iota.entity)) throw MishapBadEntity.of(iota.entity, "mirabilia.media_source")
                MediaSource.Entity(iota.entity.uuid)
            }
            else -> throw MishapInvalidIota.of(iota, 0, "mirabilia.vector_entity_or_null")
        }
        return SpellAction.Result(Spell(item, source), 0, listOf(ParticleSpray.burst(env.mishapSprayPos(), 0.5)))
    }

    private data class Spell(val item: ItemStack, val source: MediaSource?) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            Tether.set(item, source)
            if (source != null) (env.castingEntity as? ServerPlayer)?.let { MirabiliaAdvancements.award(it, "supply_chain") }
        }
    }
}
