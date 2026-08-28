package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * 管理者用: /eco give|take|set <プレイヤー名> <金額>
 */
public class EcoAdminCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public EcoAdminCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecotp.admin")) {
            sender.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("eco.usage"));
            return true;
        }

        String sub = args[0].toLowerCase();
        String targetName = args[1];
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.msg("eco.invalid-amount"));
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(plugin.msg("eco.negative-amount"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Economy economy = plugin.getEconomy();
        plugin.getEcoTpEconomy().ensureAccount(target.getUniqueId(), targetName);

        switch (sub) {
            case "give" -> {
                economy.depositPlayer(target, amount);
                sender.sendMessage(plugin.msg("eco.gave", "player", targetName, "amount", ChatUtil.formatMoney(amount)));
            }
            case "take" -> {
                EconomyResponse response = economy.withdrawPlayer(target, amount);
                if (!response.transactionSuccess()) {
                    sender.sendMessage(plugin.msg("eco.took-failed", "error", response.errorMessage));
                    return true;
                }
                sender.sendMessage(plugin.msg("eco.took", "player", targetName, "amount", ChatUtil.formatMoney(amount)));
            }
            case "set" -> {
                double current = economy.getBalance(target);
                if (amount > current) {
                    economy.depositPlayer(target, amount - current);
                } else if (amount < current) {
                    economy.withdrawPlayer(target, current - amount);
                }
                sender.sendMessage(plugin.msg("eco.set", "player", targetName, "amount", ChatUtil.formatMoney(amount)));
            }
            default -> sender.sendMessage(plugin.msg("eco.usage"));
        }
        return true;
    }
}
