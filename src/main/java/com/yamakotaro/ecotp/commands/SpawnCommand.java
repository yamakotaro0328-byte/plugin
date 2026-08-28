package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public SpawnCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行できます。");
            return true;
        }
        if (!player.hasPermission("ecotp.spawn")) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cこのコマンドを使う権限がありません。"));
            return true;
        }

        double cost = plugin.getConfig().getDouble("costs.spawn", 100.0);
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(cost) + ")"));
            return true;
        }

        plugin.getConfirmationManager().request(player, cost, "スポーン地点へテレポートします", () -> {
            if (!economy.has(player, cost)) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(cost) + ")"));
                return;
            }
            economy.withdrawPlayer(player, cost);
            player.teleport(plugin.getSpawnManager().getSpawn());
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&aスポーン地点にテレポートしました。(" + ChatUtil.formatMoney(cost) + " 支払いました)"));
        });
        return true;
    }
}
