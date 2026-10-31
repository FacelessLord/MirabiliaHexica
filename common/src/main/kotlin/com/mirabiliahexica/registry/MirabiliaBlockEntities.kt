package com.mirabiliahexica.registry

import com.mirabiliahexica.machines.AmbientCollectorBlockEntity
import com.mirabiliahexica.machines.CrystallizerBlockEntity
import com.mirabiliahexica.machines.SolarCollectorBlockEntity
import com.mirabiliahexica.wards.WardStoneBlockEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType

object MirabiliaBlockEntities : MirabiliaRegistrar<BlockEntityType<*>>(
    Registries.BLOCK_ENTITY_TYPE,
    { BuiltInRegistries.BLOCK_ENTITY_TYPE },
) {
    val WARD_STONE = register("ward_stone") {
        BlockEntityType.Builder.of(
            ::WardStoneBlockEntity,
            MirabiliaBlocks.WARDING_STONE.value,
            MirabiliaBlocks.SILENCE_STONE.value,
        ).build(null)
    }
    val AMBIENT_COLLECTOR = register("ambient_collector") {
        BlockEntityType.Builder.of(::AmbientCollectorBlockEntity, MirabiliaBlocks.AMBIENT_COLLECTOR.value).build(null)
    }
    val SOLAR_COLLECTOR = register("solar_collector") {
        BlockEntityType.Builder.of(::SolarCollectorBlockEntity, MirabiliaBlocks.SOLAR_COLLECTOR.value).build(null)
    }
    val CRYSTALLIZER = register("crystallizer") {
        BlockEntityType.Builder.of(::CrystallizerBlockEntity, MirabiliaBlocks.CRYSTALLIZER.value).build(null)
    }
}
