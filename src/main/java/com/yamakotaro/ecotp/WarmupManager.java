package com.yamakotaro.ecotp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ワープ実行前の待機時間 (詠唱時間)。移動やダメージでキャンセルできるようにして、
 * 戦闘中に料金を払って一瞬で逃げる、といった悪用を防ぐ。
 * 支払いはこの待機が完了してから行われるため、キャンセルされても課金されない。
 */
public class WarmupManager implements Listener {

    private final EcoTpPlugin plugin;
    private final Map<UUID, Warmup> warmups = new HashMap<>();

    public WarmupManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Warmup {
        final Location startLocation;
        final BukkitTask task;

        private Warmup(Location startLocation, BukkitTask task) {
            this.startLocation = startLocation;
            this.task = task;
        }
    }

    /**
     * config で無効化されているか秒数が0なら即座に onComplete を実行する。
     * それ以外は待機メッセージを出し、待機完了後に onComplete を実行する
     * (移動/ダメージでキャンセルされた場合は onComplete を実行しない)。
     */
    public void start(Player player, String description, Runnable onComplete) {
        if (!plugin.getConfig().getBoolean("warmup.enabled", true)) {
            onComplete.run();
            return;
        }
        int seconds = Math.max(0, plugin.getConfig().getInt("warmup.seconds", 3));
        if (seconds == 0) {
            onComplete.run();
            return;
        }

        UUID uuid = player.getUniqueId();
        cancelSilently(uuid);

        player.sendMessage(plugin.msg("warmup.countdown", "description", description, "seconds", seconds));

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            warmups.remove(uuid);
            onComplete.run();
        }, seconds * 20L);

        warmups.put(uuid, new Warmup(player.getLocation(), task));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("warmup.cancel-on-move", true)) {
            return;
        }
        Warmup warmup = warmups.get(event.getPlayer().getUniqueId());
        if (warmup == null) {
            return;
        }
        Location from = warmup.startLocation;
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // ワールドが違う、または 0.2 ブロック以上動いたらキャンセル (視点移動だけでは反応しない)。
        if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > 0.04) {
            cancel(event.getPlayer(), "warmup.cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("warmup.cancel-on-damage", true)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (warmups.containsKey(player.getUniqueId())) {
            cancel(player, "warmup.cancelled-damage");
        }
    }

    private void cancel(Player player, String messageKey) {
        Warmup warmup = warmups.remove(player.getUniqueId());
        if (warmup != null) {
            warmup.task.cancel();
            player.sendMessage(plugin.msg(messageKey));
        }
    }

    public void cancelSilently(UUID uuid) {
        Warmup warmup = warmups.remove(uuid);
        if (warmup != null) {
            warmup.task.cancel();
        }
    }
}
