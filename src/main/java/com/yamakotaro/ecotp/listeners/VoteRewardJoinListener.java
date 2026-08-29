package com.yamakotaro.ecotp.listeners;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * オフライン中に届いた投票の報酬を、次回ログイン時に付与する。
 */
public class VoteRewardJoinListener implements Listener {

    private final EcoTpPlugin plugin;

    public VoteRewardJoinListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getVoteRewardManager().onPlayerJoin(player);
    }
}
