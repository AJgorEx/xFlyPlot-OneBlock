package com.AJgorEx.xFlyPlot;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;

public class MessageManager {
    private final Plugin plugin;
    private FileConfiguration messages;
    private String prefix = "";

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", "pl");
        String fileName = "messages_" + lang + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        prefix = ChatColor.translateAlternateColorCodes('&', messages.getString("prefix", ""));
    }

    public String get(String key) {
        String msg = messages.getString(key, key);
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    public void send(Player player, String key) {
        player.sendMessage(get(key));
    }
}
