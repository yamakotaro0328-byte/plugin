package com.yamakotaro.ecoboss.listeners;

import com.yamakotaro.ecoboss.boss.ActiveBoss;
import com.yamakotaro.ecoboss.boss.BossManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;

/** Attributes damage dealt to an active boss to the player who dealt it, then re-checks its phases/boss bar. */
public class BossDamageListener implements Listener {

    private final BossManager bossManager;

    public BossDamageListener(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Optional<ActiveBoss> activeOpt = bossManager.findActive(event.getEntity().getUniqueId());
        if (activeOpt.isEmpty()) {
            return;
        }
        ActiveBoss active = activeOpt.get();
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker != null) {
            active.addDamage(attacker.getUniqueId(), event.getFinalDamage());
        }
        bossManager.onDamaged(active);
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
