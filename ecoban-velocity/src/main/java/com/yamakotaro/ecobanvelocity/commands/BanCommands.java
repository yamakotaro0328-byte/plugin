package com.yamakotaro.ecobanvelocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.yamakotaro.ecoban.core.DurationParser;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecobanvelocity.Messages;
import com.yamakotaro.ecobanvelocity.PlayerResolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One command registered for /ban, /tempban, /unban, /ipban, /unbanip - dispatches on the alias actually typed. */
public class BanCommands implements SimpleCommand {

    private final ProxyServer proxyServer;
    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public BanCommands(ProxyServer proxyServer, PunishmentManager punishmentManager, Messages messages) {
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
        switch (invocation.alias().toLowerCase()) {
            case "ban" -> handleBan(invocation);
            case "tempban" -> handleTempban(invocation);
            case "unban" -> handleUnban(invocation);
            case "ipban" -> handleIpban(invocation);
            case "unbanip" -> handleUnbanIp(invocation);
            default -> {
            }
        }
    }

    private void handleBan(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(messages.get("ban.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(proxyServer, args[0]);
        if (uuid == null) {
            invocation.source().sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        Punishment result = punishmentManager.ban(uuid, args[0], PlayerResolver.joinFrom(args, 1), PlayerResolver.operatorName(invocation.source()), 0);
        kickIfOnline(uuid, result);
        invocation.source().sendMessage(messages.get("ban.issued", Map.of("player", args[0])));
    }

    private void handleTempban(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            invocation.source().sendMessage(messages.get("tempban.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(proxyServer, args[0]);
        if (uuid == null) {
            invocation.source().sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        long durationMillis = DurationParser.parseMillis(args[1]);
        if (durationMillis <= 0) {
            invocation.source().sendMessage(messages.get("general.invalid-duration", Map.of()));
            return;
        }
        Punishment result = punishmentManager.ban(uuid, args[0], PlayerResolver.joinFrom(args, 2), PlayerResolver.operatorName(invocation.source()), durationMillis);
        kickIfOnline(uuid, result);
        invocation.source().sendMessage(messages.get("tempban.issued", Map.of("player", args[0])));
    }

    private void handleUnban(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(messages.get("unban.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(proxyServer, args[0]);
        if (uuid == null) {
            invocation.source().sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        boolean removed = punishmentManager.unban(uuid, PlayerResolver.operatorName(invocation.source()), PlayerResolver.joinFrom(args, 1));
        invocation.source().sendMessage(messages.get(removed ? "unban.success" : "unban.not-banned", Map.of("player", args[0])));
    }

    private void handleIpban(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(messages.get("ipban.usage", Map.of()));
            return;
        }
        String ip = PlayerResolver.resolveIp(proxyServer, args[0]);
        punishmentManager.ipban(ip, args[0], PlayerResolver.joinFrom(args, 1), PlayerResolver.operatorName(invocation.source()));
        kickOnlineByIp(ip);
        invocation.source().sendMessage(messages.get("ipban.issued", Map.of("ip", ip)));
    }

    private void handleUnbanIp(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(messages.get("unbanip.usage", Map.of()));
            return;
        }
        boolean removed = punishmentManager.unbanIp(args[0], PlayerResolver.operatorName(invocation.source()), PlayerResolver.joinFrom(args, 1));
        invocation.source().sendMessage(messages.get(removed ? "unbanip.success" : "unbanip.not-banned", Map.of("ip", args[0])));
    }

    private void kickIfOnline(UUID uuid, Punishment ban) {
        Optional<Player> online = proxyServer.getPlayer(uuid);
        if (online.isEmpty()) {
            return;
        }
        online.get().disconnect(messages.get(ban.isPermanent() ? "ban.kick-message-permanent" : "ban.kick-message-temporary", Map.of(
                "reason", ban.getReason() != null ? ban.getReason() : "",
                "operator", ban.getOperatorName() != null ? ban.getOperatorName() : "")));
    }

    private void kickOnlineByIp(String ip) {
        for (Player online : proxyServer.getAllPlayers()) {
            if (ip.equals(online.getRemoteAddress().getAddress().getHostAddress())) {
                online.disconnect(messages.get("ipban.kick-message", Map.of()));
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0];
            return proxyServer.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
