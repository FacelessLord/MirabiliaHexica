package com.mirabiliahexica.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The Glass Man's render type: vanilla's translucent entity, but writing colour only, not depth. Entities are drawn
 * before water, so a translucent body that wrote depth would hide the water behind it and seem to cut a hole in it.
 * (A subclass only to reach RenderType's protected state pieces.)
 */
public abstract class GlassRenderType extends RenderType {
    private GlassRenderType() {
        super("", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
    }

    public static RenderType glass(ResourceLocation texture) {
        return create("mirabilia_glass", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(true));
    }
}
