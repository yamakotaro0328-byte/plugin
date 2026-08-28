package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public SpawnCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("ecotp.spawn")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        double cost = plugin.getConfig().getDouble("costs.spawn", 100.0);
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
            return true;
        }

        String description = plugin.getMessages().get("spawn.teleporting");
        plugin.getConfirmationManager().request(player, cost, description, () ->
                plugin.getWarmupManager().start(player, description, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!economy.has(player, cost)) {
                        player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
                        return;
                    }
                    economy.withdrawPlayer(player, cost);
                    player.teleport(plugin.getSpawnManager().getSpawn());
                    player.sendMessage(plugin.msg("spawn.success", "cost", ChatUtil.formatMoney(cost)));
                }));
        return true;
    }
}
