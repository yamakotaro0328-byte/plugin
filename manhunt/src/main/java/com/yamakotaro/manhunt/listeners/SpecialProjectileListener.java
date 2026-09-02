package com.yamakotaro.manhunt.listeners;

import com.yamakotaro.manhunt.Messages;
import com.yamakotaro.manhunt.game.GameManager;
import com.yamakotaro.manhunt.game.ManhuntGame;
import com.yamakotaro.manhunt.game.Role;
import com.yamakotaro.manhunt.items.SpecialItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the thrown craftable items: the hunter's Tracking Dart and Snare Trap, and the runner's
 * Flashbang. All are plain snowballs so the vanilla throw works unmodified - which one was thrown
 * is tracked by correlating the throw's PlayerInteractEvent with the ProjectileLaunchEvent that
 * immediately follows it, then tagging the spawned entity's own PersistentDataContainer so the
 * tag survives until it lands. Same interact-then-create correlation EcoRail's
 * CartAutoManageListener uses for cart ownership.
 */
public class SpecialProjectileListener implements Listener {

    private enum ThrownItem { TRACKING_DART, FLASHBANG, SNARE_TRAP }

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final SpecialItems specialItems;
    private final Messages messages;
    private final NamespacedKey trackingDartProjectileKey;
    private final NamespacedKey flashbangProjectileKey;
    private final NamespacedKey snareTrapProjectileKey;
    private final Map<UUID, ThrownItem> pendingThrows = new HashMap<>();

    public SpecialProjectileListener(JavaPlugin plugin, GameManager gameManager, SpecialItems specialItems, Messages messages) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.specialItems = specialItems;
        this.messages = messages;
        this.trackingDartProjectileKey = new NamespacedKey(plugin, "thrown_tracking_dart");
        this.flashbangProjectileKey = new NamespacedKey(plugin, "thrown_flashbang");
        this.snareTrapProjectileKey = new NamespacedKey(plugin, "thrown_snare_trap");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        ThrownItem thrown;
        if (specialItems.isTrackingDart(item)) {
            thrown = ThrownItem.TRACKING_DART;
        } else if (specialItems.isFlashbang(item)) {
            thrown = ThrownItem.FLASHBANG;
        } else if (specialItems.isSnareTrap(item)) {
            thrown = ThrownItem.SNARE_TRAP;
        } else {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        pendingThrows.put(playerId, thrown);
        // Safety net in case no projectile actually launches this tick (e.g. the interact did
        // something else instead) - avoids misattributing a later, unrelated throw.
        Bukkit.getScheduler().runTask(plugin, () -> pendingThrows.remove(playerId));
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        ThrownItem thrown = pendingThrows.remove(shooter.getUniqueId());
        if (thrown == null) {
            return;
        }
        NamespacedKey key = switch (thrown) {
            case TRACKING_DART -> trackingDartProjectileKey;
            case FLASHBANG -> flashbangProjectileKey;
            case SNARE_TRAP -> snareTrapProjectileKey;
        };
        projectile.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile projectile)) {
            return;
        }
        if (projectile.getPersistentDataContainer().has(trackingDartProjectileKey, PersistentDataType.BOOLEAN)) {
            handleTrackingDartHit(projectile, event);
        } else if (projectile.getPersistentDataContainer().has(flashbangProjectileKey, PersistentDataType.BOOLEAN)) {
            handleFlashbangHit(projectile);
        } else if (projectile.getPersistentDataContainer().has(snareTrapProjectileKey, PersistentDataType.BOOLEAN)) {
            handleSnareTrapHit(projectile, event);
        }
    }

    private void handleTrackingDartHit(Projectile projectile, ProjectileHitEvent event) {
        ManhuntGame game = gameManager.game();
        if (!game.isRunning() || !(projectile.getShooter() instanceof Player shooter)
                || game.getRole(shooter.getUniqueId()) != Role.HUNTER) {
            return;
        }
        if (!(event.getHitEntity() instanceof Player target) || game.getRole(target.getUniqueId()) != Role.RUNNER) {
            return;
        }
        long glowSeconds = plugin.getConfig().getLong("items.tracking-dart-glow-seconds", 15);
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (glowSeconds * 20), 0));
        shooter.sendMessage(messages.get("item.tracking-dart-hit", Map.of("player", target.getName())));
        target.sendMessage(messages.get("item.tracking-dart-hit-notice", Map.of()));
    }

    private void handleFlashbangHit(Projectile projectile) {
        ManhuntGame game = gameManager.game();
        if (!game.isRunning() || !(projectile.getShooter() instanceof Player shooter)
                || game.getRole(shooter.getUniqueId()) != Role.RUNNER) {
            return;
        }
        Location impact = projectile.getLocation();
        double radius = plugin.getConfig().getDouble("items.flashbang-radius", 6);
        long blindSeconds = plugin.getConfig().getLong("items.flashbang-blind-seconds", 5);
        int durationTicks = (int) (blindSeconds * 20);
        for (Player hunter : game.onlineHunters()) {
            if (!hunter.getWorld().equals(impact.getWorld())
                    || hunter.getLocation().distanceSquared(impact) > radius * radius) {
                continue;
            }
            hunter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 0));
            hunter.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 1));
            hunter.sendMessage(messages.get("item.flashbang-hit", Map.of()));
        }
        shooter.sendMessage(messages.get("item.flashbang-used", Map.of()));
    }

    private void handleSnareTrapHit(Projectile projectile, ProjectileHitEvent event) {
        ManhuntGame game = gameManager.game();
        if (!game.isRunning() || !(projectile.getShooter() instanceof Player shooter)
                || game.getRole(shooter.getUniqueId()) != Role.HUNTER) {
            return;
        }
        if (!(event.getHitEntity() instanceof Player target) || game.getRole(target.getUniqueId()) != Role.RUNNER) {
            return;
        }
        long durationSeconds = plugin.getConfig().getLong("items.snare-trap-duration-seconds", 6);
        int durationTicks = (int) (durationSeconds * 20);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, durationTicks, 0));
        shooter.sendMessage(messages.get("item.snare-hit", Map.of("player", target.getName())));
        target.sendMessage(messages.get("item.snare-hit-notice", Map.of()));
    }
}
