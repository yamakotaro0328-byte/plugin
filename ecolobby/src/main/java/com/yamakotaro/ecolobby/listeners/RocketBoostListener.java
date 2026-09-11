package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Right-clicking the marked firework rocket launches the player forward+upward (on a cooldown) -
 * a reusable boost pad you carry with you, same velocity-boost technique as DoubleJumpListener
 * and JumpPadListener. The item is never consumed - the event is cancelled before vanilla
 * firework-use logic runs, so there's nothing to run out of.
 */
public class RocketBoostListener implements Listener {

    private final EcoLobbyPlugin plugin;
    private final Map<UUID, Long> lastBoostMillis = new HashMap<>();

    public RocketBoostListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.isFeatureEnabled("rocket-boost")) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!Boolean.TRUE.equals(meta.getPersistentDataContainer().get(plugin.getRocketBoostItemKey(), PersistentDataType.BOOLEAN))) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long cooldownMillis = (long) (plugin.getConfig().getDouble("rocket-boost.cooldown-seconds", 2.0) * 1000);
        Long last = lastBoostMillis.get(player.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        lastBoostMillis.put(player.getUniqueId(), now);

        double velocity = plugin.getConfig().getDouble("rocket-boost.velocity", 1.4);
        Vector boost = player.getLocation().getDirection().normalize().multiply(velocity);
        if (boost.getY() < velocity * 0.6) {
            boost.setY(velocity * 0.6);
        }
        player.setVelocity(boost);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 30, 0.2, 0.2, 0.2, 0.05);
    }
}
