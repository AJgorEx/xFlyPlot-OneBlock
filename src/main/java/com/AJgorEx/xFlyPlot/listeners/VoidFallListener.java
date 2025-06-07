package com.AJgorEx.xFlyPlot.listeners;

import com.AJgorEx.xFlyPlot.OneBlockManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class VoidFallListener implements Listener {

    private final OneBlockManager manager;

    public VoidFallListener(OneBlockManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onVoidFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player &&
                event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            player.teleport(manager.getGenerator(player.getUniqueId()));
        }
    }
}
