package com.yamakotaro.ecotp;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
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
 * /home・/spawn・/tpa・/tphere でテレポートする前に、次をすべて満たすことを確認する。
 * 1. 設定範囲内に敵対Mobがいない
 * 2. PvPクールダウン中ではない
 * 3. 詠唱時間 (デフォルト5秒) の間、開始地点から動かない (視点移動だけでは解除されない)
 * 4. テレポート先と同じディメンションにいる
 * 待機中にモブ出現・PvP・移動・ダメージのいずれかを検知した場合はキャンセルする。
 * 支払いは詠唱完了後に呼び出し側が行うため、キャンセルされた場合は課金されない。
 */
public class TeleportSafetyManager implements Listener {

    private final EcoTpPlugin plugin;
    private final CombatTracker combatTracker;
    private final Map<UUID, Countdown> countdowns = new HashMap<>();

    public TeleportSafetyManager(EcoTpPlugin plugin, CombatTracker combatTracker) {
        this.plugin = plugin;
        this.combatTracker = combatTracker;
    }

    private static final class Countdown {
        final Location startLocation;
        final BukkitTask completionTask;
        final BukkitTask monitorTask;

        private Countdown(Location startLocation, BukkitTask completionTask, BukkitTask monitorTask) {
            this.startLocation = startLocation;
            this.completionTask = completionTask;
            this.monitorTask = monitorTask;
        }
    }

    public boolean isSameDimension(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }

    public boolean hasHostileMobNearby(Player player) {
        double radius = plugin.getConfig().getDouble("teleport-safety.hostile-mob-radius", 10.0);
        if (radius <= 0) {
            return false;
        }
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (isHostile(entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHostile(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return entity instanceof Monster
                || entity instanceof Slime
                || entity instanceof Phantom
                || entity instanceof Ghast
                || entity instanceof Shulker;
    }

    public boolean isInPvpCooldown(Player player) {
        return combatTracker.isInCooldown(player.getUniqueId());
    }

    /**
     * テレポートを開始できるかその場で確認し、可能なら詠唱を開始する。
     *
     * @param player      実際に移動する (安全確認の対象になる) プレイヤー
     * @param destination 目的地。同ディメンションか確認するために使う (不明な場合は null で省略可)
     * @param description 詠唱中に表示する説明文
     * @param onComplete  詠唱が正常に完了したときに実行する処理 (支払い + 実テレポートはここで行う)
     * @return 詠唱を開始できた場合 true。安全条件を満たさず即座に拒否した場合 false
     * (このときは呼び出し側に代わって理由をプレイヤーへ通知済み)
     */
    public boolean start(Player player, Location destination, String description, Runnable onComplete) {
        if (destination != null && !isSameDimension(player.getLocation(), destination)) {
            player.sendMessage(plugin.msg("teleport-safety.wrong-dimension"));
            return false;
        }
        if (hasHostileMobNearby(player)) {
            player.sendMessage(plugin.msg("teleport-safety.hostile-mob-nearby"));
            return false;
        }
        if (isInPvpCooldown(player)) {
            player.sendMessage(plugin.msg("teleport-safety.pvp-cooldown"));
            return false;
        }

        int seconds = Math.max(0, plugin.getConfig().getInt("teleport-safety.countdown-seconds", 5));
        if (seconds == 0) {
            onComplete.run();
            return true;
        }

        UUID uuid = player.getUniqueId();
        cancelSilently(uuid);

        player.sendMessage(plugin.msg("teleport-safety.countdown", "description", description, "seconds", seconds));

        BukkitTask completionTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // monitorTask はこの時点でまだローカル変数として代入されていないため、
            // 直接参照せず countdowns マップ経由で両方のタスクを止める。
            Countdown finished = countdowns.remove(uuid);
            if (finished != null) {
                finished.monitorTask.cancel();
            }
            onComplete.run();
        }, seconds * 20L);

        // 移動/ダメージはイベントで即検知できるが、モブの接近やPvPクールダウンの発生は
        // イベントが無いため、待機中は毎秒ポーリングして確認する。
        BukkitTask monitorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelSilently(uuid);
                return;
            }
            if (hasHostileMobNearby(player)) {
                cancel(player, "teleport-safety.cancelled-mob");
            } else if (isInPvpCooldown(player)) {
                cancel(player, "teleport-safety.cancelled-pvp");
            }
        }, 20L, 20L);

        countdowns.put(uuid, new Countdown(player.getLocation(), completionTask, monitorTask));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Countdown countdown = countdowns.get(event.getPlayer().getUniqueId());
        if (countdown == null) {
            return;
        }
        Location from = countdown.startLocation;
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        double tolerance = plugin.getConfig().getDouble("teleport-safety.movement-tolerance", 0.2);
        // ワールドが違う、または許容範囲より動いたらキャンセル (視点移動だけでは反応しない)。
        if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > tolerance * tolerance) {
            cancel(event.getPlayer(), "teleport-safety.cancelled-move");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (countdowns.containsKey(player.getUniqueId())) {
            cancel(player, "teleport-safety.cancelled-damage");
        }
    }

    private void cancel(Player player, String messageKey) {
        Countdown countdown = countdowns.remove(player.getUniqueId());
        if (countdown != null) {
            countdown.completionTask.cancel();
            countdown.monitorTask.cancel();
            player.sendMessage(plugin.msg(messageKey));
        }
    }

    public void cancelSilently(UUID uuid) {
        Countdown countdown = countdowns.remove(uuid);
        if (countdown != null) {
            countdown.completionTask.cancel();
            countdown.monitorTask.cancel();
        }
    }
}
