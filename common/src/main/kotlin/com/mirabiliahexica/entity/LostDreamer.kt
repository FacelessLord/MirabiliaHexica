package com.mirabiliahexica.entity

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.monster.ZombieVillager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

/**
 * A villager who dreamed too long in Tanako's World and came back wrong: hostile, stretched out of true, and
 * prone to skipping a few blocks through space while it hunts. It is a kind of zombie villager underneath, but
 * past curing: a golden apple does nothing (otherwise the dream would be an endless free source of villagers).
 *
 * Spawned only by [com.mirabiliahexica.dream.DreamSpawner]. Unlike zombies it never calls in reinforcements,
 * carries no tools or armour, doesn't burn and doesn't drown into anything else.
 */
class LostDreamer(type: EntityType<out ZombieVillager>, level: Level) : ZombieVillager(type, level) {
    override fun isSunSensitive() = false

    override fun convertsInWater() = false

    // Zombies otherwise roll a chance to summon plain zombies when hurt
    override fun randomizeReinforcementsChance() {
        getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE)?.baseValue = 0.0
    }

    override fun populateDefaultEquipmentSlots(random: RandomSource, difficulty: DifficultyInstance) {}

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (player.getItemInHand(hand).`is`(Items.GOLDEN_APPLE)) return InteractionResult.PASS
        return super.mobInteract(player, hand)
    }

    override fun aiStep() {
        super.aiStep()
        val level = level()
        if (level is ServerLevel && target != null && isAlive && random.nextInt(BLINK_ODDS) == 0) blink(level)
    }

    /** A short, sudden skip to a nearby spot, leaving a puff of the dream behind. */
    private fun blink(level: ServerLevel) {
        val fromX = x
        val fromY = y
        val fromZ = z
        val x = x + (random.nextDouble() - 0.5) * 2 * BLINK_RANGE
        val z = z + (random.nextDouble() - 0.5) * 2 * BLINK_RANGE
        if (randomTeleport(x, y + random.nextInt(3) - 1, z, false)) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, fromX, fromY + 1, fromZ, 16, 0.3, 0.6, 0.3, 0.02)
        }
    }

    companion object {
        private const val BLINK_ODDS = 90
        private const val BLINK_RANGE = 4.0

        fun createAttributes(): AttributeSupplier.Builder = Zombie.createAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.24)
            .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0)
    }
}
