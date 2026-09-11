package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A pure spectacle toy with no PvP angle whatsoever (replaces the earlier snowball-launcher,
 * which could still be read as knocking other players around): right-clicking the marked wand
 * launches a firework a couple of blocks in front of the player that explodes shortly after with
 * a random color/shape. Nothing about it can touch another player or entity at all.
 */
public class FireworkWandListener implements Listener {

    private static final List<Color> COLORS = List.of(
            Color.AQUA, Color.RED, Color.LIME, Color.FUCHSIA, Color.YELLOW, Color.ORANGE);
    private static final List<FireworkEffect.Type> TYPES = List.of(
            FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.STAR, FireworkEffect.Type.BURST);

    private final EcoLobbyPlugin plugin;
    private final Map<UUID, Long> lastUseMillis = new HashMap<>();

    public FireworkWandListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.isFeatureEnabled("firework-wand")) {
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
        if (!Boolean.TRUE.equals(meta.getPersistentDataContainer().get(plugin.getFireworkWandItemKey(), PersistentDataType.BOOLEAN))) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long cooldownMillis = (long) (plugin.getConfig().getDouble("firework-wand.cooldown-seconds", 1.0) * 1000);
        Long last = lastUseMillis.get(player.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        lastUseMillis.put(player.getUniqueId(), now);

        Location location = player.getEyeLocation()
                .add(player.getLocation().getDirection().multiply(2))
                .add(0, 1, 0);
        Firework firework = player.getWorld().spawn(location, Firework.class);
        FireworkMeta fireworkMeta = firework.getFireworkMeta();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        fireworkMeta.addEffect(FireworkEffect.builder()
                .withColor(COLORS.get(random.nextInt(COLORS.size())))
                .withFade(COLORS.get(random.nextInt(COLORS.size())))
                .with(TYPES.get(random.nextInt(TYPES.size())))
                .trail(true)
                .flicker(true)
                .build());
        fireworkMeta.setPower(1);
        firework.setFireworkMeta(fireworkMeta);
    }
}
