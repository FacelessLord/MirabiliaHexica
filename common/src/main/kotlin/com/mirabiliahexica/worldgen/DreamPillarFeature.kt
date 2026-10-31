package com.mirabiliahexica.worldgen

import com.mirabiliahexica.registry.MirabiliaBlocks
import com.mirabiliahexica.registry.MirabiliaTags
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * The rock pillars of Tanako's World. No two alike: each picks its own height, thickness, taper, lean and
 * banding, its surface ripples, and some are broken stumps or lone boulders instead. The base is sunk a few
 * blocks into the ground so leaning pillars stay anchored.
 *
 * Features may only write within the chunk being decorated and its neighbours, so the lean is limited to keep
 * the whole pillar within [MAX_REACH] blocks of where it starts.
 */
class DreamPillarFeature : Feature<NoneFeatureConfiguration>(NoneFeatureConfiguration.CODEC) {
    override fun place(context: FeaturePlaceContext<NoneFeatureConfiguration>): Boolean {
        val level = context.level()
        val random = context.random()
        val origin = context.origin()
        if (!level.getBlockState(origin.below()).`is`(MirabiliaTags.DREAM_SHAPEABLE)) return false
        if (nearBuilding(level, origin)) return false
        if (random.nextFloat() < BOULDER_CHANCE) {
            boulder(level, random, origin)
            return true
        }

        val broken = random.nextFloat() < BROKEN_CHANCE
        val height = if (broken) 3 + random.nextInt(5) else 8 + random.nextInt(22)
        val baseRadius = 0.9f + random.nextFloat() * 2.3f
        // Broken stumps end jagged rather than tapering away
        val topRadius = if (broken) baseRadius * 0.9f else baseRadius * (0.3f + random.nextFloat() * 0.5f)
        val maxLean = minOf(0.45f, (MAX_REACH - baseRadius) / height)
        val lean = if (random.nextFloat() < 0.3f) 0f else random.nextFloat() * maxLean
        val leanAngle = random.nextFloat() * Mth.TWO_PI
        val leanX = cos(leanAngle) * lean
        val leanZ = sin(leanAngle) * lean
        val bandEvery = 3 + random.nextInt(5)
        val bandPhase = random.nextInt(bandEvery)
        val ripplePhase = random.nextFloat() * Mth.TWO_PI
        val rippleSize = 0.08f + random.nextFloat() * 0.18f
        // Some flare out into a capital at the top
        val capital = !broken && random.nextFloat() < 0.25f

        for (i in -SINK until height) {
            val t = maxOf(i, 0).toFloat() / height
            var radius = Mth.lerp(t, baseRadius, topRadius) * (1 + rippleSize * sin(i * 0.8f + ripplePhase))
            if (capital && i >= height - 2) radius = baseRadius * 1.1f
            if (broken && i == height - 1) radius *= 0.6f + random.nextFloat() * 0.4f
            val cx = origin.x + 0.5f + leanX * maxOf(i, 0)
            val cz = origin.z + 0.5f + leanZ * maxOf(i, 0)
            val band = (i + bandPhase) % bandEvery == 0
            val state = when {
                band -> MirabiliaBlocks.DEEP_DREAM_GLASS.value.defaultBlockState()
                random.nextFloat() < 0.04f -> MirabiliaBlocks.DUSKY_DREAM_GLASS.value.defaultBlockState()
                else -> MirabiliaBlocks.MARBLE.value.defaultBlockState()
            }
            disc(level, cx, origin.y + i, cz, radius, state, ragged = broken && i == height - 1, random = random)
        }
        return true
    }

    /** Castles, shrines and listening posts are built before pillars grow; pillars keep clear of them. */
    private fun nearBuilding(level: WorldGenLevel, origin: BlockPos) =
        BlockPos.betweenClosedStream(origin.offset(-CLEARANCE, -1, -CLEARANCE), origin.offset(CLEARANCE, 4, CLEARANCE))
            .anyMatch { level.getBlockState(it).`is`(MirabiliaTags.DREAM_BUILT) }

    private fun boulder(level: WorldGenLevel, random: RandomSource, origin: BlockPos) {
        val radius = 1.2f + random.nextFloat() * 1.4f
        val cy = origin.y + radius * 0.3f
        val r = ceil(radius).toInt()
        for (dy in -r..r) {
            val slice = radius * radius - dy * dy
            if (slice <= 0) continue
            disc(level, origin.x + 0.5f, Mth.floor(cy) + dy, origin.z + 0.5f, Mth.sqrt(slice), MirabiliaBlocks.MARBLE.value.defaultBlockState())
        }
    }

    /** Fills a horizontal disc, replacing only air and the dream's own ground. */
    private fun disc(
        level: WorldGenLevel, cx: Float, y: Int, cz: Float, radius: Float, state: BlockState,
        ragged: Boolean = false, random: RandomSource? = null,
    ) {
        val r = ceil(radius).toInt()
        val pos = BlockPos.MutableBlockPos()
        for (x in Mth.floor(cx) - r..Mth.floor(cx) + r) {
            for (z in Mth.floor(cz) - r..Mth.floor(cz) + r) {
                val dx = x + 0.5f - cx
                val dz = z + 0.5f - cz
                if (dx * dx + dz * dz > radius * radius) continue
                if (ragged && random != null && random.nextFloat() < 0.4f) continue
                pos.set(x, y, z)
                val current = level.getBlockState(pos)
                if (current.isAir || (current.`is`(MirabiliaTags.DREAM_SHAPEABLE) && !current.`is`(MirabiliaTags.DREAM_BUILT))) {
                    level.setBlock(pos, state, 2)
                }
            }
        }
    }

    private companion object {
        const val SINK = 3
        const val CLEARANCE = 7
        const val MAX_REACH = 12f
        const val BOULDER_CHANCE = 0.12f
        const val BROKEN_CHANCE = 0.2f
    }
}
