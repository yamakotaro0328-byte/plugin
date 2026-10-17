package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 初参加の歓迎: 初めてサーバーに入った人に歓迎メッセージ (と任意で初期資金) を渡し、
 * 全体に「初参加」を通知する。
 */
public class WelcomeListener implements Listener {

    private final EcoTpPlugin plugin;

    public WelcomeListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore() || !plugin.getConfig().getBoolean("welcome.enabled", true)) {
            return;
        }
        int number = Bukkit.getOfflinePlayers().length;
        // 参加直後は他のメッセージに埋もれるので少し待つ (アカウント作成もこの間に済む)。
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getConfig().getBoolean("welcome.broadcast", true)) {
                Bukkit.broadcastMessage(plugin.getMessages().get("welcome.broadcast",
                        "player", player.getName(), "number", String.valueOf(number)));
            }
            if (!player.isOnline()) {
                return;
            }
            player.sendMessage(plugin.getMessages().get("welcome.message", "player", player.getName()));
            double gift = plugin.getConfig().getDouble("welcome.gift", 0.0);
            Economy economy = plugin.getEconomyHolder().get();
            if (gift > 0 && economy != null && economy.depositPlayer(player, gift).transactionSuccess()) {
                player.sendMessage(plugin.msg("welcome.gift", "amount", ChatUtil.formatMoney(gift)));
            }
        }, 40L);
    }
}
