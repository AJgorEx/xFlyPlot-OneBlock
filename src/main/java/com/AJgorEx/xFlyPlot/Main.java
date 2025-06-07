package com.AJgorEx.xFlyPlot;

import com.AJgorEx.xFlyPlot.commands.OneBlockCommand;
import com.AJgorEx.xFlyPlot.listeners.BlockBreakListener;
import com.AJgorEx.xFlyPlot.listeners.VoidFallListener;
import com.AJgorEx.xFlyPlot.listeners.MenuListener;
import com.AJgorEx.xFlyPlot.listeners.GeneratorInteractListener;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private OneBlockManager manager;
    private MessageManager messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageManager(this);
        manager = new OneBlockManager(this, messages);
        getCommand("oneblock").setExecutor(new OneBlockCommand(manager));
        getServer().getPluginManager().registerEvents(new BlockBreakListener(manager), this);
        getServer().getPluginManager().registerEvents(new VoidFallListener(manager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(manager), this);
        getServer().getPluginManager().registerEvents(new com.AJgorEx.xFlyPlot.listeners.BlockPlaceListener(manager), this);
        getServer().getPluginManager().registerEvents(new GeneratorInteractListener(manager), this);
        getLogger().info("xFlyPlot enabled.");
    }

    public OneBlockManager getOneBlockManager() {
        return manager;
    }
}
