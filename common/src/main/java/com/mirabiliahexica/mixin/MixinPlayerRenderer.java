package com.mirabiliahexica.mixin;

import com.mirabiliahexica.client.ImbuedGlyphLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives players the layer that draws imbued glyphs on their armor, lenses and held tools. */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private MixinPlayerRenderer(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mirabilia$glyphs(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        addLayer(new ImbuedGlyphLayer(this));
    }
}
