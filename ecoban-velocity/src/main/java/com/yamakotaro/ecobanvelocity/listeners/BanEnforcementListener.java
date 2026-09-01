package com.yamakotaro.ecobanvelocity.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecobanvelocity.Messages;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Rejects a banned player (by UUID or IP) before they're allowed to connect to any backend
 * server - this is what makes the ban network-wide rather than per-server.
 */
public class BanEnforcementListener {

    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public BanEnforcementListener(PunishmentManager punishmentManager, Messages messages) {
        this.punishmentManager = punishmentManager;
        this.messages = messages;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        InetSocketAddress address = player.getRemoteAddress();
        Punishment ban = punishmentManager.checkBan(player.getUniqueId(), address.getAddress().getHostAddress());
        if (ban == null) {
            return;
        }
        String key = ban.isPermanent() ? "ban.kick-message-permanent" : "ban.kick-message-temporary";
        event.setResult(ResultedEvent.ComponentResult.denied(messages.get(key, Map.of(
                "reason", ban.getReason() != null ? ban.getReason() : "",
                "operator", ban.getOperatorName() != null ? ban.getOperatorName() : ""))));
    }
}
