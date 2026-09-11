package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/** Catches players who fall out of the lobby world and returns them to spawn instead of letting
 * them keep falling or die - the lobby has no respawn screen worth showing for that. */
public class VoidTeleportListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public VoidTeleportListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.isFeatureEnabled("void-teleport")) {
            return;
        }
        event.setCancelled(true);
        plugin.getSpawnManager().getSpawn().ifPresent(player::teleport);
    }
}
