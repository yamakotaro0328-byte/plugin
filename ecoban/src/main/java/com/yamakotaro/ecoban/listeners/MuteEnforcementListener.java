package com.yamakotaro.ecoban.listeners;

import com.yamakotaro.ecoban.Messages;
import com.yamakotaro.ecoban.core.Punishment;
import com.yamakotaro.ecoban.core.PunishmentManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;

public class MuteEnforcementListener implements Listener {

    private final PunishmentManager punishmentManager;
    private final Messages messages;

    public MuteEnforcementListener(PunishmentManager punishmentManager, Messages messages) {
        this.punishmentManager = punishmentManager;
        this.messages = messages;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("ecoban.exempt")) {
            return;
        }
        Punishment mute = punishmentManager.checkMute(player.getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(messages.get(mute.isPermanent() ? "mute.blocked-permanent" : "mute.blocked-temporary", Map.of(
                "reason", mute.getReason() != null ? mute.getReason() : "")));
    }
}
