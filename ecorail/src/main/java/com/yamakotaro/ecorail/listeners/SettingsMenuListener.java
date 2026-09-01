package com.yamakotaro.ecorail.listeners;

import com.yamakotaro.ecorail.Messages;
import com.yamakotaro.ecorail.settings.PlayerSettings;
import com.yamakotaro.ecorail.settings.PlayerSettingsManager;
import com.yamakotaro.ecorail.settings.SettingsMenu;
import com.yamakotaro.ecorail.settings.SettingsMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class SettingsMenuListener implements Listener {

    private final PlayerSettingsManager settingsManager;
    private final Messages messages;

    public SettingsMenuListener(PlayerSettingsManager settingsManager, Messages messages) {
        this.settingsManager = settingsManager;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SettingsMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PlayerSettings current = settingsManager.get(player.getUniqueId());
        switch (event.getSlot()) {
            case SettingsMenu.ANTI_REVERSE_SLOT -> settingsManager.setAntiReverse(player.getUniqueId(), !current.antiReverse());
            case SettingsMenu.PLAYER_COLLISION_SLOT -> settingsManager.setPlayerCollision(player.getUniqueId(), !current.playerCollision());
            default -> {
                return;
            }
        }
        SettingsMenu.refresh(event.getInventory(), player.getUniqueId(), settingsManager, messages);
    }
}
