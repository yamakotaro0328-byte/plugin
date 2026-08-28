package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 参加時に口座が無ければ作成する (Essentials のデータがあれば引き継ぐ)。
 */
public class EconomyJoinListener implements Listener {

    private final EcoTpPlugin plugin;

    public EconomyJoinListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getEcoTpEconomy().ensureAccount(player.getUniqueId(), player.getName());
    }
}
