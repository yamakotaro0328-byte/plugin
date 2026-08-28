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
 * 料金の引き落としは、送信者が支払いに同意した後・相手が承諾した時点で行う
 * (相手が拒否/タイムアウトした場合は一切課金しない)。
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
        final double cost;
        final BukkitTask timeoutTask;

        private Request(UUID requesterUuid, String requesterName, double cost, BukkitTask timeoutTask) {
            this.requesterUuid = requesterUuid;
            this.requesterName = requesterName;
            this.cost = cost;
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
                target.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cテレポートリクエストがタイムアウトしました。"));
            }
            Player r = Bukkit.getPlayer(requester.getUniqueId());
            if (r != null) {
                r.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c" + target.getName() + " へのテレポートリクエストがタイムアウトしました。"));
            }
        }, timeoutSeconds * 20L);

        requests.put(targetUuid, new Request(requester.getUniqueId(), requester.getName(), cost, task));

        requester.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + target.getName() + " にテレポートリクエストを送信しました。相手の承諾をお待ちください。"));
        ChatUtil.sendTpaRequestPrompt(target, plugin.getPrefix(), requester.getName(), cost);
    }

    public void acceptRequest(Player target) {
        Request req = requests.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c承諾できるテレポートリクエストはありません。"));
            return;
        }
        req.timeoutTask.cancel();

        Player requester = Bukkit.getPlayer(req.requesterUuid);
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cリクエストを送った " + req.requesterName + " はオフラインになりました。"));
            return;
        }

        // リクエスト送信時点の cost は見積もりに過ぎない。承諾された瞬間の実際の距離で
        // 再計算してから請求する (送信後に相手が移動して料金を騙し取られるのを防ぐ)。
        double perBlock = plugin.getConfig().getDouble("costs.tpa-per-block", 1.0);
        double crossWorldFlatCost = plugin.getConfig().getDouble("costs.cross-world-flat-cost", 500.0);
        double actualCost = CostUtil.distanceCost(requester.getLocation(), target.getLocation(), perBlock, crossWorldFlatCost);

        Economy economy = plugin.getEconomy();
        if (!economy.has(requester, actualCost)) {
            String msg = plugin.getPrefix() + "&c" + req.requesterName + " の所持金が不足しているためテレポートできませんでした。(必要: " + ChatUtil.formatMoney(actualCost) + ")";
            target.sendMessage(ChatUtil.color(msg));
            requester.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しているため " + target.getName() + " へテレポートできませんでした。(必要: " + ChatUtil.formatMoney(actualCost) + ")"));
            return;
        }

        economy.withdrawPlayer(requester, actualCost);
        requester.teleport(target.getLocation());

        requester.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + target.getName() + " へテレポートしました。(" + ChatUtil.formatMoney(actualCost) + " 支払いました)"));
        target.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + req.requesterName + " のテレポートリクエストを承諾しました。"));
    }

    public void denyRequest(Player target) {
        Request req = requests.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c拒否できるテレポートリクエストはありません。"));
            return;
        }
        req.timeoutTask.cancel();

        target.sendMessage(ChatUtil.color(plugin.getPrefix() + "&eテレポートリクエストを拒否しました。"));
        Player requester = Bukkit.getPlayer(req.requesterUuid);
        if (requester != null) {
            requester.sendMessage(ChatUtil.color(plugin.getPrefix() + "&e" + target.getName() + " にテレポートリクエストを拒否されました。"));
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
