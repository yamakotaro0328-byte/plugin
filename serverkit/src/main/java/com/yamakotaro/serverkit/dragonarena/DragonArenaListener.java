package com.yamakotaro.serverkit.dragonarena;

import com.yamakotaro.serverkit.Messages;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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

    /**
     * Intercepts the hit that would have killed a fighting participant and cancels it outright,
     * so the vanilla death screen/respawn button never appears - the plugin instead switches
     * them to spectator mode with its own "You Died"-style title (see onParticipantDefeated).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!manager.isFighting(player.getUniqueId())) {
            return;
        }
        if (manager.isDefeated(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (player.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        event.setCancelled(true);
        manager.onParticipantDefeated(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
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
