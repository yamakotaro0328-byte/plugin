package com.yamakotaro.serverkit.claims.commands;

import com.yamakotaro.serverkit.Messages;
import com.yamakotaro.serverkit.TabCompleteUtil;
import com.yamakotaro.serverkit.claims.Claim;
import com.yamakotaro.serverkit.claims.ClaimManager;
import com.yamakotaro.serverkit.claims.ClaimSelectionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final ClaimManager manager;
    private final ClaimSelectionManager selectionManager;
    private final Messages messages;

    public ClaimCommand(ClaimManager manager, ClaimSelectionManager selectionManager, Messages messages) {
        this.manager = manager;
        this.selectionManager = selectionManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.players-only", Map.of()));
            return true;
        }
        if (!player.hasPermission("serverkit.claims")) {
            player.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(messages.get("claims.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "wand" -> handleWand(player);
            case "create" -> handleCreate(player, args);
            case "remove" -> handleRemove(player, args);
            case "list" -> handleList(player);
            case "info" -> handleInfo(player);
            case "trust" -> handleTrust(player, args, true);
            case "untrust" -> handleTrust(player, args, false);
            default -> player.sendMessage(messages.get("claims.usage", Map.of()));
        }
        return true;
    }

    private void handleWand(Player player) {
        player.getInventory().addItem(selectionManager.createWand());
        player.sendMessage(messages.get("claims.wand-given", Map.of()));
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messages.get("claims.usage", Map.of()));
            return;
        }
        var points = selectionManager.getBothPoints(player.getUniqueId());
        if (points == null) {
            player.sendMessage(messages.get("claims.need-both-points", Map.of()));
            return;
        }
        Location pointA = points.getKey();
        Location pointB = points.getValue();
        String name = args[1];
        ClaimManager.CreateResult result = manager.createClaim(player, name, pointA, pointB);
        switch (result) {
            case DISABLED -> player.sendMessage(messages.get("claims.feature-disabled", Map.of()));
            case NAME_TAKEN -> player.sendMessage(messages.get("claims.name-taken", Map.of("name", name)));
            case DIFFERENT_WORLDS -> player.sendMessage(messages.get("claims.different-worlds", Map.of()));
            case INSUFFICIENT_BLOCKS -> {
                long available = manager.getBalance(player.getUniqueId()) - manager.usedBlocks(player.getUniqueId());
                player.sendMessage(messages.get("claims.insufficient-blocks", Map.of("available", String.valueOf(available))));
            }
            case OVERLAPS_CLAIM -> player.sendMessage(messages.get("claims.overlaps-claim", Map.of()));
            case OVERLAPS_EXTERNAL -> player.sendMessage(messages.get("claims.overlaps-external", Map.of()));
            case SUCCESS -> {
                selectionManager.clear(player.getUniqueId());
                player.sendMessage(messages.get("claims.created", Map.of("name", name)));
            }
        }
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messages.get("claims.usage", Map.of()));
            return;
        }
        ClaimManager.RemoveResult result = manager.removeClaim(player.getUniqueId(), args[1]);
        if (result == ClaimManager.RemoveResult.SUCCESS) {
            player.sendMessage(messages.get("claims.removed", Map.of("name", args[1])));
        } else {
            player.sendMessage(messages.get("claims.not-found", Map.of("name", args[1])));
        }
    }

    private void handleList(Player player) {
        List<Claim> claims = manager.claimsForOwner(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(messages.get("claims.list-empty", Map.of()));
            return;
        }
        player.sendMessage(messages.get("claims.list-header", Map.of()));
        for (Claim claim : claims) {
            player.sendMessage(messages.get("claims.list-entry", Map.of(
                    "name", claim.getName(),
                    "world", claim.getWorld(),
                    "area", String.valueOf(claim.area()))));
        }
    }

    private void handleInfo(Player player) {
        long balance = manager.getBalance(player.getUniqueId());
        long used = manager.usedBlocks(player.getUniqueId());
        player.sendMessage(messages.get("claims.info", Map.of(
                "balance", String.valueOf(balance),
                "used", String.valueOf(used),
                "available", String.valueOf(balance - used))));
    }

    private void handleTrust(Player player, String[] args, boolean trust) {
        if (args.length < 3) {
            player.sendMessage(messages.get("claims.usage", Map.of()));
            return;
        }
        String claimName = args[1];
        OfflinePlayer target = resolveOfflinePlayer(args[2]);
        ClaimManager.TrustResult result = trust
                ? manager.trust(player.getUniqueId(), claimName, target.getUniqueId())
                : manager.untrust(player.getUniqueId(), claimName, target.getUniqueId());
        switch (result) {
            case NOT_FOUND -> player.sendMessage(messages.get("claims.not-found", Map.of("name", claimName)));
            case ALREADY_TRUSTED -> player.sendMessage(messages.get("claims.already-trusted", Map.of("player", args[2])));
            case NOT_TRUSTED -> player.sendMessage(messages.get("claims.not-trusted", Map.of("player", args[2])));
            case SUCCESS -> player.sendMessage(messages.get(
                    trust ? "claims.trusted" : "claims.untrusted", Map.of("player", args[2])));
        }
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayer(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(
                    List.of("wand", "create", "remove", "list", "info", "trust", "untrust"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("trust")
                || args[0].equalsIgnoreCase("untrust")) && sender instanceof Player player) {
            List<String> names = manager.claimsForOwner(player.getUniqueId()).stream().map(Claim::getName).toList();
            return TabCompleteUtil.filterPrefix(names, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))
                && sender instanceof Player player) {
            return TabCompleteUtil.onlinePlayerNames(args[2], player.getUniqueId());
        }
        return Collections.emptyList();
    }
}
