package com.aerodynamics4mc.runtime;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

final class LevelgenSeedTerrainProvider implements SeedTerrainProvider {
    private static final float WATER_ROUGHNESS_LENGTH_METERS = 0.0003f;
    private static final float PLAINS_ROUGHNESS_LENGTH_METERS = 0.05f;
    private static final float FOREST_ROUGHNESS_LENGTH_METERS = 0.90f;
    private static final float ROCK_ROUGHNESS_LENGTH_METERS = 0.08f;
    private static final float SNOW_ROUGHNESS_LENGTH_METERS = 0.003f;

    private final SeedTerrainProvider fallback = new HashedSeedTerrainProvider();

    @Override
    public TerrainSample sample(ServerLevel level, int blockX, int blockZ) {
        try {
            ServerChunkCache chunkManager = level.getChunkSource();
            ChunkGenerator generator = chunkManager.getGenerator();
            RandomState noiseConfig = chunkManager.randomState();
            if (generator == null || noiseConfig == null) {
                return fallback.sample(level, blockX, blockZ);
            }

            int terrainHeightBlocks = generator.getFirstFreeHeight(
                blockX,
                blockZ,
                Heightmap.Types.WORLD_SURFACE_WG,
                level,
                noiseConfig
            );
            int biomeY = Mth.clamp(
                terrainHeightBlocks - 1,
                level.getMinBuildHeight(),
                level.getMinBuildHeight() + level.getHeight() - 1
            );
            Holder<Biome> biomeEntry = level.getUncachedNoiseBiome(
                Math.floorDiv(blockX, 4),
                Math.floorDiv(biomeY, 4),
                Math.floorDiv(blockZ, 4)
            );
            Biome biome = biomeEntry.value();
            float biomeTemperature = biome.getBaseTemperature();
            int seaLevel = level.getSeaLevel();

            byte surfaceClass;
            float roughnessLengthMeters;
            if (biomeEntry.is(BiomeTags.IS_OCEAN)
                || biomeEntry.is(BiomeTags.IS_RIVER)
                || terrainHeightBlocks < seaLevel - 1) {
                surfaceClass = HashedSeedTerrainProvider.SURFACE_CLASS_WATER;
                roughnessLengthMeters = WATER_ROUGHNESS_LENGTH_METERS;
            } else if (biomeTemperature < 0.20f && terrainHeightBlocks > seaLevel + 4) {
                surfaceClass = HashedSeedTerrainProvider.SURFACE_CLASS_SNOW;
                roughnessLengthMeters = SNOW_ROUGHNESS_LENGTH_METERS;
            } else if (biomeEntry.is(BiomeTags.IS_FOREST)
                || biomeEntry.is(BiomeTags.IS_JUNGLE)
                || biomeEntry.is(BiomeTags.IS_TAIGA)) {
                surfaceClass = HashedSeedTerrainProvider.SURFACE_CLASS_FOREST;
                roughnessLengthMeters = FOREST_ROUGHNESS_LENGTH_METERS;
            } else if (biomeEntry.is(BiomeTags.IS_BADLANDS)
                || biomeEntry.is(BiomeTags.IS_MOUNTAIN)
                || terrainHeightBlocks > seaLevel + 72) {
                surfaceClass = HashedSeedTerrainProvider.SURFACE_CLASS_ROCK;
                roughnessLengthMeters = ROCK_ROUGHNESS_LENGTH_METERS;
            } else {
                surfaceClass = HashedSeedTerrainProvider.SURFACE_CLASS_PLAINS;
                roughnessLengthMeters = PLAINS_ROUGHNESS_LENGTH_METERS;
            }

            return new TerrainSample(terrainHeightBlocks, biomeTemperature, roughnessLengthMeters, surfaceClass);
        } catch (Throwable ignored) {
            return fallback.sample(level, blockX, blockZ);
        }
    }
}
