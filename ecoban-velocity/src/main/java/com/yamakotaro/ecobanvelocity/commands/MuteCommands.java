package com.yamakotaro.ecobanvelocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.yamakotaro.ecoban.core.DurationParser;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecobanvelocity.Messages;
import com.yamakotaro.ecobanvelocity.PlayerResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One command registered for /mute, /tempmute, /unmute - dispatches on the alias actually typed. */
public class MuteCommands implements SimpleCommand {

    private final ProxyServer proxyServer;
    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public MuteCommands(ProxyServer proxyServer, PunishmentManager punishmentManager, Messages messages) {
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
            case "mute" -> handleMute(invocation);
            case "tempmute" -> handleTempmute(invocation);
            case "unmute" -> handleUnmute(invocation);
            default -> {
            }
        }
    }

    private void handleMute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(messages.get("mute.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(proxyServer, args[0]);
        if (uuid == null) {
            invocation.source().sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        punishmentManager.mute(uuid, args[0], PlayerResolver.joinFrom(args, 1), PlayerResolver.operatorName(invocation.source()), 0);
        invocation.source().sendMessage(messages.get("mute.issued", Map.of("player", args[0])));
    }

    private void handleTempmute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            invocation.source().sendMessage(messages.get("tempmute.usage", Map.of()));
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
        punishmentManager.mute(uuid, args[0], PlayerResolver.joinFrom(args, 2), PlayerResolver.operatorName(invocation.source()), durationMillis);
        invocation.source().sendMessage(messages.get("tempmute.issued", Map.of("player", args[0])));
    }

    private void handleUnmute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(messages.get("unmute.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(proxyServer, args[0]);
        if (uuid == null) {
            invocation.source().sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        boolean removed = punishmentManager.unmute(uuid, PlayerResolver.operatorName(invocation.source()), PlayerResolver.joinFrom(args, 1));
        invocation.source().sendMessage(messages.get(removed ? "unmute.success" : "unmute.not-muted", Map.of("player", args[0])));
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
