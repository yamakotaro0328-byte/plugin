package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SetHomeCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public SetHomeCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行できます。");
            return true;
        }
        if (!player.hasPermission("ecotp.sethome")) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cこのコマンドを使う権限がありません。"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        double cost = plugin.getHomeManager().getNextSetHomeCost(uuid);
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(cost) + ")"));
            return true;
        }

        var location = player.getLocation();
        plugin.getConfirmationManager().request(player, cost, "現在地をホームに設定します", () -> {
            double currentCost = plugin.getHomeManager().getNextSetHomeCost(uuid);
            if (!economy.has(player, currentCost)) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(currentCost) + ")"));
                return;
            }
            economy.withdrawPlayer(player, currentCost);
            plugin.getHomeManager().setHome(uuid, location);
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&aホームを設定しました。(" + ChatUtil.formatMoney(currentCost) + " 支払いました)"));
        });
        return true;
    }
}
