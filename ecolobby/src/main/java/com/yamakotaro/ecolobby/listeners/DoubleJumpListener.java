package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The classic hub "double jump": players are given setAllowFlight(true) (see JoinListener)
 * without ever actually being allowed to fly. Pressing jump a second time while airborne makes
 * the client request real flight, firing PlayerToggleFlightEvent - this cancels that request and
 * launches the player with a velocity boost instead, so it reads as a jump rather than flight.
 */
public class DoubleJumpListener implements Listener {

    private final EcoLobbyPlugin plugin;
    private final Map<UUID, Long> lastJumpMillis = new HashMap<>();

    public DoubleJumpListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!plugin.isFeatureEnabled("double-jump")) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return; // These gamemodes have their own, real flight - leave it alone.
        }
        if (!event.isFlying()) {
            return; // A "stop flying" request - not relevant here, nothing to cancel or boost.
        }
        // Always cancel the real-flight request regardless of ground state - only the boost
        // below is conditional, so a grounded double-tap doesn't launch the player oddly, but it
        // also never grants actual creative-style flight.
        event.setCancelled(true);
        player.setAllowFlight(true);
        if (player.isOnGround()) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = (long) (plugin.getConfig().getDouble("double-jump.cooldown-seconds", 1.0) * 1000);
        Long last = lastJumpMillis.get(player.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        lastJumpMillis.put(player.getUniqueId(), now);

        double velocity = plugin.getConfig().getDouble("double-jump.velocity", 0.8);
        Vector boost = player.getLocation().getDirection().setY(0).normalize().multiply(velocity * 0.5);
        boost.setY(velocity);
        player.setVelocity(boost);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.4f);
    }
}
