package com.qouteall.hiding_in_the_bushes;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.SodiumInterface;
import com.qouteall.immersive_portals.portal.custom_portal_gen.PortalGenInfo;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.loader.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public class O_O {
    
    public static boolean isReachEntityAttributesPresent;
    
    public static boolean isForge() {
        return false;
    }
    
    @Environment(EnvType.CLIENT)
    public static void onPlayerChangeDimensionClient(
        RegistryKey<World> from, RegistryKey<World> to
    ) {
        RequiemCompat.onPlayerTeleportedClient();
    }
    
    @Environment(EnvType.CLIENT)
    public static void segregateClientEntity(
        ClientWorld fromWorld,
        Entity entity
    ) {
        ((IEClientWorld_MA) fromWorld).segregateEntity(entity);
        entity.removed = false;
    }
    
    public static void segregateServerEntity(
        ServerWorld fromWorld,
        Entity entity
    ) {
        fromWorld.removeEntity(entity);
        entity.removed = false;
    }
    
    public static void segregateServerPlayer(
        ServerWorld fromWorld,
        ServerPlayerEntity player
    ) {
        fromWorld.removePlayer(player);
        player.removed = false;
    }
    
    public static void onPlayerTravelOnServer(
        ServerPlayerEntity player,
        RegistryKey<World> from,
        RegistryKey<World> to
    ) {
        RequiemCompat.onPlayerTeleportedServer(player);
    }
    
    public static void loadConfigFabric() {
        MyConfig myConfig = MyConfig.readConfig();
        myConfig.onConfigChanged();
        myConfig.saveConfigFile();
    }
    
    public static void onServerConstructed() {
        // forge version initialize server config
    }
    
    private static final BlockState obsidianState = Blocks.OBSIDIAN.getDefaultState();
    
    public static boolean isObsidian(BlockState blockState) {
        return blockState == obsidianState;
    }
    
    public static boolean detectOptiFine() {
        boolean isOptiFabricPresent = FabricLoader.INSTANCE.isModLoaded("optifabric");
        
        if (!isOptiFabricPresent) {
            return false;
        }
        
        try {
            //do not load other optifine classes that loads vanilla classes
            //that would load the class before mixin
            Class.forName("optifine.ZipResourceProvider");
            return true;
        }
        catch (ClassNotFoundException e) {
            Helper.err("OptiFabric is present but OptiFine is not present!!!");
            return false;
        }
    }
    
    public static void postClientChunkLoadEvent(WorldChunk chunk) {
        ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(
            ((ClientWorld) chunk.getWorld()), chunk
        );
    }
    
    public static void postClientChunkUnloadEvent(WorldChunk chunk) {
        ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(
            ((ClientWorld) chunk.getWorld()), chunk
        );
    }
    
    public static boolean isDedicatedServer() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }
    
    public static void postPortalSpawnEventForge(PortalGenInfo info) {
    
    }
    
    @Environment(EnvType.CLIENT)
    public static ClientChunkManager createMyClientChunkManager(ClientWorld world, int loadDistance) {
        return SodiumInterface.createClientChunkManager.apply(world, loadDistance);
    }
    
    public static boolean getIsPehkuiPresent() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("pehkui");
    }
}
