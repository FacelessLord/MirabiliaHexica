package com.mirabiliahexica.mixin;

import com.mirabiliahexica.config.ConfigText;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Writes the config's numbers into translations that name them (`#{...}`, see ConfigText): book pages, advancements. */
@Mixin(ClientLanguage.class)
public abstract class MixinClientLanguage {
    @Inject(method = "getOrDefault", at = @At("RETURN"), cancellable = true)
    private void mirabilia$configValues(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String text = cir.getReturnValue();
        if (text != null && text.contains("#{")) cir.setReturnValue(ConfigText.INSTANCE.fill(text));
    }
}
