package com.yamakotaro.ecobanvelocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecobanvelocity.Messages;
import com.yamakotaro.ecobanvelocity.PlayerResolver;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One command registered for /history, /banlist - dispatches on the alias actually typed. */
public class LookupCommands implements SimpleCommand {

    private final ProxyServer proxyServer;
    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public LookupCommands(ProxyServer proxyServer, PunishmentManager punishmentManager, Messages messages) {
        this.proxyServer = proxyServer;
        this.punishmentManager = punishmentManager;
        this.messages = messages;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("ecoban.use");
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.alias().equalsIgnoreCase("banlist")) {
            handleBanlist(invocation);
        } else {
            handleHistory(invocation);
        }
    }

    private void handleHistory(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(messages.get("history.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(proxyServer, args[0]);
        if (uuid == null) {
            invocation.source().sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        List<Punishment> history = punishmentManager.history(uuid);
        invocation.source().sendMessage(messages.get("history.header", Map.of("player", args[0])));
        if (history.isEmpty()) {
            invocation.source().sendMessage(messages.get("history.empty", Map.of()));
            return;
        }
        for (Punishment punishment : history) {
            String status = punishment.isActive() ? messages.raw("history.status-active", Map.of())
                    : messages.raw("history.status-inactive", Map.of());
            invocation.source().sendMessage(messages.get("history.entry", Map.of(
                    "type", punishment.getType().name(),
                    "reason", punishment.getReason() != null ? punishment.getReason() : "",
                    "operator", punishment.getOperatorName() != null ? punishment.getOperatorName() : "",
                    "date", formatDate(punishment.getCreatedAt()),
                    "status", status)));
        }
    }

    private void handleBanlist(Invocation invocation) {
        List<Punishment> active = punishmentManager.listActive(null, 50);
        invocation.source().sendMessage(messages.get("banlist.header", Map.of()));
        if (active.isEmpty()) {
            invocation.source().sendMessage(messages.get("banlist.empty", Map.of()));
            return;
        }
        for (Punishment punishment : active) {
            String target = punishment.getTargetName() != null ? punishment.getTargetName()
                    : (punishment.getIp() != null ? punishment.getIp() : "?");
            invocation.source().sendMessage(messages.get("banlist.entry", Map.of(
                    "type", punishment.getType().name(),
                    "target", target,
                    "reason", punishment.getReason() != null ? punishment.getReason() : "")));
        }
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1 && !invocation.alias().equalsIgnoreCase("banlist")) {
            String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0];
            return proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
