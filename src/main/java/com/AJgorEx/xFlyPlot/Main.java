package com.AJgorEx.xFlyPlot;

import com.AJgorEx.xFlyPlot.commands.OneBlockCommand;
import com.AJgorEx.xFlyPlot.listeners.BlockBreakListener;
import com.AJgorEx.xFlyPlot.listeners.VoidFallListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class Main extends JavaPlugin {

    private OneBlockManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        manager = new OneBlockManager(this);
        Objects.requireNonNull(getCommand("oneblock")).setExecutor(new OneBlockCommand(manager));
        getServer().getPluginManager().registerEvents(new BlockBreakListener(manager), this);
        getServer().getPluginManager().registerEvents(new VoidFallListener(manager), this);

        getLogger().info("xFlyPlot enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.clearAll();
        }
    }

    public OneBlockManager getOneBlockManager() {
        return manager;
    }
}
