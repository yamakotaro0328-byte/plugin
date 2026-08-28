package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.CostUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public TpCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("ecotp.tp")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.msg("tp.usage"));
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

        double perBlock = plugin.getConfig().getDouble("costs.tp-per-block", 1.0);
        double crossWorldFlatCost = plugin.getConfig().getDouble("costs.cross-world-flat-cost", 500.0);
        double cost = CostUtil.distanceCost(player.getLocation(), target.getLocation(), perBlock, crossWorldFlatCost);

        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
            return true;
        }

        String targetName = target.getName();
        String description = plugin.getMessages().get("tp.description", "player", targetName);
        plugin.getConfirmationManager().request(player, cost, description, () ->
                plugin.getWarmupManager().start(player, description, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Player currentTarget = Bukkit.getPlayerExact(targetName);
                    if (currentTarget == null || !currentTarget.isOnline()) {
                        player.sendMessage(plugin.msg("general.player-went-offline", "player", targetName));
                        return;
                    }
                    // 確認/詠唱の間に自分か相手が移動している可能性があるため、実際にテレポート
                    // する直前の距離で再計算してから請求する (安い見積もりのまま遠くへ移動されるのを防ぐ)。
                    double actualCost = CostUtil.distanceCost(player.getLocation(), currentTarget.getLocation(), perBlock, crossWorldFlatCost);
                    if (!economy.has(player, actualCost)) {
                        player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(actualCost)));
                        return;
                    }
                    economy.withdrawPlayer(player, actualCost);
                    player.teleport(currentTarget.getLocation());
                    player.sendMessage(plugin.msg("tp.success", "player", targetName, "cost", ChatUtil.formatMoney(actualCost)));
                }));
        return true;
    }
}
