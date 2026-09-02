package com.yamakotaro.ecoboss.listeners;

import com.yamakotaro.ecoboss.Messages;
import com.yamakotaro.ecoboss.boss.BossDefinition;
import com.yamakotaro.ecoboss.boss.BossManager;
import com.yamakotaro.ecoboss.boss.BossType;
import com.yamakotaro.ecoboss.location.Box;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

/** Spawns a DUNGEON boss the moment a player walks into its registered trigger region. */
public class DungeonTriggerListener implements Listener {

    private final BossManager bossManager;
    private final Messages messages;

    public DungeonTriggerListener(BossManager bossManager, Messages messages) {
        this.bossManager = bossManager;
        this.messages = messages;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Location to = event.getTo();
        for (BossDefinition definition : bossManager.all()) {
            if (definition.type() != BossType.DUNGEON) {
                continue;
            }
            Box region = bossManager.locations().getRegion(definition.id());
            if (region == null || bossManager.isActive(definition.id()) || bossManager.cooldownRemainingMinutes(definition.id()) > 0) {
                continue;
            }
            if (!region.contains(to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ())) {
                continue;
            }
            Location spawnLocation = region.center().toLocation();
            if (spawnLocation != null && bossManager.spawn(definition, spawnLocation) == null) {
                Component message = messages.get("boss.dungeon-awaken", Map.of("boss", definition.displayName()));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(message);
                }
            }
        }
    }
}
