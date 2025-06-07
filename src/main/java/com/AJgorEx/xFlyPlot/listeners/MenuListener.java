package com.AJgorEx.xFlyPlot.listeners;

import com.AJgorEx.xFlyPlot.OneBlockManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MenuListener implements Listener {
    private final OneBlockManager manager;

    public MenuListener(OneBlockManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!ChatColor.stripColor(event.getView().getTitle()).equalsIgnoreCase("OneBlock")) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        Material type = event.getCurrentItem().getType();
        switch (type) {
            case GRASS_BLOCK -> manager.startIsland(player);
            case OAK_DOOR -> manager.teleportHome(player);
            case EXPERIENCE_BOTTLE -> manager.sendProgress(player);
            case PAPER -> manager.sendLevel(player);
            case BOOK -> manager.listPhases(player);
            case BARRIER -> manager.deleteIsland(player);
        }

        player.closeInventory();
    }
}
