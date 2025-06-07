package com.AJgorEx.xFlyPlot.listeners;

import com.AJgorEx.xFlyPlot.OneBlockManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

public class GeneratorInteractListener implements Listener {
    private final OneBlockManager manager;

    public GeneratorInteractListener(OneBlockManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getClickedBlock() == null) return;
        if (!manager.isIslandBlock(player, event.getClickedBlock().getLocation())) return;
        if (player.isSneaking()) return;

        event.setCancelled(true);
        manager.openMenu(player);
    }
}
