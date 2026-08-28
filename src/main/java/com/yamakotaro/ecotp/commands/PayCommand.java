package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TabCompleteUtil;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public PayCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("pay")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.pay")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("pay.usage"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.msg("general.player-offline"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("general.cannot-target-self"));
            return true;
        }

        String actionKey = "pay:" + target.getName() + ":" + args[1];
        if (plugin.getConfirmationManager().tryConfirmIfSameAction(player, actionKey)) {
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.msg("pay.invalid-amount"));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(plugin.msg("pay.amount-too-low"));
            return true;
        }

        Economy economy = plugin.getEconomyHolder().get();
        if (!economy.has(player, amount)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(amount)));
            return true;
        }

        String targetName = target.getName();
        String description = plugin.getMessages().get("pay.description", "player", targetName);
        plugin.getConfirmationManager().request(player, actionKey, amount, description, () -> {
            Player currentTarget = Bukkit.getPlayerExact(targetName);
            if (currentTarget == null || !currentTarget.isOnline()) {
                player.sendMessage(plugin.msg("general.player-went-offline", "player", targetName));
                return;
            }
            if (!economy.has(player, amount)) {
                player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(amount)));
                return;
            }
            EconomyResponse withdrawResponse = economy.withdrawPlayer(player, amount);
            if (!withdrawResponse.transactionSuccess()) {
                player.sendMessage(plugin.msg("pay.failed", "error", withdrawResponse.errorMessage));
                return;
            }
            EconomyResponse depositResponse = economy.depositPlayer(currentTarget, amount);
            if (!depositResponse.transactionSuccess()) {
                // 入金に失敗した場合は引き落とし分を払い戻し、お金が消えないようにする。
                economy.depositPlayer(player, amount);
                player.sendMessage(plugin.msg("pay.refunded", "error", depositResponse.errorMessage));
                return;
            }
            player.sendMessage(plugin.msg("pay.sent", "player", targetName, "amount", ChatUtil.formatMoney(amount)));
            currentTarget.sendMessage(plugin.msg("pay.received", "player", player.getName(), "amount", ChatUtil.formatMoney(amount)));
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return TabCompleteUtil.onlinePlayerNames(args[0], player.getUniqueId());
        }
        return Collections.emptyList();
    }
}
