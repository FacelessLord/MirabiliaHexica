package com.mirabiliahexica.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.mishaps.MishapEntityTooFarAway;
import com.mirabiliahexica.wards.Wards;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wards on entities. Hex's range hooks only see positions, and a player's entity iota skips range entirely by
 * default (its "true name has ambit" option), so warded armor, and stones guarding a player, need the two entity
 * checks themselves.
 */
@Mixin(value = CastingEnvironment.class, remap = false)
public abstract class MixinCastingEnvironment {
    @Inject(method = "isEntityInRange(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
    private void mirabilia$hideWarded(Entity entity, boolean ignoreTruenameAmbit, CallbackInfoReturnable<Boolean> cir) {
        if (Wards.refusesEntity((CastingEnvironment) (Object) this, entity)) cir.setReturnValue(false);
    }

    @Inject(method = "assertEntityInRange", at = @At("HEAD"))
    private void mirabilia$refuseWarded(Entity entity, CallbackInfo ci) throws MishapEntityTooFarAway {
        if (Wards.refusesEntity((CastingEnvironment) (Object) this, entity)) throw new MishapEntityTooFarAway(entity);
    }
}
