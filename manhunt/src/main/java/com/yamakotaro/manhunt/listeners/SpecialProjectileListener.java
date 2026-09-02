package com.yamakotaro.manhunt.listeners;

import com.yamakotaro.manhunt.Messages;
import com.yamakotaro.manhunt.game.GameManager;
import com.yamakotaro.manhunt.game.ManhuntGame;
import com.yamakotaro.manhunt.game.Role;
import com.yamakotaro.manhunt.items.SpecialItems;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrowableItemProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/** Handles the thrown craftable items landing: the hunter's Tracking Dart and the runner's Flashbang. */
public class SpecialProjectileListener implements Listener {

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final SpecialItems specialItems;
    private final Messages messages;

    public SpecialProjectileListener(JavaPlugin plugin, GameManager gameManager, SpecialItems specialItems, Messages messages) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.specialItems = specialItems;
        this.messages = messages;
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ThrowableItemProjectile projectile)) {
            return;
        }
        ItemStack usedItem = projectile.getItem();
        if (specialItems.isTrackingDart(usedItem)) {
            handleTrackingDartHit(projectile, event);
        } else if (specialItems.isFlashbang(usedItem)) {
            handleFlashbangHit(projectile);
        }
    }

    private void handleTrackingDartHit(ThrowableItemProjectile projectile, ProjectileHitEvent event) {
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

    private void handleFlashbangHit(ThrowableItemProjectile projectile) {
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
}
