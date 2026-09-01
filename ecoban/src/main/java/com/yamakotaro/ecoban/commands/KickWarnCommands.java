package com.yamakotaro.ecoban.commands;

import com.yamakotaro.ecoban.Messages;
import com.yamakotaro.ecoban.PlayerResolver;
import com.yamakotaro.ecoban.TabCompleteUtil;
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

/** One executor registered for /kick, /warn - dispatches on the label actually typed. */
public class KickWarnCommands implements CommandExecutor, TabCompleter {

    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public KickWarnCommands(PunishmentManager punishmentManager, Messages messages) {
        this.punishmentManager = punishmentManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecoban.use")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        boolean isKick = label.equalsIgnoreCase("kick");
        if (args.length < 1) {
            sender.sendMessage(messages.get(isKick ? "kick.usage" : "warn.usage", Map.of()));
            return true;
        }
        UUID uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return true;
        }
        String reason = PlayerResolver.joinFrom(args, 1);
        if (isKick) {
            handleKick(sender, uuid, args[0], reason);
        } else {
            handleWarn(sender, uuid, args[0], reason);
        }
        return true;
    }

    private void handleKick(CommandSender sender, UUID uuid, String name, String reason) {
        punishmentManager.kick(uuid, name, reason, sender.getName());
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.kick(messages.get("kick.message", Map.of("reason", reason != null ? reason : "")));
        }
        sender.sendMessage(messages.get("kick.issued", Map.of("player", name)));
    }

    private void handleWarn(CommandSender sender, UUID uuid, String name, String reason) {
        punishmentManager.warn(uuid, name, reason, sender.getName());
        sender.sendMessage(messages.get("warn.issued", Map.of("player", name)));
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(messages.get("warn.notice", Map.of("reason", reason != null ? reason : "")));
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
