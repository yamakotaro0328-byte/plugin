package com.yamakotaro.ecoban.commands;

import com.yamakotaro.ecoban.Messages;
import com.yamakotaro.ecoban.PlayerResolver;
import com.yamakotaro.ecoban.TabCompleteUtil;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One executor registered for /history, /banlist - dispatches on the label actually typed. */
public class LookupCommands implements CommandExecutor, TabCompleter {

    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public LookupCommands(PunishmentManager punishmentManager, Messages messages) {
        this.punishmentManager = punishmentManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecoban.use")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        if (label.equalsIgnoreCase("banlist")) {
            handleBanlist(sender);
        } else {
            handleHistory(sender, args);
        }
        return true;
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.get("history.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        List<Punishment> history = punishmentManager.history(uuid);
        sender.sendMessage(messages.get("history.header", Map.of("player", args[0])));
        if (history.isEmpty()) {
            sender.sendMessage(messages.get("history.empty", Map.of()));
            return;
        }
        for (Punishment punishment : history) {
            String status = punishment.isActive() ? messages.raw("history.status-active", Map.of())
                    : messages.raw("history.status-inactive", Map.of());
            sender.sendMessage(messages.get("history.entry", Map.of(
                    "type", punishment.getType().name(),
                    "reason", punishment.getReason() != null ? punishment.getReason() : "",
                    "operator", punishment.getOperatorName() != null ? punishment.getOperatorName() : "",
                    "date", formatDate(punishment.getCreatedAt()),
                    "status", status)));
        }
    }

    private void handleBanlist(CommandSender sender) {
        List<Punishment> active = punishmentManager.listActive(null, 50);
        sender.sendMessage(messages.get("banlist.header", Map.of()));
        if (active.isEmpty()) {
            sender.sendMessage(messages.get("banlist.empty", Map.of()));
            return;
        }
        for (Punishment punishment : active) {
            String target = punishment.getTargetName() != null ? punishment.getTargetName()
                    : (punishment.getIp() != null ? punishment.getIp() : "?");
            sender.sendMessage(messages.get("banlist.entry", Map.of(
                    "type", punishment.getType().name(),
                    "target", target,
                    "reason", punishment.getReason() != null ? punishment.getReason() : "")));
        }
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && !alias.equalsIgnoreCase("banlist")) {
            return TabCompleteUtil.onlinePlayerNames(args[0]);
        }
        return Collections.emptyList();
    }
}
