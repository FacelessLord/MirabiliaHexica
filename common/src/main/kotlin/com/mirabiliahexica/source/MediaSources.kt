package com.mirabiliahexica.source

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import com.mirabiliahexica.Mirabilia
import com.mirabiliahexica.advancement.MirabiliaAdvancements
import net.minecraft.server.level.ServerPlayer
import com.mirabiliahexica.dream.DreamDanger
import com.mirabiliahexica.config.Features

/**
 * Bound media sources. Binding one makes the rest of the hex pay from it first, until it runs dry or the hex
 * ends; whatever it can't cover is paid the usual way (inventory, the standing spell's budget, the circle…).
 *
 * "The hex" is whatever keeps one [CastingImage]: the staff's grid until it's cleared, one run of a lens or
 * standing spell, a circle's whole run, one use of a casting item. The binding is kept in the image's user data,
 * which Hex carries along with it, and each casting environment gets a hook that reads it on creation (or,
 * failing that, the [Tether] of the item whose hex is running) and pays
 * from it before anything else.
 */
object MediaSources {
    private const val USER_DATA = "${Mirabilia.MODID}:media_source"

    fun init() {
        CastingEnvironment.addCreateEventListener { env, userData ->
            // The hex's own binding if it has one, else the tether of the item running it
            val source = if (userData.contains(USER_DATA)) MediaSource.load(userData.getCompound(USER_DATA))
                else Tether.castingItem(env)?.let(Tether::of)
            env.addExtension(Hook(env, source))
        }
    }

    /** The source [env] currently draws from, if any. */
    fun current(env: CastingEnvironment): MediaSource? = env.getExtension(KEY)?.source

    /** Binds [source] (or clears the binding, for null) for the rest of the hex. */
    fun bind(env: CastingEnvironment, image: CastingImage, source: MediaSource?): CastingImage {
        env.getExtension(KEY)?.source = source
        val userData = image.userData.copy()
        if (source == null) userData.remove(USER_DATA) else userData.put(USER_DATA, source.save())
        return image.copy(userData = userData)
    }

    private class Hook(private val env: CastingEnvironment, var source: MediaSource?) :
        CastingEnvironmentComponent.ExtractMedia.Pre {
        override fun getKey() = KEY

        override fun onExtractMedia(cost: Long, simulate: Boolean): Long {
            val source = source ?: return cost
            if (cost <= 0 || !Features.wellsprings) return cost
            val left = maxOf(0, cost - source.withdraw(env, cost, simulate))
            // Paid in full by the wellspring, while carrying no media at all
            if (!simulate && left == 0L) {
                (env.castingEntity as? ServerPlayer)?.takeIf { DreamDanger.carried(it) == 0L }
                    ?.let { MirabiliaAdvancements.award(it, "fully_provided") }
            }
            return left
        }
    }

    private val KEY = object : CastingEnvironmentComponent.Key<Hook> {}
}
