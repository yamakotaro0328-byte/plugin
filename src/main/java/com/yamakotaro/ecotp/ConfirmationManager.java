package com.yamakotaro.ecotp;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「チャットクリック承諾」の共通の仕組み。
 * 実際のお金のやり取り (残高チェック・引き落とし) は各コマンド側の onConfirm で行う。
 * ここでは「承諾/キャンセルの受付」と「タイムアウト」だけを管理する。
 * 承諾は次のいずれの方法でもよい: チャットのボタン、同じコマンドの再実行、/accept (/ok)。
 */
public class ConfirmationManager {

    private final EcoTpPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ConfirmationManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Pending {
        final String actionKey;
        final Runnable onConfirm;
        final BukkitTask timeoutTask;

        private Pending(String actionKey, Runnable onConfirm, BukkitTask timeoutTask) {
            this.actionKey = actionKey;
            this.onConfirm = onConfirm;
            this.timeoutTask = timeoutTask;
        }
    }

    /**
     * 支払いを伴う操作の確認を要求し、クリック可能なチャットメッセージを送る。
     *
     * @param player      対象プレイヤー
     * @param actionKey   「同じコマンドの再実行で承諾」を判定するためのキー
     *                    (例: "home:name", "tpa:targetName")。コマンドと引数が同じなら
     *                    再実行時に自動で承諾したことになる。
     * @param cost        表示する金額 (実際の引き落としは onConfirm 内で行うこと)
     * @param description 「〇〇へテレポート」などの説明文
     * @param onConfirm   承諾されたときに実行する処理
     */
    public void request(Player player, String actionKey, double cost, String description, Runnable onConfirm) {
        UUID uuid = player.getUniqueId();
        cancelSilently(uuid);

        int timeoutSeconds = plugin.getConfig().getInt("confirmation-timeout-seconds", 30);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(uuid);
            if (player.isOnline()) {
                player.sendMessage(plugin.msg("confirm.timed-out"));
            }
        }, timeoutSeconds * 20L);

        pending.put(uuid, new Pending(actionKey, onConfirm, task));
        ChatUtil.sendConfirmPrompt(player, plugin.getPrefix(), description, cost);
    }

    /**
     * 各コマンドの先頭で呼ぶ。同じ操作 (actionKey が一致) の確認待ちが既にある場合、
     * それを承諾したものとして実行して true を返す (呼び出し側はここで処理を終える)。
     * 確認待ちが無い、または別の操作の確認待ちがある場合は何もせず false を返す。
     */
    public boolean tryConfirmIfSameAction(Player player, String actionKey) {
        Pending p = pending.get(player.getUniqueId());
        if (p == null || !p.actionKey.equals(actionKey)) {
            return false;
        }
        pending.remove(player.getUniqueId());
        p.timeoutTask.cancel();
        p.onConfirm.run();
        return true;
    }

    /**
     * /accept (/ok) で呼ばれる。保留中の操作があれば実行する。
     */
    public void confirm(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            player.sendMessage(plugin.msg("confirm.none-pending"));
            return;
        }
        p.timeoutTask.cancel();
        p.onConfirm.run();
    }

    /**
     * /accept cancel で呼ばれる。保留中の操作をキャンセルする。
     */
    public void cancel(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            player.sendMessage(plugin.msg("confirm.none-to-cancel"));
            return;
        }
        p.timeoutTask.cancel();
        player.sendMessage(plugin.msg("confirm.cancelled"));
    }

    /**
     * このプレイヤーに支払いの確認待ちの操作があるかどうか。
     * /accept が支払い確認と /tpa の受諾のどちらを処理すべきか判断するために使う。
     */
    public boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /**
     * プレイヤー退出時などにメッセージを出さず保留を消す。
     */
    public void cancelSilently(UUID uuid) {
        Pending p = pending.remove(uuid);
        if (p != null) {
            p.timeoutTask.cancel();
        }
    }
}
