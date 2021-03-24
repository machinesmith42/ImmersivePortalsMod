package com.qouteall.immersive_portals.mixin.client;

import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.ModMain;
import com.qouteall.immersive_portals.ducks.IEMinecraftClient;
import com.qouteall.immersive_portals.miscellaneous.FPSMonitor;
import com.qouteall.immersive_portals.network.CommonNetworkClient;
import com.qouteall.immersive_portals.render.context_management.WorldRenderInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient extends ReentrantThreadExecutor<Runnable> implements IEMinecraftClient {
    @Final
    @Shadow
    @Mutable
    private Framebuffer framebuffer;
    
    @Shadow
    public Screen currentScreen;
    
    @Mutable
    @Shadow
    @Final
    public WorldRenderer worldRenderer;
    
    @Shadow
    private static int currentFps;
    
    @Shadow
    public abstract Profiler getProfiler();
    
    @Shadow
    @Nullable
    public ClientWorld world;
    
    @Mutable
    @Shadow
    @Final
    private BufferBuilderStorage bufferBuilders;
    
    public MixinMinecraftClient(String string) {
        super(string);
        throw new RuntimeException();
    }
    
    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld;tick(Ljava/util/function/BooleanSupplier;)V",
            shift = At.Shift.AFTER
        )
    )
    private void onAfterClientTick(CallbackInfo ci) {
        getProfiler().push("imm_ptl_tick_signal");
        ModMain.postClientTickSignal.emit();
        getProfiler().pop();
        
        CGlobal.clientTeleportationManager.manageTeleportation(0);
    }
    
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/snooper/Snooper;update()V"
        )
    )
    private void onSnooperUpdate(boolean tick, CallbackInfo ci) {
        FPSMonitor.updateEverySecond(currentFps);
    }
    
    @Inject(
        method = "Lnet/minecraft/client/MinecraftClient;setWorld(Lnet/minecraft/client/world/ClientWorld;)V",
        at = @At("HEAD")
    )
    private void onSetWorld(ClientWorld clientWorld_1, CallbackInfo ci) {
        ModMain.clientCleanupSignal.emit();
    }
    
    //avoid messing up rendering states in fabulous
    @Inject(method = "isFabulousGraphicsOrBetter", at = @At("HEAD"), cancellable = true)
    private static void onIsFabulousGraphicsOrBetter(CallbackInfoReturnable<Boolean> cir) {
        if (WorldRenderInfo.isRendering()) {
            cir.setReturnValue(false);
        }
    }
    
    // when processing redirected message, a mod packet processing may call execute()
    // then the task gets delayed. keep the hacky redirect after delaying
    @Inject(
        method = "createTask",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onCreateTask(Runnable runnable, CallbackInfoReturnable<Runnable> cir) {
        MinecraftClient this_ = (MinecraftClient) (Object) this;
        if (this_.isOnThread()) {
            if (CommonNetworkClient.getIsProcessingRedirectedMessage()) {
                ClientWorld currWorld = this_.world;
                Runnable newRunnable = () -> {
                    CommonNetworkClient.withSwitchedWorld(currWorld, runnable);
                };
                cir.setReturnValue(newRunnable);
            }
        }
    }
    
    /**
     * Make sure that the redirected packet handling won't be delayed
     */
    @Override
    protected boolean shouldExecuteAsync() {
        boolean onThread = isOnThread();
        
        if (onThread) {
            if (CommonNetworkClient.isProcessingRedirectedMessage) {
                return false;
            }
        }
        
        return this.hasRunningTasks() || !onThread;
    }
    
    @Override
    public void setFrameBuffer(Framebuffer buffer) {
        framebuffer = buffer;
    }
    
    @Override
    public Screen getCurrentScreen() {
        return currentScreen;
    }
    
    @Override
    public void setWorldRenderer(WorldRenderer r) {
        worldRenderer = r;
    }
    
    @Override
    public void setBufferBuilderStorage(BufferBuilderStorage arg) {
        bufferBuilders = arg;
    }
}
