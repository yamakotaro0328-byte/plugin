package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.CostUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
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

/**
 * /warp [名前] : 名前なしで一覧表示、名前ありでそのワープ地点へ移動する。
 * 料金・確認・安全チェックは /spawn と同じ (距離制、詠唱中に動いたら中断・課金なし)。
 */
public class WarpCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public WarpCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("warp")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.warp")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        List<String> names = plugin.getWarpManager().getWarpNames();
        if (args.length == 0) {
            if (names.isEmpty()) {
                player.sendMessage(plugin.msg("warp.empty"));
            } else {
                player.sendMessage(plugin.msg("warp.list", "warps", String.join(", ", names)));
            }
            return true;
        }

        String name = plugin.getWarpManager().resolveName(args[0]);
        if (name == null) {
            player.sendMessage(plugin.msg("warp.not-found", "name", args[0]));
            return true;
        }
        String actionKey = "warp:" + name;
        if (plugin.getConfirmationManager().tryConfirmIfSameAction(player, actionKey)) {
            return true;
        }
        Location warp = plugin.getWarpManager().getWarp(name);
        if (warp == null) {
            player.sendMessage(plugin.msg("warp.world-missing"));
            return true;
        }

        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 10.0);
        double cost = plugin.getTeleportSafetyManager().isSameDimension(player.getLocation(), warp)
                ? CostUtil.distanceCost(player.getLocation(), warp, minFee, blocksPerYen)
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

        String description = plugin.getMessages().get("warp.teleporting", "name", name);
        plugin.getConfirmationManager().request(player, actionKey, cost, description, () ->
                plugin.getTeleportSafetyManager().start(player, warp, description, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    // 詠唱中に /setwarp や /delwarp で移動・削除された場合も最新の状態で判定する。
                    Location finalWarp = plugin.getWarpManager().getWarp(name);
                    if (finalWarp == null) {
                        player.sendMessage(plugin.msg("warp.not-found", "name", name));
                        return;
                    }
                    if (!plugin.getTeleportSafetyManager().isSameDimension(player.getLocation(), finalWarp)) {
                        player.sendMessage(plugin.msg("teleport-safety.wrong-dimension"));
                        return;
                    }
                    double finalCost = CostUtil.distanceCost(player.getLocation(), finalWarp, minFee, blocksPerYen);
                    if (!economy.has(player, finalCost)) {
                        player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(finalCost)));
                        return;
                    }
                    economy.withdrawPlayer(player, finalCost);
                    player.teleport(finalWarp);
                    plugin.getTeleportSafetyManager().playTeleportEffects(player);
                    player.sendMessage(plugin.msg("warp.success", "name", name, "cost", ChatUtil.formatMoney(finalCost)));
                }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(plugin.getWarpManager().getWarpNames(), args[0]);
        }
        return Collections.emptyList();
    }
}
