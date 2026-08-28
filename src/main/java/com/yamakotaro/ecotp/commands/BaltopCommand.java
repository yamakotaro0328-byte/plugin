package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.BalanceEntry;
import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class BaltopCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public BaltopCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = plugin.getConfig().getInt("baltop-limit", 10);
        List<BalanceEntry> top = plugin.getEcoTpEconomy().getTopBalances(limit);

        sender.sendMessage(plugin.msg("baltop.header", "limit", limit));
        if (top.isEmpty()) {
            sender.sendMessage(plugin.msg("baltop.empty"));
            return true;
        }

        int rank = 1;
        for (BalanceEntry entry : top) {
            sender.sendMessage(plugin.msg("baltop.line", "rank", rank, "player", entry.name(), "balance", ChatUtil.formatMoney(entry.balance())));
            rank++;
        }
        return true;
    }
}
