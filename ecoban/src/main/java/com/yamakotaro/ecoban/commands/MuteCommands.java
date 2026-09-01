package com.yamakotaro.ecoban.commands;

import com.yamakotaro.ecoban.Messages;
import com.yamakotaro.ecoban.PlayerResolver;
import com.yamakotaro.ecoban.TabCompleteUtil;
import com.yamakotaro.ecoban.core.DurationParser;
import com.yamakotaro.ecoban.core.PunishmentManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One executor registered for /mute, /tempmute, /unmute - dispatches on the label actually typed. */
public class MuteCommands implements CommandExecutor, TabCompleter {

    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public MuteCommands(PunishmentManager punishmentManager, Messages messages) {
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
            case "mute" -> handleMute(sender, args);
            case "tempmute" -> handleTempmute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            default -> {
            }
        }
        return true;
    }

    private void handleMute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.get("mute.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        punishmentManager.mute(uuid, args[0], PlayerResolver.joinFrom(args, 1), sender.getName(), 0);
        sender.sendMessage(messages.get("mute.issued", Map.of("player", args[0])));
    }

    private void handleTempmute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("tempmute.usage", Map.of()));
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
        punishmentManager.mute(uuid, args[0], PlayerResolver.joinFrom(args, 2), sender.getName(), durationMillis);
        sender.sendMessage(messages.get("tempmute.issued", Map.of("player", args[0])));
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.get("unmute.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        boolean removed = punishmentManager.unmute(uuid, sender.getName(), PlayerResolver.joinFrom(args, 1));
        sender.sendMessage(messages.get(removed ? "unmute.success" : "unmute.not-muted", Map.of("player", args[0])));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.onlinePlayerNames(args[0]);
        }
        return Collections.emptyList();
    }
}
