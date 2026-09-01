package com.yamakotaro.ecobanvelocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecobanvelocity.Messages;
import com.yamakotaro.ecobanvelocity.PlayerResolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One command registered for /kick, /warn - dispatches on the alias actually typed. */
public class KickWarnCommands implements SimpleCommand {

    private final ProxyServer proxyServer;
    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public KickWarnCommands(ProxyServer proxyServer, PunishmentManager punishmentManager, Messages messages) {
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
        boolean isKick = invocation.alias().equalsIgnoreCase("kick");
        String[] args = invocation.arguments();
        if (args.length < 1) {
            invocation.source().sendMessage(messages.get(isKick ? "kick.usage" : "warn.usage", Map.of()));
            return;
        }
        UUID uuid = PlayerResolver.resolveUuid(proxyServer, args[0]);
        if (uuid == null) {
            invocation.source().sendMessage(messages.get("general.player-not-found", Map.of("player", args[0])));
            return;
        }
        String reason = PlayerResolver.joinFrom(args, 1);
        if (isKick) {
            handleKick(invocation, uuid, args[0], reason);
        } else {
            handleWarn(invocation, uuid, args[0], reason);
        }
    }

    private void handleKick(Invocation invocation, UUID uuid, String name, String reason) {
        punishmentManager.kick(uuid, name, reason, PlayerResolver.operatorName(invocation.source()));
        Optional<Player> online = proxyServer.getPlayer(uuid);
        online.ifPresent(player -> player.disconnect(messages.get("kick.message", Map.of("reason", reason != null ? reason : ""))));
        invocation.source().sendMessage(messages.get("kick.issued", Map.of("player", name)));
    }

    private void handleWarn(Invocation invocation, UUID uuid, String name, String reason) {
        punishmentManager.warn(uuid, name, reason, PlayerResolver.operatorName(invocation.source()));
        invocation.source().sendMessage(messages.get("warn.issued", Map.of("player", name)));
        Optional<Player> online = proxyServer.getPlayer(uuid);
        online.ifPresent(player -> player.sendMessage(messages.get("warn.notice", Map.of("reason", reason != null ? reason : ""))));
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
