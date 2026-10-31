package com.mirabiliahexica.client

import java.util.UUID
import kotlin.random.Random

/**
 * How a Lost Dreamer's body came back wrong. Picked from its UUID, so every player sees the same creature and it
 * never changes: at most one change to the head and at most one to the body. About one in six is spared both.
 */
enum class DreamerMutation(val slot: Slot) {
    /** Two heads side by side where one should be. */
    TWIN_HEADS(Slot.HEAD),

    /** No head: four arms reach up from the neck instead. */
    HEAD_OF_HANDS(Slot.HEAD),

    /** A second face on the back of the head, looking the other way. */
    BACK_FACE(Slot.HEAD),

    /** A second torso stacked on the first, with the head (and another pair of arms) on top. */
    TALL_STACK(Slot.HEAD),

    /** One or two more pairs of arms down the sides of the body. */
    EXTRA_ARMS(Slot.BODY),

    /** A second body grown into its back, with its own head and arms, facing away. */
    CONJOINED(Slot.BODY),

    /** Arms hanging from the hips beside the legs. */
    ARM_LEGS(Slot.BODY),
    ;

    enum class Slot { HEAD, BODY }

    companion object {
        private val cache = HashMap<UUID, Set<DreamerMutation>>()

        fun of(uuid: UUID): Set<DreamerMutation> = cache.getOrPut(uuid) {
            val random = Random(uuid.leastSignificantBits xor uuid.mostSignificantBits)
            val head = entries.filter { it.slot == Slot.HEAD }
            val body = entries.filter { it.slot == Slot.BODY }
            val picked = mutableSetOf<DreamerMutation>()
            when (random.nextInt(6)) {
                0 -> {}
                1, 2 -> picked += head.random(random)
                3, 4 -> picked += body.random(random)
                else -> {
                    picked += head.random(random)
                    picked += body.random(random)
                }
            }
            if (cache.size > 512) cache.clear()
            picked
        }

        /** Whether EXTRA_ARMS grows two pairs rather than one. */
        fun twoExtraPairs(uuid: UUID) = (uuid.mostSignificantBits ushr 7) and 1L == 1L
    }
}
