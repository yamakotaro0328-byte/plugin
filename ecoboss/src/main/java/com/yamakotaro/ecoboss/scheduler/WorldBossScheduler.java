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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Periodically spawns every WORLD-type boss at one of its registered spawn points, with a lead-time warning first. */
public class WorldBossScheduler {

    private final JavaPlugin plugin;
    private final BossManager bossManager;
    private final Messages messages;
    private final Random random = new Random();
    private final Map<String, Long> nextSpawnAtMillisByBossId = new HashMap<>();
    private final Map<String, Boolean> warnedByBossId = new HashMap<>();

    public WorldBossScheduler(JavaPlugin plugin, BossManager bossManager, Messages messages) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.messages = messages;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long leadTimeMillis = plugin.getConfig().getLong("announcement-lead-time-seconds", 30) * 1000L;

        for (BossDefinition definition : bossManager.all()) {
            if (definition.type() != BossType.WORLD) {
                continue;
            }
            if (bossManager.isActive(definition.id()) || bossManager.cooldownRemainingMinutes(definition.id()) > 0) {
                continue;
            }
            long nextSpawnAt = nextSpawnAtMillisByBossId.computeIfAbsent(definition.id(),
                    id -> now + definition.worldIntervalMinutes() * 60_000L);

            if (!warnedByBossId.getOrDefault(definition.id(), false) && now >= nextSpawnAt - leadTimeMillis) {
                broadcast("boss.warning-announce", Map.of("boss", definition.displayName(), "seconds", String.valueOf(leadTimeMillis / 1000L)));
                warnedByBossId.put(definition.id(), true);
            }

            if (now >= nextSpawnAt) {
                trySpawn(definition);
                nextSpawnAtMillisByBossId.put(definition.id(), now + definition.worldIntervalMinutes() * 60_000L);
                warnedByBossId.put(definition.id(), false);
            }
        }
    }

    private void trySpawn(BossDefinition definition) {
        List<Point> spawnPoints = bossManager.locations().getSpawnPoints(definition.id());
        if (spawnPoints.isEmpty()) {
            plugin.getLogger().warning("World boss '" + definition.id() + "' has no spawn points set - skipping.");
            return;
        }
        Point point = spawnPoints.get(random.nextInt(spawnPoints.size()));
        Location location = point.toLocation();
        if (location == null) {
            plugin.getLogger().warning("World boss '" + definition.id() + "': spawn point's world isn't loaded - skipping.");
            return;
        }
        if (bossManager.spawn(definition, location) == null) {
            broadcast("boss.spawn-announce", Map.of("boss", definition.displayName()));
        }
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        Component message = messages.get(key, placeholders);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }
}
