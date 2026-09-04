package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Handles explorer: pays out every time a joined player reaches a new farthest (horizontal-only)
 * distance from their current world's spawn point. Only does any work at all for players who
 * have actually joined explorer, and only re-checks on a block-level horizontal move (not on
 * every look-around/jump, which would otherwise fire this on nearly every tick).
 */
public class ExplorerListener implements Listener {

    private final PlayerJobManager jobs;

    public ExplorerListener(PlayerJobManager jobs) {
        this.jobs = jobs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // PlayerTeleportEvent は PlayerMoveEvent のサブクラスなので、何もしないと /home や /spawn で
        // 一気に数千ブロック移動した分までマイルストーン報酬が出てしまう(テレポート1回で数千円)。
        // 「自分で歩いて到達した距離」を評価する機能なので、テレポート由来は数えない。
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        // 滑空・乗り物での移動も数えない。エリトラで飛び続けるだけで250ブロックごとに報酬が
        // ワールド境界まで永久に入り続けてしまうため。
        if (player.isGliding() || player.isInsideVehicle()) {
            return;
        }
        if (!jobs.isJoined(player.getUniqueId(), "explorer")) {
            return;
        }
        Location spawn = player.getWorld().getSpawnLocation();
        double dx = to.getX() - spawn.getX();
        double dz = to.getZ() - spawn.getZ();
        jobs.checkExplorerMilestones(player, player.getWorld().getName(), Math.sqrt(dx * dx + dz * dz));
    }
}
