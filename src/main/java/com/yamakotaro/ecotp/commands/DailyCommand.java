package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /daily : 1日1回のログインボーナスを受け取る (連続日数に応じて報酬が増える)。
 */
public class DailyCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public DailyCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("daily")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.daily")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        plugin.getDailyRewardManager().claim(player);
        return true;
    }
}
