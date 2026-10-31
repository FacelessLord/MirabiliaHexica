@file:JvmName("MirabiliaAbstractionsImpl")

package com.mirabiliahexica.fabric

import com.mirabiliahexica.registry.MirabiliaRegistrar
import net.minecraft.core.Registry

fun <T : Any> initRegistry(registrar: MirabiliaRegistrar<T>) {
    val registry = registrar.registry
    registrar.init { id, value -> Registry.register(registry, id, value) }
}
