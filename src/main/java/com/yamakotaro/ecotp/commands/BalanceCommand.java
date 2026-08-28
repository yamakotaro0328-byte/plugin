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
                sender.sendMessage(plugin.getMessages().get("general.players-only"));
                return true;
            }
            double balance = economy.getBalance(player);
            player.sendMessage(plugin.msg("balance.self", "balance", ChatUtil.formatMoney(balance)));
            return true;
        }

        if (!sender.hasPermission("ecotp.balance.others")) {
            sender.sendMessage(plugin.msg("balance.no-permission-others"));
            return true;
        }

        String targetName = args[0];
        double balance = economy.getBalance(targetName);
        sender.sendMessage(plugin.msg("balance.other", "player", targetName, "balance", ChatUtil.formatMoney(balance)));
        return true;
    }
}
