package com.mirabiliahexica.mixin;

import com.mirabiliahexica.client.SunSignRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the sign on the Sun once vanilla has finished the sky (only reached when the sky was drawn in full, so not
 * in fog, lava or blindness). Client only.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(method = "renderSky", at = @At("TAIL"))
    private void mirabilia$drawSunSign(PoseStack poseStack, Matrix4f projection, float partialTick, Camera camera,
                                        boolean isFoggy, Runnable setupFog, CallbackInfo ci) {
        SunSignRenderer.INSTANCE.render(poseStack, partialTick);
    }
}
