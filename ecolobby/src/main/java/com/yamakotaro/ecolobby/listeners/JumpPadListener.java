package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns every pressure plate into a jump pad: stepping on one launches the player forward+upward
 * instead of just triggering the vanilla redstone pulse. Detected by material name suffix rather
 * than a Bukkit Tag constant (matches every wood/stone/weighted pressure plate variant without
 * depending on a specific Tag API shape that may differ across Paper versions).
 */
public class JumpPadListener implements Listener {

    private final EcoLobbyPlugin plugin;
    private final Map<UUID, Long> lastLaunchMillis = new HashMap<>();

    public JumpPadListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onStep(PlayerInteractEvent event) {
        if (!plugin.isFeatureEnabled("jump-pads")) {
            return;
        }
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) {
            return;
        }
        if (!event.getClickedBlock().getType().name().endsWith("_PRESSURE_PLATE")) {
            return;
        }

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long cooldownMillis = (long) (plugin.getConfig().getDouble("jump-pad.cooldown-seconds", 0.3) * 1000);
        Long last = lastLaunchMillis.get(player.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        lastLaunchMillis.put(player.getUniqueId(), now);

        double velocity = plugin.getConfig().getDouble("jump-pad.velocity", 0.9);
        Vector boost = player.getLocation().getDirection().setY(0).normalize().multiply(velocity * 0.6);
        boost.setY(velocity);
        player.setVelocity(boost);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.7f, 1.2f);
    }
}
