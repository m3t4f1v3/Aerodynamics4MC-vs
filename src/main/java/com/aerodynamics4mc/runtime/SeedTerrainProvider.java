package com.aerodynamics4mc.runtime;

import net.minecraft.server.level.ServerLevel;

interface SeedTerrainProvider {
    TerrainSample sample(ServerLevel level, int blockX, int blockZ);

    record TerrainSample(
        float terrainHeightBlocks,
        float biomeTemperature,
        float roughnessLengthMeters,
        byte surfaceClass
    ) {
    }
}
