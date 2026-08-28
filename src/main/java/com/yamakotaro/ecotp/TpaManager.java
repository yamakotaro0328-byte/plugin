package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /tpa のリクエスト (相手の承諾待ち) を管理する。
 * 料金の引き落としは、送信者が支払いに同意した後・相手が承諾し、詠唱時間を経てから行う
 * (相手が拒否/タイムアウトした場合、または詠唱中にキャンセルされた場合は一切課金しない)。
 */
public class TpaManager {

    private final EcoTpPlugin plugin;
    private final Map<UUID, Request> requests = new HashMap<>();

    public TpaManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Request {
        final UUID requesterUuid;
        final String requesterName;
        final BukkitTask timeoutTask;

        private Request(UUID requesterUuid, String requesterName, BukkitTask timeoutTask) {
            this.requesterUuid = requesterUuid;
            this.requesterName = requesterName;
            this.timeoutTask = timeoutTask;
        }
    }

    public void sendRequest(Player requester, Player target, double cost) {
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

        requests.put(targetUuid, new Request(requester.getUniqueId(), requester.getName(), task));

        requester.sendMessage(plugin.msg("tpa.sent", "player", target.getName()));
        ChatUtil.sendTpaRequestPrompt(target, plugin.getPrefix(), requester.getName(), cost);
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

        target.sendMessage(plugin.msg("tpa.accepted-target", "player", req.requesterName));

        String description = plugin.getMessages().get("tp.description", "player", target.getName());
        plugin.getWarmupManager().start(requester, description, () -> {
            if (!requester.isOnline() || !target.isOnline()) {
                return;
            }
            // リクエスト送信時点の cost は見積もりに過ぎない。実際にテレポートする直前の
            // 距離で再計算してから請求する (送信後に相手が移動して料金を騙し取られるのを防ぐ)。
            double perBlock = plugin.getConfig().getDouble("costs.tpa-per-block", 1.0);
            double crossWorldFlatCost = plugin.getConfig().getDouble("costs.cross-world-flat-cost", 500.0);
            double actualCost = CostUtil.distanceCost(requester.getLocation(), target.getLocation(), perBlock, crossWorldFlatCost);

            Economy economy = plugin.getEconomy();
            if (!economy.has(requester, actualCost)) {
                target.sendMessage(plugin.msg("tpa.insufficient-funds-target", "player", req.requesterName, "cost", ChatUtil.formatMoney(actualCost)));
                requester.sendMessage(plugin.msg("tpa.insufficient-funds-requester", "player", target.getName(), "cost", ChatUtil.formatMoney(actualCost)));
                return;
            }

            economy.withdrawPlayer(requester, actualCost);
            requester.teleport(target.getLocation());
            requester.sendMessage(plugin.msg("tpa.accepted-requester", "player", target.getName(), "cost", ChatUtil.formatMoney(actualCost)));
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
