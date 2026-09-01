package com.yamakotaro.ecoban.commands;

import com.yamakotaro.ecoban.Messages;
import com.yamakotaro.ecoban.PlayerResolver;
import com.yamakotaro.ecoban.TabCompleteUtil;
import com.yamakotaro.ecoban.core.DurationParser;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One executor registered for /ban, /tempban, /unban, /ipban, /unbanip - dispatches on the label actually typed. */
public class BanCommands implements CommandExecutor, TabCompleter {

    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public BanCommands(PunishmentManager punishmentManager, Messages messages) {
        this.punishmentManager = punishmentManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecoban.use")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        switch (label.toLowerCase()) {
            case "ban" -> handleBan(sender, args);
            case "tempban" -> handleTempban(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "ipban" -> handleIpban(sender, args);
            case "unbanip" -> handleUnbanIp(sender, args);
            default -> {
            }
        }
        return true;
    }

    private void handleBan(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.get("ban.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        Punishment result = punishmentManager.ban(uuid, args[0], PlayerResolver.joinFrom(args, 1), sender.getName(), 0);
        kickIfOnline(uuid, result);
        sender.sendMessage(messages.get("ban.issued", Map.of("player", args[0])));
    }

    private void handleTempban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("tempban.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        long durationMillis = DurationParser.parseMillis(args[1]);
        if (durationMillis <= 0) {
            sender.sendMessage(messages.get("general.invalid-duration", Map.of()));
            return;
        }
        Punishment result = punishmentManager.ban(uuid, args[0], PlayerResolver.joinFrom(args, 2), sender.getName(), durationMillis);
        kickIfOnline(uuid, result);
        sender.sendMessage(messages.get("tempban.issued", Map.of("player", args[0])));
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.get("unban.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        boolean removed = punishmentManager.unban(uuid, sender.getName(), PlayerResolver.joinFrom(args, 1));
        sender.sendMessage(messages.get(removed ? "unban.success" : "unban.not-banned", Map.of("player", args[0])));
    }

    private void handleIpban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.get("ipban.usage", Map.of()));
            return;
        }
        String ip = PlayerResolver.resolveIp(args[0]);
        punishmentManager.ipban(ip, args[0], PlayerResolver.joinFrom(args, 1), sender.getName());
        kickOnlineByIp(ip);
        sender.sendMessage(messages.get("ipban.issued", Map.of("ip", ip)));
    }

    private void handleUnbanIp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.get("unbanip.usage", Map.of()));
            return;
        }
        boolean removed = punishmentManager.unbanIp(args[0], sender.getName(), PlayerResolver.joinFrom(args, 1));
        sender.sendMessage(messages.get(removed ? "unbanip.success" : "unbanip.not-banned", Map.of("ip", args[0])));
    }

    private void kickIfOnline(UUID uuid, Punishment ban) {
        Player online = Bukkit.getPlayer(uuid);
        if (online == null) {
            return;
        }
        online.kick(messages.get(ban.isPermanent() ? "ban.kick-message-permanent" : "ban.kick-message-temporary", Map.of(
                "reason", ban.getReason() != null ? ban.getReason() : "",
                "operator", ban.getOperatorName() != null ? ban.getOperatorName() : "")));
    }

    private void kickOnlineByIp(String ip) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getAddress() != null && ip.equals(online.getAddress().getAddress().getHostAddress())) {
                online.kick(messages.get("ipban.kick-message", Map.of()));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.onlinePlayerNames(args[0]);
        }
        return Collections.emptyList();
    }
}
