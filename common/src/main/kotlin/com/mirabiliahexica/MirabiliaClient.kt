package com.mirabiliahexica

import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import com.mirabiliahexica.client.ClientNetworking
import com.mirabiliahexica.client.DreamSky
import com.mirabiliahexica.client.GlassManRenderer
import com.mirabiliahexica.client.LensLabels
import com.mirabiliahexica.config.ConfigText
import com.mirabiliahexica.client.LostDreamerRenderer
import com.mirabiliahexica.entity.LostDreamer
import com.mirabiliahexica.imbue.Imbuement
import com.mirabiliahexica.lens.LensImbuement
import com.mirabiliahexica.registry.MirabiliaEntities
import com.mirabiliahexica.registry.MirabiliaItems
import com.mirabiliahexica.source.MediaSource
import com.mirabiliahexica.source.Tether
import com.mirabiliahexica.wards.ArmorWard
import dev.architectury.event.events.client.ClientGuiEvent
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTooltipEvent
import dev.architectury.registry.client.level.entity.EntityRendererRegistry
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.network.chat.Component

/**
 * Client setup shared by both loaders. Placing lens labels needs the camera matrices from inside the world
 * render, which takes a loader-specific hook, so each loader's client entrypoint calls `LensLabels.project`.
 */
object MirabiliaClient {
    /** Entity renderers; Forge needs these hooked up before client setup, so each loader calls this early. */
    @Suppress("UNCHECKED_CAST")
    fun initRenderers() {
        EntityRendererRegistry.register({ MirabiliaEntities.LOST_DREAMER.value }) { context ->
            LostDreamerRenderer(context) as EntityRenderer<LostDreamer>
        }
        EntityRendererRegistry.register({ MirabiliaEntities.GLASS_MAN.value }, ::GlassManRenderer)
    }

    /** Called once by each loader's client entrypoint. */
    fun init() {
        ClientNetworking.init()
        ClientGuiEvent.RENDER_HUD.register { graphics, _ -> LensLabels.render(graphics) }
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register {
            LensLabels.clear()
            DreamSky.clear()
            // Back to this client's own numbers
            ConfigText.synced = null
        }
        ClientTooltipEvent.ITEM.register { stack, lines, _ ->
            if (Imbuement.isImbued(stack)) {
                // The Greater Lens says this itself
                if (!stack.`is`(MirabiliaItems.GREATER_LENS.value)) {
                    lines.add(1, Component.translatable("item.mirabilia.imbued", LensImbuement.hexSize(stack)).withStyle(ChatFormatting.LIGHT_PURPLE))
                }
                val dust = { media: Long -> "%.1f".format(java.util.Locale.ROOT, media.toDouble() / MediaConstants.DUST_UNIT) }
                lines.add(minOf(2, lines.size), Component.translatable("item.mirabilia.imbued.media", dust(Imbuement.media(stack)), dust(Imbuement.capacity(stack)))
                    .withStyle(ChatFormatting.DARK_PURPLE))
            }
            when (val tether = Tether.of(stack)) {
                is MediaSource.Block -> lines.add(1, Component.translatable("item.mirabilia.tethered.block", tether.pos.x, tether.pos.y, tether.pos.z)
                    .withStyle(ChatFormatting.DARK_AQUA))
                is MediaSource.Entity -> lines.add(1, Component.translatable("item.mirabilia.tethered.entity").withStyle(ChatFormatting.DARK_AQUA))
                else -> {}
            }
            if (ArmorWard.isWarded(stack)) {
                lines.add(1, Component.translatable("item.mirabilia.warded").withStyle(ChatFormatting.LIGHT_PURPLE))
            }
        }
    }
}
