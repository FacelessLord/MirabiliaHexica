package com.mirabiliahexica.network

import com.mirabiliahexica.Mirabilia
import dev.architectury.networking.NetworkManager
import dev.architectury.platform.Platform
import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import com.mirabiliahexica.config.ConfigText
import com.mirabiliahexica.config.Features

/** Server → client packets. The server decides what the labels are; the client only draws (and hides). */
object MirabiliaNetworking {
    /**
     * Labels from the worn imbued lens, replacing any previous ones (empty list: clear), and whether that lens
     * has Deepsight (otherwise the client hides labels the wearer can't see).
     */
    val LENS_LABELS = Mirabilia.id("lens_labels")

    /** The server's feature switches, sent on joining, so the client's book matches the server. */
    val FEATURES = Mirabilia.id("features")

    fun sendFeatures(player: ServerPlayer) {
        if (!canReceive(player, FEATURES)) return
        val buf = FriendlyByteBuf(Unpooled.buffer())
        buf.writeMap(Features.snapshot(), { b, k -> b.writeUtf(k) }, { b, v -> b.writeBoolean(v) })
        // And the numbers its texts quote
        buf.writeMap(ConfigText.values(), { b, k -> b.writeUtf(k) }, { b, v -> b.writeDouble(v) })
        NetworkManager.sendToPlayer(player, FEATURES, buf)
    }

    /** A lens label of one or more lines, pinned to a fixed position or following an entity (by its network id). */
    data class Label(val anchor: Anchor, val lines: List<Component>) {
        sealed interface Anchor {
            data class Fixed(val pos: Vec3) : Anchor
            data class Following(val entityId: Int) : Anchor
        }
    }

    fun sendLensLabels(player: ServerPlayer, labels: List<Label>, throughWalls: Boolean) {
        if (!canReceive(player, LENS_LABELS)) return
        val buf = FriendlyByteBuf(Unpooled.buffer())
        buf.writeBoolean(throughWalls)
        buf.writeCollection(labels) { b, label -> writeLabel(b, label) }
        NetworkManager.sendToPlayer(player, LENS_LABELS, buf)
    }

    /**
     * Skip players whose client can't take the packet. Only meaningful on Fabric: Forge makes every client have
     * the mod anyway, and Architectury's check there (a login handshake) wrongly says no even in singleplayer.
     * On Fabric it also filters out GameTest mock players, which have no real connection.
     */
    private fun canReceive(player: ServerPlayer, id: net.minecraft.resources.ResourceLocation) =
        !Platform.isFabric() || NetworkManager.canPlayerReceive(player, id)

    private fun writeLabel(buf: FriendlyByteBuf, label: Label) {
        when (val anchor = label.anchor) {
            is Label.Anchor.Fixed -> {
                buf.writeBoolean(false)
                buf.writeDouble(anchor.pos.x)
                buf.writeDouble(anchor.pos.y)
                buf.writeDouble(anchor.pos.z)
            }
            is Label.Anchor.Following -> {
                buf.writeBoolean(true)
                buf.writeVarInt(anchor.entityId)
            }
        }
        buf.writeCollection(label.lines) { b, line -> b.writeComponent(line) }
    }

    fun readLabel(buf: FriendlyByteBuf): Label {
        val anchor = if (buf.readBoolean()) {
            Label.Anchor.Following(buf.readVarInt())
        } else {
            Label.Anchor.Fixed(Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()))
        }
        return Label(anchor, buf.readList { it.readComponent() })
    }
}
