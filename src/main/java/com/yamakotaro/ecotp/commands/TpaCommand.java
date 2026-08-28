package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.CostUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TabCompleteUtil;
import com.yamakotaro.ecotp.TpaManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /tpa <プレイヤー名> : 自分が相手のもとへ移動するリクエストを送る。支払いは自分 (移動する側)。
 */
public class TpaCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public TpaCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("tpa")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.tpa")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.msg("tpa.usage"));
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

        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 100.0);
        double estimatedCost = plugin.getTeleportSafetyManager().isSameDimension(player.getLocation(), target.getLocation())
                ? CostUtil.distanceCost(player.getLocation(), target.getLocation(), minFee, blocksPerYen)
                : minFee;

        Economy economy = plugin.getEconomyHolder().get();
        if (!economy.has(player, estimatedCost)) {
            player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(estimatedCost)));
            return true;
        }

        // ここではまだ支払い確認を求めない。相手が承諾した後、実際に移動する側 (自分) に
        // あらためて「本当に行きますか？」と確認してから課金する (TpaManager.acceptRequest 参照)。
        plugin.getTpaManager().sendRequest(TpaManager.Type.TPA, player, target, estimatedCost);
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
