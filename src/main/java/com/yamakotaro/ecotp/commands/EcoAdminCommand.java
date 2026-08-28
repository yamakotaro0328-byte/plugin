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
            sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cこのコマンドを使う権限がありません。"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c使い方: /eco <give|take|set> <プレイヤー名> <金額>"));
            return true;
        }

        String sub = args[0].toLowerCase();
        String targetName = args[1];
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c金額は数値で指定してください。"));
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c金額は0以上を指定してください。"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        Economy economy = plugin.getEconomy();
        plugin.getEcoTpEconomy().ensureAccount(target.getUniqueId(), targetName);

        switch (sub) {
            case "give" -> {
                economy.depositPlayer(target, amount);
                sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + targetName + " に " + ChatUtil.formatMoney(amount) + " 付与しました。"));
            }
            case "take" -> {
                EconomyResponse response = economy.withdrawPlayer(target, amount);
                if (!response.transactionSuccess()) {
                    sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c失敗しました: " + response.errorMessage));
                    return true;
                }
                sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + targetName + " から " + ChatUtil.formatMoney(amount) + " 減らしました。"));
            }
            case "set" -> {
                double current = economy.getBalance(target);
                if (amount > current) {
                    economy.depositPlayer(target, amount - current);
                } else if (amount < current) {
                    economy.withdrawPlayer(target, current - amount);
                }
                sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + targetName + " の所持金を " + ChatUtil.formatMoney(amount) + " に設定しました。"));
            }
            default -> sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c使い方: /eco <give|take|set> <プレイヤー名> <金額>"));
        }
        return true;
    }
}
