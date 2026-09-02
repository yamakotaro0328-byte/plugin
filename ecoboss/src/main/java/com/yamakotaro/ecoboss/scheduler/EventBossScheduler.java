package com.yamakotaro.ecoboss.scheduler;

import com.yamakotaro.ecoboss.Messages;
import com.yamakotaro.ecoboss.boss.BossDefinition;
import com.yamakotaro.ecoboss.boss.BossManager;
import com.yamakotaro.ecoboss.boss.BossType;
import com.yamakotaro.ecoboss.location.Point;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Spawns every EVENT-type boss once per its configured day-of-week + hour, at a registered spawn point. */
public class EventBossScheduler {

    private final JavaPlugin plugin;
    private final BossManager bossManager;
    private final Messages messages;
    private final Random random = new Random();
    private final Map<String, Long> lastFiredEpochHourByBossId = new HashMap<>();

    public EventBossScheduler(JavaPlugin plugin, BossManager bossManager, Messages messages) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.messages = messages;
    }

    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        long currentEpochHour = now.toEpochSecond(ZoneOffset.UTC) / 3600L;

        for (BossDefinition definition : bossManager.all()) {
            if (definition.type() != BossType.EVENT || definition.eventDayOfWeek() == null) {
                continue;
            }
            if (now.getDayOfWeek() != definition.eventDayOfWeek() || now.getHour() != definition.eventHour()) {
                continue;
            }
            Long lastFired = lastFiredEpochHourByBossId.get(definition.id());
            if (lastFired != null && lastFired == currentEpochHour) {
                continue;
            }
            lastFiredEpochHourByBossId.put(definition.id(), currentEpochHour);
            if (bossManager.isActive(definition.id()) || bossManager.cooldownRemainingMinutes(definition.id()) > 0) {
                continue;
            }
            trySpawn(definition);
        }
    }

    private void trySpawn(BossDefinition definition) {
        List<Point> spawnPoints = bossManager.locations().getSpawnPoints(definition.id());
        if (spawnPoints.isEmpty()) {
            plugin.getLogger().warning("Event boss '" + definition.id() + "' has no spawn points set - skipping.");
            return;
        }
        Point point = spawnPoints.get(random.nextInt(spawnPoints.size()));
        Location location = point.toLocation();
        if (location == null) {
            plugin.getLogger().warning("Event boss '" + definition.id() + "': spawn point's world isn't loaded - skipping.");
            return;
        }
        if (bossManager.spawn(definition, location) == null) {
            Component message = messages.get("boss.spawn-announce", Map.of("boss", definition.displayName()));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }
    }
}
