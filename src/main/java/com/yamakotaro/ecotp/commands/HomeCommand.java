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
            sender.sendMessage("プレイヤーのみ実行できます。");
            return true;
        }
        if (!player.hasPermission("ecotp.home")) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cこのコマンドを使う権限がありません。"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (!plugin.getHomeManager().hasHome(uuid)) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cホームが設定されていません。/sethome で設定してください。"));
            return true;
        }

        Location home = plugin.getHomeManager().getHome(uuid);
        if (home == null) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cホームのワールドが見つかりません。"));
            return true;
        }

        double cost = plugin.getConfig().getDouble("costs.home", 100.0);
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(cost) + ")"));
            return true;
        }

        plugin.getConfirmationManager().request(player, cost, "ホームへテレポートします", () -> {
            if (!economy.has(player, cost)) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(cost) + ")"));
                return;
            }
            Location current = plugin.getHomeManager().getHome(uuid);
            if (current == null) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cホームのワールドが見つかりません。"));
                return;
            }
            economy.withdrawPlayer(player, cost);
            player.teleport(current);
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&aホームにテレポートしました。(" + ChatUtil.formatMoney(cost) + " 支払いました)"));
        });
        return true;
    }
}
