package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.CostUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.HomeManager;
import com.yamakotaro.ecotp.TabCompleteUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class HomeCommand implements CommandExecutor, TabCompleter {

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
        if (!plugin.isFeatureEnabled("home")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.home")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        String name = args.length > 0 ? args[0] : HomeManager.DEFAULT_NAME;
        String actionKey = "home:" + name;
        if (plugin.getConfirmationManager().tryConfirmIfSameAction(player, actionKey)) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (!plugin.getHomeManager().hasHome(uuid, name)) {
            player.sendMessage(plugin.msg("home.not-set", "name", name));
            return true;
        }

        Location home = plugin.getHomeManager().getHome(uuid, name);
        if (home == null) {
            player.sendMessage(plugin.msg("home.world-missing"));
            return true;
        }

        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 10.0);
        double cost = plugin.getTeleportSafetyManager().isSameDimension(player.getLocation(), home)
                ? CostUtil.distanceCost(player.getLocation(), home, minFee, blocksPerYen)
                : minFee;

        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            player.sendMessage(plugin.msg("general.no-economy"));
            return true;
        }
        if (!economy.has(player, cost)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
            return true;
        }

        String description = plugin.getMessages().get("home.teleporting", "name", name);
        plugin.getConfirmationManager().request(player, actionKey, cost, description, () -> {
            Location current = plugin.getHomeManager().getHome(uuid, name);
            if (current == null) {
                player.sendMessage(plugin.msg("home.world-missing"));
                return;
            }
            // start() が false を返す (別ディメンション/モブ/PvP) 場合は理由を通知済みなので何もしなくてよい。
            plugin.getTeleportSafetyManager().start(player, current, description, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location finalHome = plugin.getHomeManager().getHome(uuid, name);
                if (finalHome == null) {
                    player.sendMessage(plugin.msg("home.world-missing"));
                    return;
                }
                if (!plugin.getTeleportSafetyManager().isSameDimension(player.getLocation(), finalHome)) {
                    player.sendMessage(plugin.msg("teleport-safety.wrong-dimension"));
                    return;
                }
                double finalCost = CostUtil.distanceCost(player.getLocation(), finalHome, minFee, blocksPerYen);
                if (!economy.has(player, finalCost)) {
                    player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(finalCost)));
                    return;
                }
                economy.withdrawPlayer(player, finalCost);
                player.teleport(finalHome);
                plugin.getTeleportSafetyManager().playTeleportEffects(player);
                player.sendMessage(plugin.msg("home.success", "name", name, "cost", ChatUtil.formatMoney(finalCost)));
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return TabCompleteUtil.filterPrefix(plugin.getHomeManager().getHomeNames(player.getUniqueId()), args[0]);
        }
        return Collections.emptyList();
    }
}
