package com.yamakotaro.serverkit.menu;

import com.yamakotaro.serverkit.Messages;
import com.yamakotaro.serverkit.ServerKitPlugin;
import com.yamakotaro.serverkit.claims.ClaimManager;
import com.yamakotaro.serverkit.dragonarena.DragonArenaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.UUID;

public class MenuListener implements Listener {

    private final ServerKitPlugin plugin;
    private final Messages messages;

    public MenuListener(ServerKitPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory() || event.getCurrentItem() == null) {
            return;
        }
        switch (holder.getMode()) {
            case MAIN -> handleMain(player, event.getSlot());
            case FREEZE -> handleFreeze(player, holder, event.getSlot());
            case CLAIMS -> handleClaims(player, holder, event.getSlot());
        }
    }

    private void handleMain(Player player, int slot) {
        switch (slot) {
            case 10 -> {
                if (plugin.getVanishManager() != null && player.hasPermission("serverkit.staff.vanish")) {
                    boolean nowVanished = plugin.getVanishManager().toggle(player);
                    player.sendMessage(messages.get(nowVanished ? "staff.vanish-on" : "staff.vanish-off", Map.of()));
                    player.closeInventory();
                }
            }
            case 11 -> {
                if (plugin.getStaffChatManager() != null && player.hasPermission("serverkit.staff.staffchat")) {
                    boolean nowOn = plugin.getStaffChatManager().toggle(player.getUniqueId());
                    player.sendMessage(messages.get(nowOn ? "staff.staffchat-mode-on" : "staff.staffchat-mode-off", Map.of()));
                    player.closeInventory();
                }
            }
            case 12 -> {
                if (plugin.getFreezeManager() != null && player.hasPermission("serverkit.staff.freeze")) {
                    player.openInventory(MenuBuilder.buildFreeze(plugin, messages).getInventory());
                }
            }
            case 14 -> {
                if (plugin.getDragonArenaManager() != null && player.hasPermission("serverkit.dragonfight")) {
                    handleDragonStart(player);
                }
            }
            case 15 -> {
                if (plugin.getDragonArenaManager() != null && player.hasPermission("serverkit.dragonfight")) {
                    handleDragonLeave(player);
                }
            }
            case 16 -> {
                if (plugin.getClaimManager() != null && player.hasPermission("serverkit.claims")) {
                    player.openInventory(MenuBuilder.buildClaims(plugin, messages, player).getInventory());
                }
            }
            case 22 -> player.closeInventory();
            default -> {
                // Unassigned slot; nothing to do.
            }
        }
    }

    private void handleDragonStart(Player player) {
        player.closeInventory();
        DragonArenaManager.StartOutcome outcome = plugin.getDragonArenaManager().start(player);
        switch (outcome.result()) {
            case NOT_LEADER -> player.sendMessage(messages.get("dragonarena.party-not-leader", Map.of()));
            case ALREADY_IN_FIGHT -> player.sendMessage(messages.get("dragonarena.already-in-fight", Map.of()));
            case ON_COOLDOWN -> player.sendMessage(messages.get("dragonarena.on-cooldown",
                    Map.of("seconds", String.valueOf(outcome.cooldownSecondsRemaining()))));
            case MAX_INSTANCES -> player.sendMessage(messages.get("dragonarena.max-instances", Map.of()));
            case WORLD_ERROR -> player.sendMessage(messages.get("dragonarena.world-error", Map.of()));
            case SUCCESS -> player.sendMessage(messages.get("dragonarena.starting", Map.of()));
        }
    }

    private void handleDragonLeave(Player player) {
        player.closeInventory();
        DragonArenaManager.LeaveResult result = plugin.getDragonArenaManager().leave(player);
        if (result == DragonArenaManager.LeaveResult.NOT_IN_FIGHT) {
            player.sendMessage(messages.get("dragonarena.not-in-fight", Map.of()));
        } else {
            player.sendMessage(messages.get("dragonarena.left-fight", Map.of()));
        }
    }

    private void handleFreeze(Player player, MenuHolder holder, int slot) {
        if (slot == 53) {
            player.openInventory(MenuBuilder.buildMain(plugin, messages, player).getInventory());
            return;
        }
        UUID target = holder.getFreezeTarget(slot);
        if (target == null || !player.hasPermission("serverkit.staff.freeze")) {
            return;
        }
        boolean nowFrozen = plugin.getFreezeManager().toggle(target);
        Player targetPlayer = plugin.getServer().getPlayer(target);
        player.sendMessage(messages.get(nowFrozen ? "staff.freeze-on" : "staff.freeze-off",
                Map.of("player", targetPlayer != null ? targetPlayer.getName() : "?")));
        if (targetPlayer != null) {
            targetPlayer.sendMessage(messages.get(nowFrozen ? "staff.you-were-frozen" : "staff.you-were-unfrozen", Map.of()));
        }
        player.openInventory(MenuBuilder.buildFreeze(plugin, messages).getInventory());
    }

    private void handleClaims(Player player, MenuHolder holder, int slot) {
        if (slot == 53) {
            player.openInventory(MenuBuilder.buildMain(plugin, messages, player).getInventory());
            return;
        }
        if (slot == 1) {
            player.getInventory().addItem(plugin.getClaimSelectionManager().createWand());
            player.sendMessage(messages.get("claims.wand-given", Map.of()));
            player.closeInventory();
            return;
        }
        String claimName = holder.getClaimName(slot);
        if (claimName == null) {
            return;
        }
        ClaimManager.RemoveResult result = plugin.getClaimManager().removeClaim(player.getUniqueId(), claimName);
        if (result == ClaimManager.RemoveResult.SUCCESS) {
            player.sendMessage(messages.get("claims.removed", Map.of("name", claimName)));
        }
        player.openInventory(MenuBuilder.buildClaims(plugin, messages, player).getInventory());
    }
}
