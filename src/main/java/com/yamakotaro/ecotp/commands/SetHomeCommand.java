package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.HomeManager;
import com.yamakotaro.ecotp.TabCompleteUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SetHomeCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public SetHomeCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("sethome")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.sethome")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        String name = args.length > 0 ? args[0] : HomeManager.DEFAULT_NAME;
        if (!HomeManager.VALID_NAME.matcher(name).matches()) {
            player.sendMessage(plugin.msg("sethome.invalid-name"));
            return true;
        }

        String actionKey = "sethome:" + name;
        if (plugin.getConfirmationManager().tryConfirmIfSameAction(player, actionKey)) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (!plugin.getHomeManager().canSetHome(uuid, name)) {
            int max = plugin.getConfig().getInt("homes.max-per-player", 3);
            player.sendMessage(plugin.msg("sethome.limit-reached", "max", max));
            return true;
        }

        double cost = plugin.getHomeManager().getNextSetHomeCost(uuid);
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(cost)));
            return true;
        }

        String description = plugin.getMessages().get("sethome.description", "name", name);
        plugin.getConfirmationManager().request(player, actionKey, cost, description, () -> {
            if (!plugin.getHomeManager().canSetHome(uuid, name)) {
                int max = plugin.getConfig().getInt("homes.max-per-player", 3);
                player.sendMessage(plugin.msg("sethome.limit-reached", "max", max));
                return;
            }
            double currentCost = plugin.getHomeManager().getNextSetHomeCost(uuid);
            if (!economy.has(player, currentCost)) {
                player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(currentCost)));
                return;
            }
            economy.withdrawPlayer(player, currentCost);
            // 承諾した瞬間の位置をホームにする (コマンド入力後に移動している可能性があるため)。
            plugin.getHomeManager().setHome(uuid, name, player.getLocation());
            player.sendMessage(plugin.msg("sethome.success", "name", name, "cost", ChatUtil.formatMoney(currentCost)));
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
