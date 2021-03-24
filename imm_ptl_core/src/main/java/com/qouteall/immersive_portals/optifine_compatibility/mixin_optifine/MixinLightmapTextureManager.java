package com.qouteall.immersive_portals.optifine_compatibility.mixin_optifine;

import com.qouteall.immersive_portals.ClientWorldLoader;
import com.qouteall.immersive_portals.render.context_management.RenderDimensionRedirect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(LightmapTextureManager.class)
public class MixinLightmapTextureManager {
    @Redirect(
        method = "update",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;"
        )
    )
    ClientWorld redirectWorldInUpdate(MinecraftClient client) {
        return ClientWorldLoader.getWorld(RenderDimensionRedirect.getRedirectedDimension(
            client.world.getRegistryKey()
        ));
    }
}
