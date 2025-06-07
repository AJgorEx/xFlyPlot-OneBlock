package com.AJgorEx.xFlyPlot;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    @SuppressWarnings("deprecation")
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        // Return an empty chunk - Bukkit will fill it with air by default
        return createChunkData(world);
    }
}
