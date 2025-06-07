package com.AJgorEx.xFlyPlot;

import org.bukkit.Material;
import java.util.List;

public class Phase {
    private final String name;
    private final int blockCount;
    private final List<Material> materials;

    public Phase(String name, int blockCount, List<Material> materials) {
        this.name = name;
        this.blockCount = blockCount;
        this.materials = materials;
    }

    public String getName() { return name; }
    public int getBlockCount() { return blockCount; }
    public List<Material> getMaterials() { return materials; }
}
