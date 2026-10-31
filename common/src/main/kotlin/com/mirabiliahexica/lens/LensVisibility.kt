package com.mirabiliahexica.lens

import com.mirabiliahexica.registry.MirabiliaEnchantments
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext

/**
 * Whether a lens label is drawn: only over what the wearer can see from their eyes, unless the lens has
 * [Deepsight][DeepsightEnchantment]. The hex's results are all kept; this is checked by the client every frame
 * while drawing (`LensLabels`), so a label appears the moment its place or creature comes into view, without
 * waiting for the hex to run again. Works on both sides.
 */
object LensVisibility {
    fun seesThroughWalls(lens: ItemStack) =
        EnchantmentHelper.getItemEnchantmentLevel(MirabiliaEnchantments.DEEPSIGHT.value, lens) > 0

    /**
     * A place is visible if nothing opaque stands between the eyes and it. See-through blocks (glass, leaves,
     * ice… anything that doesn't hide what's behind it from the renderer) never block the view; other blocks
     * block it only where their shape actually is, so a slab or fence doesn't hide the whole cube. The block
     * the place lies in doesn't count.
     */
    fun canSee(player: Player, target: Vec3): Boolean {
        val from = player.eyePosition
        val targetBlock = BlockPos.containing(target)
        val blocked: Boolean? = BlockGetter.traverseBlocks(from, target, player.level(), { level, pos ->
            when {
                pos == targetBlock -> false
                else -> {
                    val state = level.getBlockState(pos)
                    val hides = state.canOcclude() &&
                        state.getVisualShape(level, pos, CollisionContext.empty()).clip(from, target, pos) != null
                    if (hides) true else null // null: keep looking along the line
                }
            }
        }, { false })
        return blocked != true
    }

    /** An entity is visible if its eyes or the middle of its body are. */
    fun canSee(player: Player, entity: Entity): Boolean =
        entity == player || canSee(player, entity.eyePosition) || canSee(player, entity.boundingBox.center)
}
