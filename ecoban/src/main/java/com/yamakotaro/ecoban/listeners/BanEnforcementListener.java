package com.yamakotaro.ecoban.listeners;

import com.yamakotaro.ecoban.Messages;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Map;

/**
 * Rejects a banned player (by UUID or IP) before they finish connecting. Runs even when a
 * Velocity proxy is in front (see ecoban-velocity) as defense in depth - a proxy that isn't
 * running, or a direct connection to this server bypassing it, should still be covered.
 */
public class BanEnforcementListener implements Listener {

    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public BanEnforcementListener(PunishmentManager punishmentManager, Messages messages) {
        this.punishmentManager = punishmentManager;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Punishment ban = punishmentManager.checkBan(event.getUniqueId(), event.getAddress().getHostAddress());
        if (ban == null) {
            return;
        }
        String key = ban.isPermanent() ? "ban.kick-message-permanent" : "ban.kick-message-temporary";
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, messages.get(key, Map.of(
                "reason", ban.getReason() != null ? ban.getReason() : "",
                "operator", ban.getOperatorName() != null ? ban.getOperatorName() : "")));
    }
}
