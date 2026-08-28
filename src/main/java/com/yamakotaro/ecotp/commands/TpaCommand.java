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

public class TpaCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public TpaCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行できます。");
            return true;
        }
        if (!player.hasPermission("ecotp.tpa")) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cこのコマンドを使う権限がありません。"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c使い方: /tpa <プレイヤー名>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cそのプレイヤーはオンラインではありません。"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c自分自身にはリクエストできません。"));
            return true;
        }

        double perBlock = plugin.getConfig().getDouble("costs.tpa-per-block", 1.0);
        double crossWorldFlatCost = plugin.getConfig().getDouble("costs.cross-world-flat-cost", 500.0);
        double cost = CostUtil.distanceCost(player.getLocation(), target.getLocation(), perBlock, crossWorldFlatCost);

        Economy economy = plugin.getEconomy();
        if (!economy.has(player, cost)) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c所持金が不足しています。(必要: " + ChatUtil.formatMoney(cost) + ")"));
            return true;
        }

        String targetName = target.getName();
        // ここではお金は引き落とさない。相手が承諾したときに初めて課金する。
        plugin.getConfirmationManager().request(player, cost, targetName + " にテレポートリクエストを送ります", () -> {
            Player currentTarget = Bukkit.getPlayerExact(targetName);
            if (currentTarget == null || !currentTarget.isOnline()) {
                player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&c" + targetName + " はオフラインになりました。"));
                return;
            }
            plugin.getTpaManager().sendRequest(player, currentTarget, cost);
        });
        return true;
    }
}
