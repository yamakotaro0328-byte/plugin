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
 */
public class ConfirmationManager {

    private final EcoTpPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ConfirmationManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Pending {
        final Runnable onConfirm;
        final BukkitTask timeoutTask;

        private Pending(Runnable onConfirm, BukkitTask timeoutTask) {
            this.onConfirm = onConfirm;
            this.timeoutTask = timeoutTask;
        }
    }

    /**
     * 支払いを伴う操作の確認を要求し、クリック可能なチャットメッセージを送る。
     *
     * @param player      対象プレイヤー
     * @param cost        表示する金額 (実際の引き落としは onConfirm 内で行うこと)
     * @param description 「〇〇へテレポート」などの説明文
     * @param onConfirm   承諾されたときに実行する処理
     */
    public void request(Player player, double cost, String description, Runnable onConfirm) {
        UUID uuid = player.getUniqueId();
        cancelSilently(uuid);

        int timeoutSeconds = plugin.getConfig().getInt("confirmation-timeout-seconds", 30);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(uuid);
            if (player.isOnline()) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c確認がタイムアウトしました。"));
            }
        }, timeoutSeconds * 20L);

        pending.put(uuid, new Pending(onConfirm, task));
        ChatUtil.sendConfirmPrompt(player, plugin.getPrefix(), description, cost);
    }

    /**
     * /accept で呼ばれる。保留中の操作があれば実行する。
     */
    public void confirm(Player player) {
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c確認待ちの操作はありません。"));
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
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cキャンセルできる操作はありません。"));
            return;
        }
        p.timeoutTask.cancel();
        player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&e操作をキャンセルしました。"));
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
