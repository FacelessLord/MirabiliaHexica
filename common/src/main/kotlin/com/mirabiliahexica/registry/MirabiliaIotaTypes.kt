package com.mirabiliahexica.registry

import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes
import com.mirabiliahexica.casting.iota.StringIota

/**
 * Iota types this mod adds. Registered even when the string patterns are turned off in the config, so a server and
 * its clients always agree on the registry and old saves still load.
 */
object MirabiliaIotaTypes : MirabiliaRegistrar<IotaType<*>>(HexRegistries.IOTA_TYPE, { HexIotaTypes.REGISTRY }) {
    val STRING = register("string") { StringIota.TYPE }
}
