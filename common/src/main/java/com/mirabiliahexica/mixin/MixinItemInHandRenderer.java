package com.mirabiliahexica.mixin;

import com.mirabiliahexica.client.ImbuedGlyphLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The ring of imbued glyphs round a held tool, in your own first-person view. */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {
    // Just before the item itself is drawn, when the pose is at the hand
    @Inject(method = "renderArmWithItem", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void mirabilia$glyphs(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing,
                                  ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ImbuedGlyphLayer.Companion.renderFirstPerson(pose, buffers, player, stack, hand, partialTick);
    }
}
