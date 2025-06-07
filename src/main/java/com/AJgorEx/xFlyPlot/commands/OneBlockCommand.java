package com.AJgorEx.xFlyPlot.commands;

import com.AJgorEx.xFlyPlot.OneBlockManager;
import com.AJgorEx.xFlyPlot.Phase;
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
                case "progress" -> manager.sendProgress(p);
                case "phases" -> manager.listPhases(p);
                case "menu" -> manager.openMenu(p);
                case "start" -> manager.startIsland(p);
                case "reset" -> {
                    manager.removePlayer(p.getUniqueId());
                    p.sendMessage(ChatColor.YELLOW + "Twoja wyspa zostala zresetowana.");
                }
                case "phase" -> {
                    Phase phase = manager.getPlayerPhase(p.getUniqueId());
                    p.sendMessage(ChatColor.YELLOW + "Aktualna faza: " + ChatColor.GOLD + phase.getName());
                }
                case "reload" -> {
                    if (p.hasPermission("oneblock.reload")) {
                        manager.reloadPhases();
                        p.sendMessage(ChatColor.GREEN + "Przeladowano konfiguracje faz.");
                    } else {
                        p.sendMessage(ChatColor.RED + "Nie masz uprawnien.");
                    }
                }
                case "help" -> sendHelp(p);
                default -> manager.openMenu(p);
            }
        } else {
            manager.openMenu(p);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Uzycie: /oneblock <subkomenda>");
        player.sendMessage(ChatColor.GRAY + "menu, start, home, progress, phases, phase, reset, reload, help");
    }
}
