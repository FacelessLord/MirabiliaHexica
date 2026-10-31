package com.mirabiliahexica.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getItemEntity
import at.petrak.hexcasting.api.casting.getList
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.casting.mishaps.MishapBadItem
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.utils.extractMedia
import at.petrak.hexcasting.api.utils.isMediaItem
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.source.MediaSource
import com.mirabiliahexica.source.MediaSources
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import net.minecraft.server.level.ServerPlayer
import com.mirabiliahexica.config.Tuning

/**
 * Imbue Lens / Imbue Armor / Imbue Tool (great spells): entity, hex → nothing. Stores the hex in the lens, armor
 * piece or tool in the caster's other hand, and gives it a store of media made from the given dropped stack of amethyst (dust,
 * shards, Charged Amethyst; whatever Craft Phial would take). The whole stack is used up: its media becomes both
 * the store's size and its first filling, as when crafting a Phial. Imbuing again replaces hex and store. See
 * [Imbuement] and [com.mirabiliahexica.lens.LensImbuement] for what the items do with them.
 */
class OpImbueItem(private val kind: Kind) : SpellAction {
    enum class Kind(val fits: (ItemStack) -> Boolean, val wanted: String) {
        LENS(Imbuement::canImbueLens, "mirabilia.lens"),
        ARMOR(Imbuement::canImbueArmor, "mirabilia.imbuable_armor"),
        TOOL(Imbuement::canImbueTool, "mirabilia.imbuable_tool"),
    }

    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val source = args.getItemEntity(0, argc)
        val hex = args.getList(1, argc).toList()
        val item = env.getHeldItemToOperateOn(kind.fits)?.stack
            ?: throw MishapBadOffhandItem.of(ItemStack.EMPTY.copy(), kind.wanted)
        env.assertEntityInRange(source)
        if (!isMediaItem(source.item) || extractMedia(source.item, drainForBatteries = true, simulate = true) <= 0) {
            throw MishapBadItem.of(source, "media_for_battery")
        }
        return SpellAction.Result(Spell(kind, item, source, hex), COST, listOf(ParticleSpray.burst(source.position(), 0.5)))
    }

    private data class Spell(val kind: Kind, val item: ItemStack, val source: ItemEntity, val hex: List<Iota>) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            if (!source.isAlive) return
            val stack = source.item.copy()
            val media = extractMedia(stack, drainForBatteries = true)
            if (stack.isEmpty) source.kill() else source.item = stack
            Imbuement.imbue(item, hex, capacity = media)
            if (hex.isNotEmpty()) (env.castingEntity as? ServerPlayer)?.let {
                MirabiliaAdvancements.award(it, if (kind == Kind.LENS) "inscription" else "well_armed")
            }
        }
    }

    companion object {
        val COST get() = Tuning.Imbuing.imbueCost
    }
}

/**
 * Recharge Imbued Item: entity | null → nothing. Refills the store of the imbued armor piece or tool in the
 * caster's other hand, up to the store's size:
 *
 * * given a dropped stack of media items, from that, using up only what it needs (whole items, as Recharge Item
 *   does);
 * * given null, from the hex's bound wellspring ([MediaSources]), exactly as much as fits or it has.
 *
 * Costs a shard, like Recharge Item.
 */
object OpRechargeImbued : SpellAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val item = env.getHeldItemToOperateOn(Imbuement::isImbued)?.stack
            ?: throw MishapBadOffhandItem.of(ItemStack.EMPTY.copy(), "mirabilia.imbued_item")
        val spell = when (val from = args[0]) {
            is NullIota -> {
                val wellspring = MediaSources.current(env) ?: throw MishapInvalidIota.of(from, 0, "mirabilia.recharge_source")
                FromWellspring(item, wellspring)
            }
            else -> {
                val source = args.getItemEntity(0, argc)
                env.assertEntityInRange(source)
                if (!isMediaItem(source.item)) throw MishapBadItem.of(source, "media")
                FromStack(item, source)
            }
        }
        return SpellAction.Result(spell, MediaConstants.SHARD_UNIT, listOf(ParticleSpray.burst(env.mishapSprayPos(), 0.5)))
    }

    private fun room(item: ItemStack) = Imbuement.capacity(item) - Imbuement.media(item)

    private data class FromStack(val item: ItemStack, val source: ItemEntity) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            val room = room(item)
            if (!source.isAlive || room <= 0) return
            val stack = source.item.copy()
            val taken = extractMedia(stack, room)
            if (stack.isEmpty) source.kill() else source.item = stack
            Imbuement.setMedia(item, Imbuement.media(item) + taken)
        }
    }

    private data class FromWellspring(val item: ItemStack, val wellspring: MediaSource) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            val room = room(item)
            if (room <= 0) return
            Imbuement.setMedia(item, Imbuement.media(item) + wellspring.withdraw(env, room, false))
        }
    }
}
