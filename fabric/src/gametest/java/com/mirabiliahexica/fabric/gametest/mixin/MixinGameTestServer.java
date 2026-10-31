package com.mirabiliahexica.fabric.gametest.mixin;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The GameTest server builds its worlds from an empty dimension list, so datapack dimensions (Tanako's World)
 * never load. This adds ours, decoded from the mod's own dimension file, the way a real world would. Tests only.
 */
@Mixin(GameTestServer.class)
public abstract class MixinGameTestServer {
    @Unique
    private static RegistryAccess.Frozen mirabilia$worldgen;

    // The lambda inside create() that sets up the world; Mojang's mappings leave it unnamed
    @Inject(method = "method_40377", at = @At("HEAD"))
    private static void mirabilia$keepWorldgen(LevelSettings settings, WorldLoader.DataLoadContext context, CallbackInfoReturnable<?> cir) {
        mirabilia$worldgen = context.datapackWorldgen();
    }

    @ModifyArg(
        method = "method_40377",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;")
    )
    private static Registry<LevelStem> mirabilia$addDream(Registry<LevelStem> empty) {
        var id = new ResourceLocation("mirabilia", "tanakos_world");
        var path = "/data/mirabilia/dimension/tanakos_world.json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(MixinGameTestServer.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            var stem = LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, mirabilia$worldgen), JsonParser.parseReader(reader))
                .getOrThrow(false, error -> {});
            var registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
            registry.register(ResourceKey.create(Registries.LEVEL_STEM, id), stem, Lifecycle.stable());
            return registry.freeze();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't add " + id + " to the GameTest server", e);
        }
    }
}
