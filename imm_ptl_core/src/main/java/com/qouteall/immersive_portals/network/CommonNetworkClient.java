package com.qouteall.immersive_portals.network;

import com.qouteall.immersive_portals.CHelper;
import com.qouteall.immersive_portals.ClientWorldLoader;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.ducks.IEClientPlayNetworkHandler;
import com.qouteall.immersive_portals.ducks.IEClientWorld;
import com.qouteall.immersive_portals.ducks.IEMinecraftClient;
import com.qouteall.immersive_portals.ducks.IEParticleManager;
import com.qouteall.immersive_portals.my_util.LimitedLogger;
import com.qouteall.immersive_portals.my_util.SignalArged;
import com.qouteall.immersive_portals.portal.Portal;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Packet;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public class CommonNetworkClient {
    
    public static final SignalArged<Portal> clientPortalSpawnSignal = new SignalArged<>();
    
    public static final MinecraftClient client = MinecraftClient.getInstance();
    private static final LimitedLogger limitedLogger = new LimitedLogger(100);
    
    public static boolean isProcessingRedirectedMessage = false;
    
    
    public static void processRedirectedPacket(RegistryKey<World> dimension, Packet packet) {
        Runnable func = () -> {
            try {
                client.getProfiler().push("process_redirected_packet");
                
                ClientWorld packetWorld = ClientWorldLoader.getWorld(dimension);
                
                doProcessRedirectedMessage(packetWorld, packet);
            }
            finally {
                client.getProfiler().pop();
            }
        };
        
        CHelper.executeOnRenderThread(func);
    }
    
    
    public static void doProcessRedirectedMessage(
        ClientWorld packetWorld,
        Packet packet
    ) {
        boolean oldIsProcessing = isProcessingRedirectedMessage;
        
        isProcessingRedirectedMessage = true;
        
        ClientPlayNetworkHandler netHandler = ((IEClientWorld) packetWorld).getNetHandler();
        
        if ((netHandler).getWorld() != packetWorld) {
            ((IEClientPlayNetworkHandler) netHandler).setWorld(packetWorld);
            Helper.err("The world field of client net handler is wrong");
        }
        
        client.getProfiler().push(() -> {
            return "handle_redirected_packet" + packetWorld.getRegistryKey() + packet;
        });
        
        try {
            withSwitchedWorld(packetWorld, () -> packet.apply(netHandler));
        }
        catch (Throwable e) {
            limitedLogger.throwException(() -> new IllegalStateException(
                "handling packet in " + packetWorld.getRegistryKey(), e
            ));
        }
        finally {
            client.getProfiler().pop();
            
            isProcessingRedirectedMessage = oldIsProcessing;
        }
    }
    
    
    public static void withSwitchedWorld(ClientWorld newWorld, Runnable runnable) {
        Validate.isTrue(client.isOnThread());
        
        ClientWorld originalWorld = client.world;
        WorldRenderer originalWorldRenderer = client.worldRenderer;
        
        WorldRenderer newWorldRenderer = ClientWorldLoader.getWorldRenderer(newWorld.getRegistryKey());
        
        Validate.notNull(newWorldRenderer);
        
        client.world = newWorld;
        ((IEParticleManager) client.particleManager).mySetWorld(newWorld);
        ((IEMinecraftClient) client).setWorldRenderer(newWorldRenderer);
        
        try {
            runnable.run();
        }
        finally {
            if (client.world != newWorld) {
                Helper.err("Respawn packet should not be redirected");
                originalWorld = client.world;
                originalWorldRenderer = client.worldRenderer;
                throw new RuntimeException("Respawn packet should not be redirected");
            }
            
            client.world = originalWorld;
            ((IEMinecraftClient) client).setWorldRenderer(originalWorldRenderer);
            ((IEParticleManager) client.particleManager).mySetWorld(originalWorld);
        }
    }
    
    public static void processEntitySpawn(String entityTypeString, int entityId, RegistryKey<World> dim, CompoundTag compoundTag) {
        Optional<EntityType<?>> entityType = EntityType.get(entityTypeString);
        if (!entityType.isPresent()) {
            Helper.err("unknown entity type " + entityTypeString);
            return;
        }
        
        CHelper.executeOnRenderThread(() -> {
            client.getProfiler().push("ip_spawn_entity");
            
            ClientWorld world = ClientWorldLoader.getWorld(dim);
            
            Entity entity = entityType.get().create(
                world
            );
            entity.fromTag(compoundTag);
            entity.setEntityId(entityId);
            entity.updateTrackedPosition(entity.getX(), entity.getY(), entity.getZ());
            world.addEntity(entityId, entity);
            
            //do not create client world while rendering or gl states will be disturbed
            if (entity instanceof Portal) {
                ClientWorldLoader.getWorld(((Portal) entity).dimensionTo);
                clientPortalSpawnSignal.emit(((Portal) entity));
            }
            
            client.getProfiler().pop();
        });
    }
    
    public static boolean getIsProcessingRedirectedMessage() {
        return isProcessingRedirectedMessage;
    }
}
