package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerCleanupListener implements Listener {

    private final EcoTpPlugin plugin;

    public PlayerCleanupListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        plugin.getConfirmationManager().cancelSilently(uuid);
        plugin.getTpaManager().cancelSilently(uuid);
        plugin.getTeleportSafetyManager().cancelSilently(uuid);
        plugin.getChatInputManager().cancelSilently(uuid);
        plugin.getCombatTracker().clear(uuid);
    }
}
