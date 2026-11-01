package com.mirabiliahexica.mixin;

import com.mirabiliahexica.dream.Possession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a villager's possession (and Hex's flayed mark) in its own save data, which carrying mods keep (see Possession). */
@Mixin(Villager.class)
public abstract class MixinVillager {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mirabilia$saveMarks(CompoundTag tag, CallbackInfo ci) {
        Possession.INSTANCE.saveMarks((Villager) (Object) this, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mirabilia$restoreMarks(CompoundTag tag, CallbackInfo ci) {
        Possession.INSTANCE.restoreMarks((Villager) (Object) this, tag);
    }
}
