package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /tpa と /tphere のリクエスト (相手の承諾待ち) を管理する。
 * どちらも「実際に移動する側」が支払う。/tpa は要求した側 (requester) が移動して支払い、
 * /tphere は着払いのため、呼ばれた側 (target) が移動して支払う。
 * 料金の引き落としは、リクエストが承諾され、詠唱時間 (安全確認) を経てから行う
 * (拒否/タイムアウト/詠唱中のキャンセルの場合は一切課金しない)。
 */
public class TpaManager {

    public enum Type {
        TPA, TPHERE
    }

    private final EcoTpPlugin plugin;
    private final Map<UUID, Request> requests = new HashMap<>();

    public TpaManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Request {
        final Type type;
        final UUID requesterUuid;
        final String requesterName;
        final BukkitTask timeoutTask;

        private Request(Type type, UUID requesterUuid, String requesterName, BukkitTask timeoutTask) {
            this.type = type;
            this.requesterUuid = requesterUuid;
            this.requesterName = requesterName;
            this.timeoutTask = timeoutTask;
        }
    }

    /**
     * @param cost 相手への表示用の見積もり額。実際の請求は承諾後、詠唱完了時点の距離で再計算する。
     */
    public void sendRequest(Type type, Player requester, Player target, double cost) {
        UUID targetUuid = target.getUniqueId();
        cancelSilently(targetUuid);

        int timeoutSeconds = plugin.getConfig().getInt("tpa-request-timeout-seconds", 60);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            requests.remove(targetUuid);
            if (target.isOnline()) {
                target.sendMessage(plugin.msg("tpa.timeout-target"));
            }
            Player r = Bukkit.getPlayer(requester.getUniqueId());
            if (r != null) {
                r.sendMessage(plugin.msg("tpa.timeout-requester", "player", target.getName()));
            }
        }, timeoutSeconds * 20L);

        requests.put(targetUuid, new Request(type, requester.getUniqueId(), requester.getName(), task));

        String sentKey = type == Type.TPA ? "tpa.sent" : "tphere.sent";
        String incomingKey = type == Type.TPA ? "tpa.incoming" : "tphere.incoming";
        requester.sendMessage(plugin.msg(sentKey, "player", target.getName()));
        ChatUtil.sendTpaRequestPrompt(target, plugin.getPrefix(), requester.getName(), cost, incomingKey);
    }

    public void acceptRequest(Player target) {
        Request req = requests.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(plugin.msg("tpa.none-to-accept"));
            return;
        }
        req.timeoutTask.cancel();

        Player requester = Bukkit.getPlayer(req.requesterUuid);
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(plugin.msg("tpa.requester-offline", "player", req.requesterName));
            return;
        }

        // TPA: requester が target のもとへ移動して支払う。TPHERE (着払い): target が
        // requester のもとへ移動して支払う。どちらも「移動する側が支払う」で共通。
        Player mover = req.type == Type.TPA ? requester : target;
        Player destinationPlayer = req.type == Type.TPA ? target : requester;

        target.sendMessage(plugin.msg("tpa.accepted-target", "player", req.requesterName));

        String description = plugin.getMessages().get("tpa.travel-description", "player", destinationPlayer.getName());
        plugin.getTeleportSafetyManager().start(mover, destinationPlayer.getLocation(), description, () -> {
            if (!mover.isOnline() || !destinationPlayer.isOnline()) {
                return;
            }
            Location destination = destinationPlayer.getLocation();
            if (!plugin.getTeleportSafetyManager().isSameDimension(mover.getLocation(), destination)) {
                mover.sendMessage(plugin.msg("teleport-safety.wrong-dimension"));
                return;
            }

            double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
            double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 100.0);
            double actualCost = CostUtil.distanceCost(mover.getLocation(), destination, minFee, blocksPerYen);

            Economy economy = plugin.getEconomy();
            if (!economy.has(mover, actualCost)) {
                mover.sendMessage(plugin.msg("tpa.insufficient-funds-requester", "player", destinationPlayer.getName(), "cost", ChatUtil.formatMoney(actualCost)));
                destinationPlayer.sendMessage(plugin.msg("tpa.insufficient-funds-target", "player", mover.getName(), "cost", ChatUtil.formatMoney(actualCost)));
                return;
            }

            economy.withdrawPlayer(mover, actualCost);
            mover.teleport(destination);
            mover.sendMessage(plugin.msg("tpa.accepted-requester", "player", destinationPlayer.getName(), "cost", ChatUtil.formatMoney(actualCost)));
        });
    }

    public void denyRequest(Player target) {
        Request req = requests.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(plugin.msg("tpa.none-to-deny"));
            return;
        }
        req.timeoutTask.cancel();

        target.sendMessage(plugin.msg("tpa.denied-target"));
        Player requester = Bukkit.getPlayer(req.requesterUuid);
        if (requester != null) {
            requester.sendMessage(plugin.msg("tpa.denied-requester", "player", target.getName()));
        }
    }

    /**
     * 退出時などに、対象プレイヤーが絡むリクエストを黙って消す (送信者・受信者どちらの場合も)。
     */
    public void cancelSilently(UUID uuid) {
        Request selfRequest = requests.remove(uuid);
        if (selfRequest != null) {
            selfRequest.timeoutTask.cancel();
        }
        requests.entrySet().removeIf(entry -> {
            if (entry.getValue().requesterUuid.equals(uuid)) {
                entry.getValue().timeoutTask.cancel();
                return true;
            }
            return false;
        });
    }
}
