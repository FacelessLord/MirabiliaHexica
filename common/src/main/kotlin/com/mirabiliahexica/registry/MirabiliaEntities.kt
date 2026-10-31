package com.mirabiliahexica.registry

import com.mirabiliahexica.entity.GlassMan
import com.mirabiliahexica.entity.LostDreamer
import dev.architectury.registry.level.entity.EntityAttributeRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object MirabiliaEntities : MirabiliaRegistrar<EntityType<*>>(Registries.ENTITY_TYPE, { BuiltInRegistries.ENTITY_TYPE }) {
    val LOST_DREAMER = register<EntityType<LostDreamer>>("lost_dreamer") {
        EntityType.Builder.of(::LostDreamer, MobCategory.MONSTER)
            .sized(0.6f, 1.95f)
            .clientTrackingRange(8)
            .build("lost_dreamer")
    }

    val GLASS_MAN = register<EntityType<GlassMan>>("glass_man") {
        EntityType.Builder.of(::GlassMan, MobCategory.MONSTER)
            .sized(0.6f, 2.1f)
            .fireImmune()
            .clientTrackingRange(10)
            .build("glass_man")
    }

    fun initAttributes() {
        EntityAttributeRegistry.register({ LOST_DREAMER.value }, LostDreamer::createAttributes)
        EntityAttributeRegistry.register({ GLASS_MAN.value }, GlassMan::createAttributes)
    }
}
