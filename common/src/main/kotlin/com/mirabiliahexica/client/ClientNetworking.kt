package com.mirabiliahexica.client

import com.mirabiliahexica.network.MirabiliaNetworking
import dev.architectury.networking.NetworkManager
import com.mirabiliahexica.config.ConfigText
import com.mirabiliahexica.config.Features
import vazkii.patchouli.client.book.ClientBookRegistry

/** Client-side receivers for [MirabiliaNetworking]'s packets. */
object ClientNetworking {
    fun init() {
        NetworkManager.registerReceiver(NetworkManager.s2c(), MirabiliaNetworking.LENS_LABELS) { buf, context ->
            val throughWalls = buf.readBoolean()
            val labels = buf.readList(MirabiliaNetworking::readLabel)
            context.queue { LensLabels.replace(labels, throughWalls) }
        }
        NetworkManager.registerReceiver(NetworkManager.s2c(), MirabiliaNetworking.FEATURES) { buf, context ->
            val features = buf.readMap({ it.readUtf() }, { it.readBoolean() })
            val numbers = buf.readMap({ it.readUtf() }, { it.readDouble() })
            context.queue {
                Features.applyFromServer(features)
                ConfigText.synced = numbers
                // The book decides what it shows when it loads, so it loads again with the server's switches
                ClientBookRegistry.INSTANCE.reload()
            }
        }
    }
}
