package com.mirabiliahexica.mixin;

import com.mirabiliahexica.ambient.AmbientMedia;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Budding amethyst breathes a little media into the air with every growth tick (see AmbientMedia). */
@Mixin(BuddingAmethystBlock.class)
public abstract class MixinBuddingAmethystBlock {
    @Inject(method = "randomTick", at = @At("HEAD"))
    private void mirabilia$breathe(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        AmbientMedia.INSTANCE.add(level, pos, AmbientMedia.INSTANCE.getBUDDING_TICK());
    }
}
