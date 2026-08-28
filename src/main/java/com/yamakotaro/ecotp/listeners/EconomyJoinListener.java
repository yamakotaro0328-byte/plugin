package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.EcoTpEconomy;
import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 参加時に口座が無ければ作成する (Essentials のデータがあれば引き継ぐ)。
 * 独自の経済 (economy.enabled) を使っていない場合は、口座管理は外部の経済プラグインに任せるため何もしない。
 */
public class EconomyJoinListener implements Listener {

    private final EcoTpPlugin plugin;

    public EconomyJoinListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        EcoTpEconomy ecoTpEconomy = plugin.getEcoTpEconomy();
        if (ecoTpEconomy == null) {
            return;
        }
        Player player = event.getPlayer();
        ecoTpEconomy.ensureAccount(player.getUniqueId(), player.getName());
    }
}
