@file:JvmName("MirabiliaAbstractions")

package com.mirabiliahexica

import com.mirabiliahexica.registry.MirabiliaRegistrar
import dev.architectury.injectables.annotations.ExpectPlatform

fun initRegistries(vararg registries: MirabiliaRegistrar<*>) {
    for (registry in registries) {
        initRegistry(registry)
    }
}

/** Implemented in `fabric/MirabiliaAbstractionsImpl.kt` and `forge/MirabiliaAbstractionsImpl.kt`. */
@ExpectPlatform
fun <T : Any> initRegistry(registrar: MirabiliaRegistrar<T>) {
    throw AssertionError()
}
