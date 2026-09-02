package com.yamakotaro.manhunt.listeners;

import com.yamakotaro.manhunt.Messages;
import com.yamakotaro.manhunt.game.GameManager;
import com.yamakotaro.manhunt.game.ManhuntGame;
import com.yamakotaro.manhunt.game.Role;
import com.yamakotaro.manhunt.items.SpecialItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;

/** Handles right-click activation of the hunter's Locator Orb and the runner's Smoke Bomb/Blink Shard. */
public class SpecialItemListener implements Listener {

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final SpecialItems specialItems;
    private final Messages messages;

    public SpecialItemListener(JavaPlugin plugin, GameManager gameManager, SpecialItems specialItems, Messages messages) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.specialItems = specialItems;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (specialItems.isLocatorOrb(item)) {
            event.setCancelled(true);
            useLocatorOrb(event.getPlayer());
        } else if (specialItems.isSmokeBomb(item)) {
            event.setCancelled(true);
            useSmokeBomb(event.getPlayer());
        } else if (specialItems.isBlinkShard(item)) {
            event.setCancelled(true);
            useBlinkShard(event.getPlayer());
        }
    }

    private void useLocatorOrb(Player hunter) {
        ManhuntGame game = gameManager.game();
        if (!game.isRunning() || game.getRole(hunter.getUniqueId()) != Role.HUNTER) {
            hunter.sendMessage(messages.get("item.not-usable-now", Map.of()));
            return;
        }
        Player nearest = nearestAliveRunner(game, hunter);
        if (nearest == null) {
            hunter.sendMessage(messages.get("item.locator-none-found", Map.of()));
            return;
        }
        consumeOne(hunter);
        hunter.sendMessage(messages.get("item.locator-used", Map.of(
                "player", nearest.getName(),
                "x", String.valueOf(nearest.getLocation().getBlockX()),
                "y", String.valueOf(nearest.getLocation().getBlockY()),
                "z", String.valueOf(nearest.getLocation().getBlockZ()))));
    }

    private void useSmokeBomb(Player runner) {
        ManhuntGame game = gameManager.game();
        if (!game.isRunning() || game.getRole(runner.getUniqueId()) != Role.RUNNER || game.isEliminated(runner.getUniqueId())) {
            runner.sendMessage(messages.get("item.not-usable-now", Map.of()));
            return;
        }
        consumeOne(runner);
        long durationSeconds = plugin.getConfig().getLong("items.smoke-bomb-duration-seconds", 8);
        int durationTicks = (int) (durationSeconds * 20);
        runner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0));
        runner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1));
        runner.sendMessage(messages.get("item.smoke-used", Map.of("seconds", String.valueOf(durationSeconds))));
    }

    private void useBlinkShard(Player runner) {
        ManhuntGame game = gameManager.game();
        if (!game.isRunning() || game.getRole(runner.getUniqueId()) != Role.RUNNER || game.isEliminated(runner.getUniqueId())) {
            runner.sendMessage(messages.get("item.not-usable-now", Map.of()));
            return;
        }
        double distance = plugin.getConfig().getDouble("items.blink-shard-distance", 8);
        Location origin = runner.getLocation();
        Location destination = origin.clone().add(origin.getDirection().normalize().multiply(distance));
        if (!isSafeToStandOn(destination)) {
            runner.sendMessage(messages.get("item.blink-blocked", Map.of()));
            return;
        }
        consumeOne(runner);
        runner.teleport(destination);
        runner.sendMessage(messages.get("item.blink-used", Map.of()));
    }

    private boolean isSafeToStandOn(Location location) {
        Material feet = location.getBlock().getType();
        Material head = location.clone().add(0, 1, 0).getBlock().getType();
        return !feet.isSolid() && !head.isSolid();
    }

    private Player nearestAliveRunner(ManhuntGame game, Player hunter) {
        Player nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Player runner : game.onlineAliveRunners()) {
            if (!runner.getWorld().equals(hunter.getWorld())) {
                continue;
            }
            double distanceSquared = runner.getLocation().distanceSquared(hunter.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = runner;
            }
        }
        return nearest;
    }

    private void consumeOne(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(inHand.getAmount() - 1);
        }
    }
}
