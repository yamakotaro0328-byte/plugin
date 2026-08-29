package com.yamakotaro.serverkit.dragonarena;

import com.yamakotaro.serverkit.Messages;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;

public class DragonArenaListener implements Listener {

    private final DragonArenaManager manager;
    private final Messages messages;

    public DragonArenaListener(DragonArenaManager manager, Messages messages) {
        this.manager = manager;
        this.messages = messages;
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }
        manager.onDragonDefeated(event.getEntity().getWorld());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        manager.onParticipantDied(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location location = manager.consumePendingRespawn(event.getPlayer().getUniqueId());
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.isFighting(player.getUniqueId())) {
            return;
        }
        String lowered = event.getMessage().toLowerCase();
        if (lowered.startsWith("/dragonfight leave") || lowered.startsWith("/df leave")) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(messages.get("dragonarena.command-blocked", Map.of()));
    }
}
