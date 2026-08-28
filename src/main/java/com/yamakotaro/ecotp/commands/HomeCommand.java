package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class HomeCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public HomeCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("ecotp.home")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (!plugin.getHomeManager().hasHome(uuid)) {
            player.sendMessage(plugin.msg("home.not-set"));
            return true;
        }

        Location home = plugin.getHomeManager().getHome(uuid);
        if (home == null) {
            player.sendMessage(plugin.msg("home.world-missing"));
            return true;
        }

        double cost = plugin.getConfig().getDouble("costs.home", 100.0);
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
            return true;
        }

        String description = plugin.getMessages().get("home.teleporting");
        plugin.getConfirmationManager().request(player, cost, description, () ->
                plugin.getWarmupManager().start(player, description, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!economy.has(player, cost)) {
                        player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
                        return;
                    }
                    Location current = plugin.getHomeManager().getHome(uuid);
                    if (current == null) {
                        player.sendMessage(plugin.msg("home.world-missing"));
                        return;
                    }
                    economy.withdrawPlayer(player, cost);
                    player.teleport(current);
                    player.sendMessage(plugin.msg("home.success", "cost", ChatUtil.formatMoney(cost)));
                }));
        return true;
    }
}
