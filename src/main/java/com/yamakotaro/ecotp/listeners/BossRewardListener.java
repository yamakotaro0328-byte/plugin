package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * ボスの討伐賞金: config.yml の boss-rewards.mobs に書いた Mob を倒したプレイヤーに
 * 賞金を払い、全体に通知する。
 */
public class BossRewardListener implements Listener {

    private final EcoTpPlugin plugin;

    public BossRewardListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("boss-rewards.enabled", true)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        String type = entity.getType().name();
        double reward = plugin.getConfig().getDouble("boss-rewards.mobs." + type, 0.0);
        Economy economy = plugin.getEconomyHolder().get();
        if (reward <= 0 || economy == null || !economy.depositPlayer(killer, reward).transactionSuccess()) {
            return;
        }
        String mobName = plugin.getMessages().get("boss-rewards.names." + type.toLowerCase());
        if (mobName.isEmpty() || mobName.contains("boss-rewards.names")) {
            mobName = type;
        }
        Bukkit.broadcastMessage(plugin.getMessages().get("boss-rewards.broadcast",
                "player", killer.getName(), "mob", mobName, "reward", ChatUtil.formatMoney(reward)));
    }
}
