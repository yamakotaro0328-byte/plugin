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
        if (!plugin.getWarpUnlockManager().isUnlocked(player, name, warp)) {
            requestUnlock(player, name, warp, actionKey);
            return true;
        }

        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        double blocksPerYen = plugin.getConfig().getDouble("costs.distance-blocks-per-yen", 10.0);
        double cost = plugin.getTeleportDiscount().apply(player, plugin.getTeleportSafetyManager().isSameDimension(player.getLocation(), warp)
                ? CostUtil.distanceCost(player.getLocation(), warp, minFee, blocksPerYen)
                : minFee);

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
                    double finalCost = plugin.getTeleportDiscount().apply(player, CostUtil.distanceCost(player.getLocation(), finalWarp, minFee, blocksPerYen));
                    if (!economy.has(player, finalCost)) {
                        player.sendMessage(plugin.msg("general.insufficient-funds", "cost", ChatUtil.formatMoney(finalCost)));
                        return;
                    }
                    if (!plugin.getTeleportSafetyManager().chargeAndTeleport(player, economy, finalCost, finalWarp)) {
                        return;
                    }
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

    /** まだ解放していない遠くのワープ: 解放料金の確認を出し、承諾されたら解放してそのまま移動の手続きへ。 */
    private void requestUnlock(Player player, String name, Location warp, String actionKey) {
        double price = plugin.getWarpUnlockManager().price(name, warp);
        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            player.sendMessage(plugin.msg("general.no-economy"));
            return;
        }
        if (!economy.has(player, price)) {
            player.sendMessage(plugin.msg("warp-unlock.insufficient-funds", "name", name, "price", ChatUtil.formatMoney(price)));
            return;
        }
        String description = plugin.getMessages().get("warp-unlock.description", "name", name);
        // 同じ actionKey を使うので、もう一度 /warp <名前> でも承諾できる。
        plugin.getConfirmationManager().request(player, actionKey, price, description, () -> {
            if (!player.isOnline() || plugin.getWarpUnlockManager().isUnlocked(player, name, warp)) {
                return;
            }
            if (!economy.withdrawPlayer(player, price).transactionSuccess()) {
                player.sendMessage(plugin.msg("warp-unlock.insufficient-funds", "name", name, "price", ChatUtil.formatMoney(price)));
                return;
            }
            plugin.getWarpUnlockManager().unlock(player.getUniqueId(), name);
            player.sendMessage(plugin.msg("warp-unlock.unlocked", "name", name, "price", ChatUtil.formatMoney(price)));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            player.performCommand("warp " + name); // 続けて移動の確認へ
        });
    }
}
