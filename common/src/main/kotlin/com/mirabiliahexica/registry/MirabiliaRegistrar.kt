package com.mirabiliahexica.registry

import com.mirabiliahexica.Mirabilia
import dev.architectury.platform.Platform
import net.fabricmc.api.EnvType
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

typealias RegistrarEntry<T> = MirabiliaRegistrar<T>.Entry<out T>

/**
 * Collects entries for one registry in common code; each loader decides when to actually register them
 * (immediately on Fabric, during `RegisterEvent` on Forge). Adapted from the HexDummy addon template.
 */
abstract class MirabiliaRegistrar<T : Any>(
    val registryKey: ResourceKey<Registry<T>>,
    getRegistry: () -> Registry<T>,
) {
    /** Do not access until the mod has been initialized! */
    val registry by lazy(getRegistry)

    private var isInitialized = false

    private val mutableEntries = mutableSetOf<Entry<out T>>()
    val entries: Set<Entry<out T>> = mutableEntries

    open fun init(registerer: (ResourceLocation, T) -> Unit) {
        check(!isInitialized) { "$this has already been initialized!" }
        isInitialized = true
        for (entry in entries) {
            registerer(entry.id, entry.value)
        }
        Mirabilia.LOGGER.info("Registered {} entries in {}", entries.size, registryKey.location())
        if (Platform.getEnv() == EnvType.CLIENT) {
            initClient()
        }
    }

    open fun initClient() {}

    fun <V : T> register(name: String, builder: () -> V): Entry<V> = register(Mirabilia.id(name), builder)

    fun <V : T> register(id: ResourceLocation, builder: () -> V): Entry<V> = register(id, lazy {
        check(isInitialized) { "$this has not been initialized!" }
        builder()
    })

    fun <V : T> register(id: ResourceLocation, lazyValue: Lazy<V>): Entry<V> = Entry(id, lazyValue).also {
        require(mutableEntries.add(it)) { "Duplicate id: $id" }
    }

    open inner class Entry<V : T>(
        val id: ResourceLocation,
        private val lazyValue: Lazy<V>,
    ) {
        val key: ResourceKey<T> = ResourceKey.create(registryKey, id)

        /** Do not access until the mod has been initialized! */
        val value by lazyValue

        override fun equals(other: Any?) = when (other) {
            is MirabiliaRegistrar<*>.Entry<*> -> key.registry() == other.key.registry() && id == other.id
            else -> false
        }

        override fun hashCode() = 31 * key.registry().hashCode() + id.hashCode()
    }
}
