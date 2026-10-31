package com.mirabiliahexica.standing

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.MishapEnvironment
import at.petrak.hexcasting.api.pigment.FrozenPigment
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate

/**
 * Where a standing spell's hex runs. It has no hands or inventory, pays from the spell's own budget, and reaches
 * [StandingSpells.RANGE] blocks around its anchor. It acts as its owner only while riding on them; otherwise
 * (on a block, or on some other entity) it has no caster, like an unowned circle.
 */
class StandingSpellEnv(
    world: ServerLevel,
    private val spell: StandingSpell,
    /** The entity it rides on, already resolved by [StandingSpells]; null for block-bound spells. */
    private val anchorEntity: Entity?,
) : CastingEnvironment(world) {
    /** Who raised the spell, online or not. */
    val ownerId get() = spell.owner

    /** The owner, if online and in this level. */
    val owner: ServerPlayer? = world.server.playerList.getPlayer(spell.owner)?.takeIf { it.level() == world }

    fun anchorPos(): Vec3 = when (val anchor = spell.anchor) {
        is StandingSpell.Anchor.Block -> Vec3.atCenterOf(anchor.pos)
        is StandingSpell.Anchor.Entity -> anchorEntity?.position() ?: Vec3.ZERO
    }

    override fun getCastingEntity(): LivingEntity? = owner?.takeIf { anchorEntity === it }

    override fun getMishapEnvironment(): MishapEnvironment = NoOpMishapEnv(world)

    override fun mishapSprayPos(): Vec3 = anchorPos()

    /** Returns the cost left unpaid, as Hex expects. */
    override fun extractMediaEnvironment(cost: Long, simulate: Boolean): Long {
        val taken = minOf(cost, spell.media)
        if (!simulate) spell.media -= taken
        return cost - taken
    }

    override fun isVecInRangeEnvironment(vec: Vec3) =
        vec.distanceToSqr(anchorPos()) <= StandingSpells.RANGE * StandingSpells.RANGE

    override fun hasEditPermissionsAtEnvironment(pos: BlockPos): Boolean {
        val player = owner ?: return true
        return world.mayInteract(player, pos)
    }

    override fun getCastingHand() = InteractionHand.MAIN_HAND

    override fun getUsableStacks(mode: StackDiscoveryMode): List<ItemStack> = emptyList()

    override fun getPrimaryStacks(): List<HeldItemInfo> = emptyList()

    override fun replaceItem(stackOk: Predicate<ItemStack>, replaceWith: ItemStack, hand: InteractionHand?) = false

    override fun getPigment(): FrozenPigment = FrozenPigment.DEFAULT.get()

    override fun setPigment(pigment: FrozenPigment?): FrozenPigment? = null

    override fun produceParticles(particles: ParticleSpray, pigment: FrozenPigment) {
        particles.sprayParticles(world, pigment)
    }

    override fun printMessage(message: Component) {
        owner?.sendSystemMessage(message)
    }

    /** A standing spell's mishap just ends it (see [StandingSpells]); nobody is there to be hurt. */
    private class NoOpMishapEnv(world: ServerLevel) : MishapEnvironment(world, null) {
        override fun yeetHeldItemsTowards(targetPos: Vec3) {}
        override fun dropHeldItems() {}
        override fun drown() {}
        override fun damage(healthProportion: Float) {}
        override fun removeXp(amount: Int) {}
        override fun blind(ticks: Int) {}
    }
}
