@file:JvmName("MirabiliaAbstractionsImpl")

package com.mirabiliahexica.forge

import com.mirabiliahexica.registry.MirabiliaRegistrar
import net.minecraftforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.forge.MOD_BUS

fun <T : Any> initRegistry(registrar: MirabiliaRegistrar<T>) {
    MOD_BUS.addListener { event: RegisterEvent ->
        event.register(registrar.registryKey) { helper ->
            registrar.init(helper::register)
        }
    }
}
