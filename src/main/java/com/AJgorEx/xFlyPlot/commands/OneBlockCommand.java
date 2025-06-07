package com.AJgorEx.xFlyPlot.commands;

import com.AJgorEx.xFlyPlot.OneBlockManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OneBlockCommand implements CommandExecutor {

    private final OneBlockManager manager;

    public OneBlockCommand(OneBlockManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko gracze mogą tego używać.");
            return true;
        }

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "home" -> manager.teleportHome(p);
                case "reload" -> {
                    if (p.hasPermission("oneblock.reload")) {
                        manager.reloadPhases();
                        p.sendMessage(ChatColor.GREEN + "Plik phases.yml przeładowany.");
                    } else {
                        p.sendMessage(ChatColor.RED + "Brak uprawnień.");
                    }
                }
                default -> manager.startIsland(p);
            }
        } else {
            manager.startIsland(p);
        }

        return true;
    }
}
