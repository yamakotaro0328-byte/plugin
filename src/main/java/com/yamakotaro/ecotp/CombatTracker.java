package com.yamakotaro.ecotp;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PvP クールダウン (直近で対人戦闘をしたかどうか) を追跡する。
 * ダメージを与えた側/受けた側どちらもプレイヤーであれば両者にタグを付ける。
 */
public class CombatTracker implements Listener {

    private final EcoTpPlugin plugin;
    private final Map<UUID, Long> lastCombatMillis = new HashMap<>();

    public CombatTracker(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    // 保護エリアで止められた攻撃や、雪玉のような0ダメージの攻撃では PvP 扱いにしない
    // (でないと殴り続けるだけで相手のテレポートを妨害できた)。
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) {
            return;
        }
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = resolvePlayerAttacker(event);

        if (victim == null && attacker == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (victim != null && attacker != null) {
            // プレイヤー同士の戦闘のみタグを付ける (Mobに殴られただけでは PvP 扱いにしない)
            lastCombatMillis.put(victim.getUniqueId(), now);
            lastCombatMillis.put(attacker.getUniqueId(), now);
        }
    }

    private Player resolvePlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    public boolean isInCooldown(UUID uuid) {
        Long last = lastCombatMillis.get(uuid);
        if (last == null) {
            return false;
        }
        int cooldownSeconds = plugin.getConfig().getInt("teleport-safety.pvp-cooldown-seconds", 15);
        return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }

    public void clear(UUID uuid) {
        lastCombatMillis.remove(uuid);
    }
}
