package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SetHomeCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public SetHomeCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("ecotp.sethome")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        double cost = plugin.getHomeManager().getNextSetHomeCost(uuid);
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
            return true;
        }

        String description = plugin.getMessages().get("sethome.description");
        plugin.getConfirmationManager().request(player, cost, description, () -> {
            double currentCost = plugin.getHomeManager().getNextSetHomeCost(uuid);
            if (!economy.has(player, currentCost)) {
                player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(currentCost)));
                return;
            }
            economy.withdrawPlayer(player, currentCost);
            // 承諾した瞬間の位置をホームにする (コマンド入力後に移動している可能性があるため)。
            plugin.getHomeManager().setHome(uuid, player.getLocation());
            player.sendMessage(plugin.msg("sethome.success", "cost", ChatUtil.formatMoney(currentCost)));
        });
        return true;
    }
}
