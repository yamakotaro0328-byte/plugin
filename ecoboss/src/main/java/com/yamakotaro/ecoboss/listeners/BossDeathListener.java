package com.yamakotaro.ecoboss.listeners;

import com.yamakotaro.ecoboss.boss.ActiveBoss;
import com.yamakotaro.ecoboss.boss.BossManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;

/** Ends the encounter and hands out loot when a tracked boss entity dies, instead of dropping vanilla loot. */
public class BossDeathListener implements Listener {

    private final BossManager bossManager;

    public BossDeathListener(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Optional<ActiveBoss> activeOpt = bossManager.findActive(event.getEntity().getUniqueId());
        if (activeOpt.isEmpty()) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        bossManager.onDeath(activeOpt.get());
    }
}
