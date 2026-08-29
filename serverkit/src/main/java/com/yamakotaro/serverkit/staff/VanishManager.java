package com.yamakotaro.serverkit.staff;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Vanish = hidden from every online player that doesn't hold serverkit.staff.vanish (so other
 * staff can still see each other) + a permanent, particle-free invisibility effect so vanilla
 * mobs stop targeting the vanished player too.
 */
public class VanishManager {

    private final Plugin plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public boolean toggle(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            show(player);
            return false;
        }
        vanished.add(player.getUniqueId());
        hide(player);
        return true;
    }

    private void hide(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, false, false));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player) || viewer.hasPermission("serverkit.staff.vanish")) {
                continue;
            }
            viewer.hidePlayer(plugin, player);
        }
    }

    private void show(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, player);
        }
    }

    /** Applies the current vanish state of every already-vanished player against a freshly joined viewer. */
    public void applyTo(Player viewer) {
        if (viewer.hasPermission("serverkit.staff.vanish")) {
            return;
        }
        for (UUID uuid : vanished) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null && !target.equals(viewer)) {
                viewer.hidePlayer(plugin, target);
            }
        }
    }

    public void clear(UUID uuid) {
        vanished.remove(uuid);
    }
}
