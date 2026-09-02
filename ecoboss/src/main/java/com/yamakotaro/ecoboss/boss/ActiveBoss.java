package com.yamakotaro.ecoboss.boss;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A currently-alive boss instance: the spawned entity, its fight state, and its boss bar. */
public class ActiveBoss {

    private final BossDefinition definition;
    private final LivingEntity entity;
    private final BossBar bossBar;
    private final double maxHealthSnapshot;
    private final Map<UUID, Double> damageByPlayer = new HashMap<>();
    private int phaseIndex = 0;

    public ActiveBoss(BossDefinition definition, LivingEntity entity, BossBar bossBar, double maxHealthSnapshot) {
        this.definition = definition;
        this.entity = entity;
        this.bossBar = bossBar;
        this.maxHealthSnapshot = maxHealthSnapshot;
    }

    public BossDefinition definition() {
        return definition;
    }

    public LivingEntity entity() {
        return entity;
    }

    public BossBar bossBar() {
        return bossBar;
    }

    public double maxHealthSnapshot() {
        return maxHealthSnapshot;
    }

    public int phaseIndex() {
        return phaseIndex;
    }

    public void advancePhase() {
        phaseIndex++;
    }

    public void addDamage(UUID playerId, double amount) {
        damageByPlayer.merge(playerId, amount, Double::sum);
    }

    public Map<UUID, Double> damageByPlayer() {
        return damageByPlayer;
    }

    public double healthPercent() {
        if (maxHealthSnapshot <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, entity.getHealth() / maxHealthSnapshot));
    }
}
