package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpEconomy;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TabCompleteUtil;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

/**
 * 管理者用: /eco give|take|set <プレイヤー名> <金額>
 */
public class EcoAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("give", "take", "set");

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
        Economy economy = plugin.getEconomyHolder().get();
        // 独自の経済 (economy.enabled) を使っている場合のみ口座の事前作成が必要。
        // 外部の経済プラグインに任せている場合は、そちら側の口座管理に任せる。
        EcoTpEconomy ecoTpEconomy = plugin.getEcoTpEconomy();
        if (ecoTpEconomy != null) {
            ecoTpEconomy.ensureAccount(target.getUniqueId(), targetName);
        }

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            return TabCompleteUtil.onlinePlayerNames(args[1], null);
        }
        return Collections.emptyList();
    }
}
