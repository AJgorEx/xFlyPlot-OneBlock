package com.AJgorEx.xFlyPlot.listeners;

import com.AJgorEx.xFlyPlot.OneBlockManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockPlaceListener implements Listener {
    private final OneBlockManager manager;

    public BlockPlaceListener(OneBlockManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (manager.isPlayerIslandWorld(player, event.getBlock().getWorld())) {
            manager.addIslandPoint(player.getUniqueId(), event.getBlockPlaced().getType());
        }
    }
}
