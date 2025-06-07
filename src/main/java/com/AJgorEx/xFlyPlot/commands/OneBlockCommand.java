package com.AJgorEx.xFlyPlot.commands;

import com.AJgorEx.xFlyPlot.OneBlockManager;
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

        if (args.length > 0 && args[0].equalsIgnoreCase("home")) {
            manager.teleportHome(p);
        } else {
            manager.startIsland(p);
        }

        return true;
    }
}
