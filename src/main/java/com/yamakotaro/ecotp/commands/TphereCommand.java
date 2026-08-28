package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.CostUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TpaManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tphere <プレイヤー名> : 相手を自分のもとへ呼ぶリクエストを送る。着払いのため、
 * 支払いは呼ばれた相手 (実際に移動する側)。呼び出す側 (自分) は支払わないため
 * 料金の事前承諾は不要 — 相手の承諾 (支払いへの同意を兼ねる) だけで進む。
 */
public class TphereCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public TphereCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("ecotp.tphere")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.msg("tphere.usage"));
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

        plugin.getTpaManager().sendRequest(TpaManager.Type.TPHERE, player, target, estimatedCost);
        return true;
    }
}
