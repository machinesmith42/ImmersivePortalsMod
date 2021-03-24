package com.qouteall.immersive_portals.mixin.common.entity_sync;

import com.google.common.collect.Lists;
import com.qouteall.immersive_portals.Global;
import com.qouteall.immersive_portals.ducks.IEEntityTracker;
import com.qouteall.immersive_portals.ducks.IEThreadedAnvilChunkStorage;
import com.qouteall.immersive_portals.network.CommonNetwork;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinThreadedAnvilChunkStorage_E implements IEThreadedAnvilChunkStorage {
    
    @Shadow
    @Final
    public Int2ObjectMap<ThreadedAnvilChunkStorage.EntityTracker> entityTrackers;
    
    @Shadow
    abstract void handlePlayerAddedOrRemoved(ServerPlayerEntity player, boolean added);
    
    @Shadow
    @Final
    private ServerWorld world;
    
    @Inject(
        method = "unloadEntity",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onUnloadEntity(Entity entity, CallbackInfo ci) {
        //when the player leave this dimension, do not stop tracking entities
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            if (Global.serverTeleportationManager.isTeleporting(player)) {
                Object tracker = entityTrackers.remove(entity.getEntityId());
                ((IEEntityTracker) tracker).stopTrackingToAllPlayers_();
                handlePlayerAddedOrRemoved(player, false);
                ci.cancel();
            }
        }
    }
    
    // Managed by EntitySync
    @Inject(method = "tickPlayerMovement", at = @At("HEAD"), cancellable = true)
    private void onTickPlayerMovement(CallbackInfo ci) {
        ci.cancel();
    }
    
    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer) {
        entityTrackers.values().forEach(obj -> {
            ((IEEntityTracker) obj).onPlayerRespawn(oldPlayer);
        });
    }
    
    /**
     * {@link ThreadedAnvilChunkStorage#sendChunkDataPackets(ServerPlayerEntity, Packet[], WorldChunk)}
     */
    @Override
    public void updateEntityTrackersAfterSendingChunkPacket(
        WorldChunk chunk, ServerPlayerEntity player
    ) {
        List<Entity> attachedEntityList = Lists.newArrayList();
        List<Entity> passengerList = Lists.newArrayList();
        
        for (Object entityTracker : this.entityTrackers.values()) {
            Entity entity = ((IEEntityTracker) entityTracker).getEntity_();
            if (entity != player && entity.chunkX == chunk.getPos().x && entity.chunkZ == chunk.getPos().z) {
                ((IEEntityTracker) entityTracker).updateEntityTrackingStatus(player);
                if (entity instanceof MobEntity && ((MobEntity) entity).getHoldingEntity() != null) {
                    attachedEntityList.add(entity);
                }
                
                if (!entity.getPassengerList().isEmpty()) {
                    passengerList.add(entity);
                }
            }
        }
        
        CommonNetwork.withForceRedirect(
            world.getRegistryKey(),
            () -> {
                for (Entity entity : attachedEntityList) {
                    player.networkHandler.sendPacket(new EntityAttachS2CPacket(
                        entity, ((MobEntity) entity).getHoldingEntity()
                    ));
                }
                
                for (Entity entity : passengerList) {
                    player.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(entity));
                }
            }
        );
    }
    
    @Override
    public void resendSpawnPacketToTrackers(Entity entity) {
        Object tracker = entityTrackers.get(entity.getEntityId());
        Validate.notNull(tracker, "entity not yet tracked");
        ((IEEntityTracker) tracker).resendSpawnPacketToTrackers();
    }
    
    @Override
    public Int2ObjectMap<ThreadedAnvilChunkStorage.EntityTracker> getEntityTrackerMap() {
        return entityTrackers;
    }
}
