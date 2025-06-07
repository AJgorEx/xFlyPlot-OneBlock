package com.AJgorEx.xFlyPlot;

import com.AJgorEx.xFlyPlot.commands.OneBlockCommand;
import com.AJgorEx.xFlyPlot.listeners.BlockBreakListener;
import com.AJgorEx.xFlyPlot.listeners.VoidFallListener;
import com.AJgorEx.xFlyPlot.listeners.MenuListener;
import com.AJgorEx.xFlyPlot.listeners.GeneratorInteractListener;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private OneBlockManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new OneBlockManager(this);
        getCommand("oneblock").setExecutor(new OneBlockCommand(manager));
        getServer().getPluginManager().registerEvents(new BlockBreakListener(manager), this);
        getServer().getPluginManager().registerEvents(new VoidFallListener(manager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(manager), this);
        getServer().getPluginManager().registerEvents(new GeneratorInteractListener(manager), this);
        getLogger().info("xFlyPlot enabled.");
    }

    public OneBlockManager getOneBlockManager() {
        return manager;
    }
}
