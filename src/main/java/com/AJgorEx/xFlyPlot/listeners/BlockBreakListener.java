package com.AJgorEx.xFlyPlot.listeners;

import com.AJgorEx.xFlyPlot.OneBlockManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class BlockBreakListener implements Listener {

    private final OneBlockManager manager;

    public BlockBreakListener(OneBlockManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (manager.isIslandBlock(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            manager.handleBlockBreak(player);
        }
    }
}
