package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** メニューGUIの通知表示用の、読み取り専用スナップショット。 */
    public record IncomingRequestInfo(Type type, String requesterName) {
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

    /**
     * メニューGUIが「保留中のリクエストがあるか」を副作用無しで確認するための読み取り専用アクセサ。
     */
    public Optional<IncomingRequestInfo> getIncomingRequest(UUID targetUuid) {
        Request req = requests.get(targetUuid);
        if (req == null) {
            return Optional.empty();
        }
        return Optional.of(new IncomingRequestInfo(req.type, req.requesterName));
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

        // 相手が承諾しても、実際に移動して支払うのは mover なので、ここであらためて
        // 「本当に行きますか？」と確認してから詠唱・課金に進む。金額はこの時点の距離での
        // 概算であり、実際の請求は詠唱完了時点の距離で再計算する (下記 startTeleport 内)。
        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 10.0);
        double estimatedCost = plugin.getTeleportSafetyManager().isSameDimension(mover.getLocation(), destinationPlayer.getLocation())
                ? CostUtil.distanceCost(mover.getLocation(), destinationPlayer.getLocation(), minFee, blocksPerYen)
                : minFee;

        String description = plugin.getMessages().get("tpa.travel-description", "player", destinationPlayer.getName());
        String moveActionKey = "tpa-move:" + mover.getUniqueId();
        plugin.getConfirmationManager().request(mover, moveActionKey, estimatedCost, description, () -> {
            if (!mover.isOnline() || !destinationPlayer.isOnline()) {
                return;
            }
            startTeleport(mover, destinationPlayer, description);
        });
    }

    private void startTeleport(Player mover, Player destinationPlayer, String description) {
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
            double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 10.0);
            double actualCost = CostUtil.distanceCost(mover.getLocation(), destination, minFee, blocksPerYen);

            Economy economy = plugin.getEconomyHolder().get();
            if (economy == null) {
                mover.sendMessage(plugin.msg("general.no-economy"));
                return;
            }
            if (!economy.has(mover, actualCost)) {
                mover.sendMessage(plugin.msg("tpa.insufficient-funds-requester", "player", destinationPlayer.getName(), "cost", ChatUtil.formatMoney(actualCost)));
                destinationPlayer.sendMessage(plugin.msg("tpa.insufficient-funds-target", "player", mover.getName(), "cost", ChatUtil.formatMoney(actualCost)));
                return;
            }

            economy.withdrawPlayer(mover, actualCost);
            mover.teleport(destination);
            plugin.getTeleportSafetyManager().playTeleportEffects(mover);
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
     * /tpacancel で呼ばれる。自分が送信した (相手がまだ応答していない) リクエストを取り消す。
     *
     * @return 取り消すリクエストがあった場合 true。無かった場合 false。
     */
    public boolean cancelByRequester(Player requester) {
        UUID requesterUuid = requester.getUniqueId();
        List<UUID> targetUuids = new ArrayList<>();
        for (Map.Entry<UUID, Request> entry : requests.entrySet()) {
            if (entry.getValue().requesterUuid.equals(requesterUuid)) {
                targetUuids.add(entry.getKey());
            }
        }
        if (targetUuids.isEmpty()) {
            return false;
        }
        for (UUID targetUuid : targetUuids) {
            Request request = requests.remove(targetUuid);
            if (request == null) {
                continue;
            }
            request.timeoutTask.cancel();
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) {
                target.sendMessage(plugin.msg("tpa.cancelled-by-requester", "player", requester.getName()));
            }
        }
        return true;
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
