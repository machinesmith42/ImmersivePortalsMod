package com.qouteall.imm_ptl_peripheral.alternate_dimension;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.qouteall.immersive_portals.Global;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.ModMain;
import com.qouteall.immersive_portals.api.IPDimensionAPI;
import com.qouteall.immersive_portals.ducks.IEWorld;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.StructureConfig;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.HashMap;
import java.util.Optional;

public class AlternateDimensions {
    public static void init() {
        IPDimensionAPI.onServerWorldInit.connect(AlternateDimensions::initializeAlternateDimensions);
        
        ModMain.postServerTickSignal.connect(AlternateDimensions::tick);
    }
    
    private static void initializeAlternateDimensions(
        GeneratorOptions generatorOptions, DynamicRegistryManager registryManager
    ) {
        SimpleRegistry<DimensionOptions> registry = generatorOptions.getDimensions();
        long seed = generatorOptions.getSeed();
        if (!Global.enableAlternateDimensions) {
            return;
        }
        
        DimensionType surfaceTypeObject = registryManager.get(Registry.DIMENSION_TYPE_KEY).get(new Identifier("immersive_portals:surface_type"));
        
        if (surfaceTypeObject == null) {
            Helper.err("Missing dimension type immersive_portals:surface_type");
            return;
        }
        
        //different seed
        IPDimensionAPI.addDimension(
            seed,
            registry,
            alternate1Option.getValue(),
            () -> surfaceTypeObject,
            createSkylandGenerator(seed + 1, registryManager)
        );
        IPDimensionAPI.markDimensionNonPersistent(alternate1Option.getValue());
        
        IPDimensionAPI.addDimension(
            seed,
            registry,
            alternate2Option.getValue(),
            () -> surfaceTypeObject,
            createSkylandGenerator(seed, registryManager)
        );
        IPDimensionAPI.markDimensionNonPersistent(alternate2Option.getValue());
        
        //different seed
        IPDimensionAPI.addDimension(
            seed,
            registry,
            alternate3Option.getValue(),
            () -> surfaceTypeObject,
            createErrorTerrainGenerator(seed + 1, registryManager)
        );
        IPDimensionAPI.markDimensionNonPersistent(alternate3Option.getValue());
        
        IPDimensionAPI.addDimension(
            seed,
            registry,
            alternate4Option.getValue(),
            () -> surfaceTypeObject,
            createErrorTerrainGenerator(seed, registryManager)
        );
        IPDimensionAPI.markDimensionNonPersistent(alternate4Option.getValue());
        
        IPDimensionAPI.addDimension(
            seed,
            registry,
            alternate5Option.getValue(),
            () -> surfaceTypeObject,
            createVoidGenerator(registryManager)
        );
        IPDimensionAPI.markDimensionNonPersistent(alternate5Option.getValue());
    }
    
    
    public static final RegistryKey<DimensionOptions> alternate1Option = RegistryKey.of(
        Registry.DIMENSION_OPTIONS,
        new Identifier("immersive_portals:alternate1")
    );
    public static final RegistryKey<DimensionOptions> alternate2Option = RegistryKey.of(
        Registry.DIMENSION_OPTIONS,
        new Identifier("immersive_portals:alternate2")
    );
    public static final RegistryKey<DimensionOptions> alternate3Option = RegistryKey.of(
        Registry.DIMENSION_OPTIONS,
        new Identifier("immersive_portals:alternate3")
    );
    public static final RegistryKey<DimensionOptions> alternate4Option = RegistryKey.of(
        Registry.DIMENSION_OPTIONS,
        new Identifier("immersive_portals:alternate4")
    );
    public static final RegistryKey<DimensionOptions> alternate5Option = RegistryKey.of(
        Registry.DIMENSION_OPTIONS,
        new Identifier("immersive_portals:alternate5")
    );
    public static final RegistryKey<DimensionType> surfaceType = RegistryKey.of(
        Registry.DIMENSION_TYPE_KEY,
        new Identifier("immersive_portals:surface_type")
    );
    public static final RegistryKey<World> alternate1 = RegistryKey.of(
        Registry.DIMENSION,
        new Identifier("immersive_portals:alternate1")
    );
    public static final RegistryKey<World> alternate2 = RegistryKey.of(
        Registry.DIMENSION,
        new Identifier("immersive_portals:alternate2")
    );
    public static final RegistryKey<World> alternate3 = RegistryKey.of(
        Registry.DIMENSION,
        new Identifier("immersive_portals:alternate3")
    );
    public static final RegistryKey<World> alternate4 = RegistryKey.of(
        Registry.DIMENSION,
        new Identifier("immersive_portals:alternate4")
    );
    public static final RegistryKey<World> alternate5 = RegistryKey.of(
        Registry.DIMENSION,
        new Identifier("immersive_portals:alternate5")
    );
//    public static DimensionType surfaceTypeObject;
    
    public static boolean isAlternateDimension(World world) {
        final RegistryKey<World> key = world.getRegistryKey();
        return key == alternate1 ||
            key == alternate2 ||
            key == alternate3 ||
            key == alternate4 ||
            key == alternate5;
    }
    
    private static void syncWithOverworldTimeWeather(ServerWorld world, ServerWorld overworld) {
        ((IEWorld) world).portal_setWeather(
            overworld.getRainGradient(1), overworld.getRainGradient(1),
            overworld.getThunderGradient(1), overworld.getThunderGradient(1)
        );
    }
    
    public static ChunkGenerator createSkylandGenerator(long seed, DynamicRegistryManager rm) {
        
        MutableRegistry<Biome> biomeRegistry = rm.get(Registry.BIOME_KEY);
        VanillaLayeredBiomeSource biomeSource = new VanillaLayeredBiomeSource(
            seed, false, false, biomeRegistry
        );
        
        MutableRegistry<ChunkGeneratorSettings> settingsRegistry = rm.get(Registry.NOISE_SETTINGS_WORLDGEN);
        
        HashMap<StructureFeature<?>, StructureConfig> structureMap = new HashMap<>();
        structureMap.putAll(StructuresConfig.DEFAULT_STRUCTURES);
        structureMap.remove(StructureFeature.MINESHAFT);
        structureMap.remove(StructureFeature.STRONGHOLD);
        
        StructuresConfig structuresConfig = new StructuresConfig(
            Optional.empty(), structureMap
        );
        ChunkGeneratorSettings skylandSetting = ChunkGeneratorSettings.createIslandSettings(
            structuresConfig, Blocks.STONE.getDefaultState(),
            Blocks.WATER.getDefaultState(), new Identifier("imm_ptl:skyland_gen_id"),
            false, false
        );
        
        return new NoiseChunkGenerator(
            biomeSource, seed, () -> skylandSetting
        );
    }
    
    public static ChunkGenerator createErrorTerrainGenerator(long seed, DynamicRegistryManager rm) {
        MutableRegistry<Biome> biomeRegistry = rm.get(Registry.BIOME_KEY);
        
        ChaosBiomeSource chaosBiomeSource = new ChaosBiomeSource(seed, biomeRegistry);
        return new ErrorTerrainGenerator(seed, chaosBiomeSource);
    }
    
    public static ChunkGenerator createVoidGenerator(DynamicRegistryManager rm) {
        MutableRegistry<Biome> biomeRegistry = rm.get(Registry.BIOME_KEY);
        
        StructuresConfig structuresConfig = new StructuresConfig(
            Optional.of(StructuresConfig.DEFAULT_STRONGHOLD),
            Maps.newHashMap(ImmutableMap.of())
        );
        FlatChunkGeneratorConfig flatChunkGeneratorConfig =
            new FlatChunkGeneratorConfig(structuresConfig, biomeRegistry);
        flatChunkGeneratorConfig.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.AIR));
        flatChunkGeneratorConfig.updateLayerBlocks();
        
        return new FlatChunkGenerator(flatChunkGeneratorConfig);
    }
    
    
    private static void tick() {
        if (!Global.enableAlternateDimensions) {
            return;
        }
        
        ServerWorld overworld = McHelper.getServerWorld(World.OVERWORLD);
        
        syncWithOverworldTimeWeather(McHelper.getServerWorld(alternate1), overworld);
        syncWithOverworldTimeWeather(McHelper.getServerWorld(alternate2), overworld);
        syncWithOverworldTimeWeather(McHelper.getServerWorld(alternate3), overworld);
        syncWithOverworldTimeWeather(McHelper.getServerWorld(alternate4), overworld);
        syncWithOverworldTimeWeather(McHelper.getServerWorld(alternate5), overworld);
    }
    
    
}
