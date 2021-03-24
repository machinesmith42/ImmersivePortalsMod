package com.qouteall.immersive_portals.render;

import com.qouteall.immersive_portals.Global;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.ModMain;
import com.qouteall.immersive_portals.ducks.IEBuiltChunk;
import com.qouteall.immersive_portals.miscellaneous.GcMonitor;
import com.qouteall.immersive_portals.my_util.ObjectBuffer;
import com.qouteall.immersive_portals.optifine_compatibility.OFBuiltChunkStorageFix;
import com.qouteall.immersive_portals.render.context_management.PortalRendering;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class MyBuiltChunkStorage extends BuiltChunkStorage {
    public static class Column {
        public long mark = 0;
        public ChunkBuilder.BuiltChunk[] chunks;
        
        public Column(ChunkBuilder.BuiltChunk[] chunks) {
            this.chunks = chunks;
        }
    }
    
    public static class Preset {
        public final ChunkBuilder.BuiltChunk[] data;
        public long lastActiveTime = 0;
        public boolean isNeighborUpdated;
        
        public Preset(
            ChunkBuilder.BuiltChunk[] data, boolean isNeighborUpdated
        ) {
            this.data = data;
            this.isNeighborUpdated = isNeighborUpdated;
        }
    }
    
    private final ChunkBuilder factory;
    private final Long2ObjectOpenHashMap<Column> columnMap = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Preset> presets = new Long2ObjectOpenHashMap<>();
    private boolean shouldUpdateMainPresetNeighbor = true;
    private final ObjectBuffer<ChunkBuilder.BuiltChunk> builtChunkBuffer;
    private Preset currentPreset = null;
    
    private boolean isAlive = true;
    
    public MyBuiltChunkStorage(
        ChunkBuilder chunkBuilder,
        World world,
        int r,
        WorldRenderer worldRenderer
    ) {
        super(chunkBuilder, world, r, worldRenderer);
        factory = chunkBuilder;
        
        ModMain.postClientTickSignal.connectWithWeakRef(
            this, MyBuiltChunkStorage::tick
        );
        
        int cacheSize = sizeX * sizeY * sizeZ;
        if (Global.cacheGlBuffer) {
            cacheSize = cacheSize / 10;
        }
        
        builtChunkBuffer = new ObjectBuffer<>(
            cacheSize,
            () -> factory.new BuiltChunk(),
            ChunkBuilder.BuiltChunk::delete
        );
    }
    
    @Override
    protected void createChunks(ChunkBuilder chunkBuilder_1) {
        //nothing
    }
    
    @Override
    public void clear() {
        getAllActiveBuiltChunks().forEach(
            ChunkBuilder.BuiltChunk::delete
        );
        columnMap.clear();
        presets.clear();
        builtChunkBuffer.destroyAll();
        
        OFBuiltChunkStorageFix.onBuiltChunkStorageCleanup(this);
        
        isAlive = false;
    }
    
    @Override
    public void updateCameraPosition(double playerX, double playerZ) {
        MinecraftClient.getInstance().getProfiler().push("built_chunk_storage");
        
        int cameraBlockX = MathHelper.floor(playerX);
        int cameraBlockZ = MathHelper.floor(playerZ);
        
        int cameraChunkX = cameraBlockX >> 4;
        int cameraChunkZ = cameraBlockZ >> 4;
        ChunkPos cameraChunkPos = new ChunkPos(
            cameraChunkX, cameraChunkZ
        );
        
        Preset preset = presets.computeIfAbsent(
            cameraChunkPos.toLong(),
            whatever -> {
                return createPresetByChunkPos(cameraChunkX, cameraChunkZ);
            }
        );
        preset.lastActiveTime = System.nanoTime();
        
        this.chunks = preset.data;
        this.currentPreset = preset;
        
        MinecraftClient.getInstance().getProfiler().push("neighbor");
        manageNeighbor(preset);
        MinecraftClient.getInstance().getProfiler().pop();
        
        MinecraftClient.getInstance().getProfiler().pop();
    }
    
    private void manageNeighbor(Preset preset) {
        boolean isRenderingPortal = PortalRendering.isRendering();
        if (!isRenderingPortal) {
            if (shouldUpdateMainPresetNeighbor) {
                shouldUpdateMainPresetNeighbor = false;
                OFBuiltChunkStorageFix.updateNeighbor(this, preset.data);
                preset.isNeighborUpdated = true;
            }
        }
        
        if (!preset.isNeighborUpdated) {
            OFBuiltChunkStorageFix.updateNeighbor(this, preset.data);
            preset.isNeighborUpdated = true;
            if (isRenderingPortal) {
                shouldUpdateMainPresetNeighbor = true;
            }
        }
    }
    
    @Override
    public void scheduleRebuild(int cx, int cy, int cz, boolean isImportant) {
        //TODO change it
        ChunkBuilder.BuiltChunk builtChunk = provideBuiltChunkByChunkPos(cx, cy, cz);
        builtChunk.scheduleRebuild(isImportant);
    }
    
    // TODO update in 1.17
    public ChunkBuilder.BuiltChunk provideBuiltChunkByChunkPos(int cx, int cy, int cz) {
        Column column = provideColumn(ChunkPos.toLong(cx, cz));
        int chunkY = MathHelper.clamp(cy, 0, 15);
        return column.chunks[chunkY];
    }
    
    /**
     * {@link BuiltChunkStorage#updateCameraPosition(double, double)}
     */
    // TODO update in 1.17
    private Preset createPresetByChunkPos(int chunkX, int chunkZ) {
        ChunkBuilder.BuiltChunk[] chunks1 =
            new ChunkBuilder.BuiltChunk[this.sizeX * this.sizeY * this.sizeZ];
        
        for (int cx = 0; cx < this.sizeX; ++cx) {
            int xBlockSize = this.sizeX * 16;
            int xStart = (chunkX << 4) - xBlockSize / 2;
            int px = xStart + Math.floorMod(cx * 16 - xStart, xBlockSize);
            
            for (int cz = 0; cz < this.sizeZ; ++cz) {
                int zBlockSize = this.sizeZ * 16;
                int zStart = (chunkZ << 4) - zBlockSize / 2;
                int pz = zStart + Math.floorMod(cz * 16 - zStart, zBlockSize);
                
                Validate.isTrue(px % 16 == 0);
                Validate.isTrue(pz % 16 == 0);
                
                Column column = provideColumn(ChunkPos.toLong(px >> 4, pz >> 4));
                
                for (int cy = 0; cy < this.sizeY; ++cy) {
                    int index = this.getChunkIndex(cx, cy, cz);
                    chunks1[index] = column.chunks[cy];
                }
            }
        }
        
        return new Preset(chunks1, false);
    }
    
    /**
     * {@link BuiltChunkStorage#updateCameraPosition(double, double)}
     */
    // TODO update in 1.17
    private void foreachPresetCoveredChunkPoses(
        int centerChunkX, int centerChunkZ,
        LongConsumer func
    ) {
        ChunkBuilder.BuiltChunk[] chunks1 =
            new ChunkBuilder.BuiltChunk[this.sizeX * this.sizeY * this.sizeZ];
        
        for (int cx = 0; cx < this.sizeX; ++cx) {
            int xBlockSize = this.sizeX * 16;
            int xStart = (centerChunkX << 4) - xBlockSize / 2;
            int px = xStart + Math.floorMod(cx * 16 - xStart, xBlockSize);
            
            for (int cz = 0; cz < this.sizeZ; ++cz) {
                int zBlockSize = this.sizeZ * 16;
                int zStart = (centerChunkZ << 4) - zBlockSize / 2;
                int pz = zStart + Math.floorMod(cz * 16 - zStart, zBlockSize);
                
                Validate.isTrue(px % 16 == 0);
                Validate.isTrue(pz % 16 == 0);
                
                long chunkPos = ChunkPos.toLong(px >> 4, pz >> 4);
                
                func.accept(chunkPos);
            }
        }
    }
    
    // TODO update in 1.17
    
    //copy because private
    private int getChunkIndex(int x, int y, int z) {
        return (z * this.sizeY + y) * this.sizeX + x;
    }
    
    private static BlockPos getBasePos(BlockPos blockPos) {
        return new BlockPos(
            MathHelper.floorDiv(blockPos.getX(), 16) * 16,
            MathHelper.floorDiv(MathHelper.clamp(blockPos.getY(), 0, 255), 16) * 16,
            MathHelper.floorDiv(blockPos.getZ(), 16) * 16
        );
    }
    
    public Column provideColumn(long chunkPos) {
        return columnMap.computeIfAbsent(chunkPos, this::createColumn);
    }
    
    // NOTE update on 1.17
    private Column createColumn(long chunkPos) {
        ChunkBuilder.BuiltChunk[] array = new ChunkBuilder.BuiltChunk[sizeY];
        
        int chunkX = ChunkPos.getPackedX(chunkPos);
        int chunkZ = ChunkPos.getPackedZ(chunkPos);
        
        for (int chunkY = 0; chunkY < sizeY; chunkY++) {
            ChunkBuilder.BuiltChunk builtChunk = builtChunkBuffer.takeObject();
            
            builtChunk.setOrigin(
                chunkX << 4, chunkY << 4, chunkZ << 4
            );
            
            OFBuiltChunkStorageFix.onBuiltChunkCreated(
                this, builtChunk
            );
            
            array[chunkY] = builtChunk;
        }
        
        return new Column(array);
    }
    
    private void tick() {
        if (!isAlive) {
            return;
        }
        
        ClientWorld worldClient = MinecraftClient.getInstance().world;
        if (worldClient != null) {
            if (GcMonitor.isMemoryNotEnough()) {
                if (worldClient.getTime() % 3 == 0) {
                    purge();
                }
            }
            else {
                if (worldClient.getTime() % 213 == 66) {
                    purge();
                }
            }
        }
    }
    
    private void purge() {
        MinecraftClient.getInstance().getProfiler().push("my_built_chunk_storage_purge");
        
        long dropTime = Helper.secondToNano(GcMonitor.isMemoryNotEnough() ? 3 : 20);
        
        long currentTime = System.nanoTime();
        
        presets.long2ObjectEntrySet().removeIf(entry -> {
            Preset preset = entry.getValue();
            
            long centerChunkPos = entry.getLongKey();
            
            boolean shouldDropPreset = shouldDropPreset(dropTime, currentTime, preset);
            
            if (!shouldDropPreset) {
                foreachPresetCoveredChunkPoses(
                    ChunkPos.getPackedX(centerChunkPos),
                    ChunkPos.getPackedZ(centerChunkPos),
                    columnChunkPos -> {
                        Column column = columnMap.get(columnChunkPos);
                        column.mark = currentTime;
                    }
                );
            }
            
            return shouldDropPreset;
        });
        
        columnMap.long2ObjectEntrySet().removeIf(entry -> {
            Column column = entry.getValue();
            boolean shouldRemove = column.mark != currentTime;
            
            if (shouldRemove) {
                for (ChunkBuilder.BuiltChunk chunk : column.chunks) {
                    builtChunkBuffer.returnObject(chunk);
                }
            }
            
            return shouldRemove;
        });
        
        OFBuiltChunkStorageFix.purgeRenderRegions(this);
        
        MinecraftClient.getInstance().getProfiler().pop();
    }
    
    private boolean shouldDropPreset(long dropTime, long currentTime, Preset preset) {
        if (preset.data == this.chunks) {
            return false;
        }
        return currentTime - preset.lastActiveTime > dropTime;
    }
    
    private Set<ChunkBuilder.BuiltChunk> getAllActiveBuiltChunks() {
        HashSet<ChunkBuilder.BuiltChunk> result = new HashSet<>();
        
        presets.forEach((key, preset) -> {
            result.addAll(Arrays.asList(preset.data));
        });
        
        if (chunks != null) {
            result.addAll(Arrays.asList(chunks));
        }
        
        return result;
    }
    
    private void foreachActiveBuiltChunks(Consumer<ChunkBuilder.BuiltChunk> func) {
        if (chunks != null) {
            for (ChunkBuilder.BuiltChunk chunk : chunks) {
                func.accept(chunk);
            }
        }
        
        for (Preset value : presets.values()) {
            for (ChunkBuilder.BuiltChunk chunk : value.data) {
                func.accept(chunk);
            }
        }
    }
    
    public int getManagedChunkNum() {
        return columnMap.size() * sizeY;
    }
    
    public String getDebugString() {
        return String.format(
            "Built Chunk Storage Columns:%s",
            columnMap.size()
        );
    }
    
    public int getRadius() {
        return (sizeX - 1) / 2;
    }
    
    public boolean isRegionActive(int cxStart, int czStart, int cxEnd, int czEnd) {
        for (int cx = cxStart; cx <= cxEnd; cx++) {
            for (int cz = czStart; cz <= czEnd; cz++) {
                if (columnMap.containsKey(ChunkPos.toLong(cx, cz))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    public void onChunkUnload(int chunkX, int chunkZ) {
        long chunkPos = ChunkPos.toLong(chunkX, chunkZ);
        Column column = columnMap.get(chunkPos);
        if (column != null) {
            for (ChunkBuilder.BuiltChunk builtChunk : column.chunks) {
                ((IEBuiltChunk) builtChunk).fullyReset();
            }
        }
    }
    
    public ChunkBuilder.BuiltChunk getSectionFromRawArray(
        BlockPos sectionOrigin, ChunkBuilder.BuiltChunk[] chunks
    ) {
        int i = MathHelper.floorDiv(sectionOrigin.getX(), 16);
        int j = MathHelper.floorDiv(sectionOrigin.getY(), 16);
        int k = MathHelper.floorDiv(sectionOrigin.getZ(), 16);
        if (j >= 0 && j < this.sizeY) {
            i = MathHelper.floorMod(i, this.sizeX);
            k = MathHelper.floorMod(k, this.sizeZ);
            return chunks[this.getChunkIndex(i, j, k)];
        }
        else {
            return null;
        }
    }
}
