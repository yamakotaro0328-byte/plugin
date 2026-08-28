package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.CostUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;
    private static final String ACTION_KEY = "spawn";

    public SpawnCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("spawn")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.spawn")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        if (plugin.getConfirmationManager().tryConfirmIfSameAction(player, ACTION_KEY)) {
            return true;
        }

        Location spawn = plugin.getSpawnManager().getSpawn();
        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 100.0);
        double cost = plugin.getTeleportSafetyManager().isSameDimension(player.getLocation(), spawn)
                ? CostUtil.distanceCost(player.getLocation(), spawn, minFee, blocksPerYen)
                : minFee;

        Economy economy = plugin.getEconomyHolder().get();
        if (!economy.has(player, cost)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
            return true;
        }

        String description = plugin.getMessages().get("spawn.teleporting");
        plugin.getConfirmationManager().request(player, ACTION_KEY, cost, description, () ->
                plugin.getTeleportSafetyManager().start(player, spawn, description, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Location finalSpawn = plugin.getSpawnManager().getSpawn();
                    if (!plugin.getTeleportSafetyManager().isSameDimension(player.getLocation(), finalSpawn)) {
                        player.sendMessage(plugin.msg("teleport-safety.wrong-dimension"));
                        return;
                    }
                    double finalCost = CostUtil.distanceCost(player.getLocation(), finalSpawn, minFee, blocksPerYen);
                    if (!economy.has(player, finalCost)) {
                        player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(finalCost)));
                        return;
                    }
                    economy.withdrawPlayer(player, finalCost);
                    player.teleport(finalSpawn);
                    plugin.getTeleportSafetyManager().playTeleportEffects(player);
                    player.sendMessage(plugin.msg("spawn.success", "cost", ChatUtil.formatMoney(finalCost)));
                }));
        return true;
    }
}
