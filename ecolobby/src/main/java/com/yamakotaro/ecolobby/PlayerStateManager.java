package com.yamakotaro.ecolobby;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory (not persisted across restarts - purely lobby-session preferences) per-player toggles
 * used by the hotbar/menu items: hide-other-players, personal double-jump on/off, a night vision
 * QoL toggle, and an equipped particle trail. Centralized here so DoubleJumpListener and the menu
 * items agree on the same state.
 */
public class PlayerStateManager {

    private final Plugin plugin;
    private final Set<UUID> hidden = new HashSet<>();
    private final Set<UUID> doubleJumpDisabled = new HashSet<>();
    private final Set<UUID> nightVisionEnabled = new HashSet<>();
    private final Map<UUID, Particle> activeTrail = new HashMap<>();

    public PlayerStateManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean toggleVisibility(Player player) {
        UUID uuid = player.getUniqueId();
        if (hidden.remove(uuid)) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(plugin, player);
            }
            return false;
        }
        hidden.add(uuid);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        }
        return true;
    }

    /** Re-applies every currently-hidden player's hidden state against a freshly joined viewer. */
    public void applyVisibilityTo(Player viewer) {
        for (UUID uuid : hidden) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null && !target.equals(viewer)) {
                viewer.hidePlayer(plugin, target);
            }
        }
    }

    public void clearOnQuit(UUID uuid) {
        hidden.remove(uuid);
        doubleJumpDisabled.remove(uuid);
        nightVisionEnabled.remove(uuid);
        activeTrail.remove(uuid);
    }

    public boolean isDoubleJumpDisabled(UUID uuid) {
        return doubleJumpDisabled.contains(uuid);
    }

    public boolean toggleDoubleJump(UUID uuid) {
        if (doubleJumpDisabled.remove(uuid)) {
            return true;
        }
        doubleJumpDisabled.add(uuid);
        return false;
    }

    public boolean toggleNightVision(Player player) {
        UUID uuid = player.getUniqueId();
        if (nightVisionEnabled.remove(uuid)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            return false;
        }
        nightVisionEnabled.add(uuid);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION, 0, false, false));
        return true;
    }

    public Particle getTrail(UUID uuid) {
        return activeTrail.get(uuid);
    }

    public void setTrail(UUID uuid, Particle particle) {
        if (particle == null) {
            activeTrail.remove(uuid);
        } else {
            activeTrail.put(uuid, particle);
        }
    }
}
