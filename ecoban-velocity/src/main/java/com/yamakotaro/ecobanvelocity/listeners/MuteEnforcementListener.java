package com.yamakotaro.ecobanvelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import com.yamakotaro.ecobanvelocity.Messages;

import java.util.Map;

/**
 * Blocks chat network-wide for a muted player, before it's forwarded to whichever backend
 * server they're currently on.
 */
public class MuteEnforcementListener {

    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public MuteEnforcementListener(PunishmentManager punishmentManager, Messages messages) {
        this.punishmentManager = punishmentManager;
        this.messages = messages;
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("ecoban.exempt")) {
            return;
        }
        Punishment mute = punishmentManager.checkMute(player.getUniqueId());
        if (mute == null) {
            return;
        }
        event.setResult(PlayerChatEvent.ChatResult.denied());
        player.sendMessage(messages.get(mute.isPermanent() ? "mute.blocked-permanent" : "mute.blocked-temporary", Map.of(
                "reason", mute.getReason() != null ? mute.getReason() : "")));
    }
}
