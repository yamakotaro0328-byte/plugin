package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public BalanceCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Economy economy = plugin.getEconomy();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("使い方: /balance <プレイヤー名>");
                return true;
            }
            double balance = economy.getBalance(player);
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&aあなたの所持金: &f" + ChatUtil.formatMoney(balance)));
            return true;
        }

        if (!sender.hasPermission("ecotp.balance.others")) {
            sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c他のプレイヤーの所持金を見る権限がありません。"));
            return true;
        }

        String targetName = args[0];
        double balance = economy.getBalance(targetName);
        sender.sendMessage(ChatUtil.color(plugin.getPrefix() + "&a" + targetName + " の所持金: &f" + ChatUtil.formatMoney(balance)));
        return true;
    }
}
