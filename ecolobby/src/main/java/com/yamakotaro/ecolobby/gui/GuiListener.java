package com.yamakotaro.ecolobby.gui;

import com.yamakotaro.ecolobby.BungeeConnector;
import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import com.yamakotaro.ecolobby.LobbyMenuConfig;
import com.yamakotaro.ecolobby.Messages;
import com.yamakotaro.ecolobby.PlayerStateManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Click/drag handling for every EcoLobby GUI: the server-select menu, the lobby-menu hub and its
 * sub-menus, the admin panel, the player list, and the leave-server confirmation. */
public class GuiListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public GuiListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isOwnHolder(event.getInventory().getHolder())) {
            event.setCancelled(true);
        }
    }

    private boolean isOwnHolder(InventoryHolder holder) {
        return holder instanceof ServerMenuHolder || holder instanceof LobbyMenuHolder
                || holder instanceof AdminPanelHolder || holder instanceof ParticleMenuHolder
                || holder instanceof PlayerListHolder || holder instanceof ConfirmLeaveHolder;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!isOwnHolder(holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return; // Ignore clicks in the player's own inventory below the menu.
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();

        if (holder instanceof ServerMenuHolder serverMenu) {
            String serverName = serverMenu.serverNameAt(slot);
            if (serverName != null) {
                player.closeInventory();
                BungeeConnector.connect(plugin, player, serverName);
            }
        } else if (holder instanceof LobbyMenuHolder lobbyMenu) {
            handleLobbyMenuClick(player, lobbyMenu.actionAt(slot));
        } else if (holder instanceof ParticleMenuHolder particleMenu) {
            handleParticleClick(player, particleMenu, slot);
        } else if (holder instanceof ConfirmLeaveHolder) {
            handleLeaveClick(player, slot);
        } else if (holder instanceof AdminPanelHolder adminPanel) {
            handleAdminPanelClick(player, adminPanel, slot);
        }
        // PlayerListHolder: display-only, nothing to do on click.
    }

    private void handleLobbyMenuClick(Player player, String action) {
        if (action == null) {
            return;
        }
        Messages messages = plugin.getMessages();
        switch (action) {
            case "particle-trail" -> player.openInventory(new ParticleMenuHolder(plugin, messages).getInventory());
            case "player-list" -> player.openInventory(new PlayerListHolder(messages).getInventory());
            case "leave-server" -> player.openInventory(new ConfirmLeaveHolder(messages).getInventory());
            case "random-teleport" -> {
                player.closeInventory();
                randomTeleport(player);
            }
            case "rules-book" -> {
                player.closeInventory();
                for (String line : messages.rawList("rules.lines")) {
                    player.sendMessage(messages.color(line));
                }
            }
            case "server-info" -> {
                player.closeInventory();
                sendServerInfo(player);
            }
            case "double-jump-toggle" -> {
                boolean enabled = plugin.getPlayerStateManager().toggleDoubleJump(player.getUniqueId());
                player.sendMessage(messages.get(enabled ? "toggle.double-jump-on" : "toggle.double-jump-off", Map.of()));
            }
            case "night-vision-toggle" -> {
                boolean enabled = plugin.getPlayerStateManager().toggleNightVision(player);
                player.sendMessage(messages.get(enabled ? "toggle.night-vision-on" : "toggle.night-vision-off", Map.of()));
            }
            case "vote-menu" -> {
                player.closeInventory();
                sendVoteLinks(player);
            }
            default -> {
            }
        }
    }

    private void randomTeleport(Player player) {
        Messages messages = plugin.getMessages();
        LobbyMenuConfig config = plugin.getMenuConfig();
        List<org.bukkit.Location> points = config.randomTeleportPoints();
        if (points.isEmpty()) {
            player.sendMessage(messages.get("teleport.random-none", Map.of()));
            return;
        }
        org.bukkit.Location target = points.get((int) (Math.random() * points.size()));
        player.teleport(target);
        player.sendMessage(messages.get("teleport.random-done", Map.of()));
    }

    private void sendServerInfo(Player player) {
        Messages messages = plugin.getMessages();
        long uptimeMillis = System.currentTimeMillis() - plugin.getEnabledAtMillis();
        Duration uptime = Duration.ofMillis(uptimeMillis);
        String uptimeText = uptime.toHours() + "h " + (uptime.toMinutesPart()) + "m";
        player.sendMessage(messages.get("info.message", Map.of(
                "online", String.valueOf(Bukkit.getOnlinePlayers().size()),
                "max", String.valueOf(Bukkit.getMaxPlayers()),
                "uptime", uptimeText)));
    }

    private void sendVoteLinks(Player player) {
        Messages messages = plugin.getMessages();
        player.sendMessage(messages.get("menu.vote-header", Map.of()));
        for (LobbyMenuConfig.LinkEntry link : plugin.getMenuConfig().voteLinks()) {
            Component line = messages.color(link.label()).clickEvent(ClickEvent.openUrl(link.url()));
            player.sendMessage(line);
        }
    }

    private void handleParticleClick(Player player, ParticleMenuHolder menu, int slot) {
        if (!menu.isKnownSlot(slot)) {
            return;
        }
        Particle particle = menu.particleAt(slot);
        PlayerStateManager state = plugin.getPlayerStateManager();
        state.setTrail(player.getUniqueId(), particle);
        Messages messages = plugin.getMessages();
        player.sendMessage(messages.get(particle == null ? "particle.disabled" : "particle.selected", Map.of()));
        player.closeInventory();
    }

    private void handleLeaveClick(Player player, int slot) {
        if (slot == ConfirmLeaveHolder.SLOT_CONFIRM) {
            player.kick(plugin.getMessages().get("leave.kick-message", Map.of()));
        } else if (slot == ConfirmLeaveHolder.SLOT_CANCEL) {
            player.closeInventory();
        }
    }

    private void handleAdminPanelClick(Player player, AdminPanelHolder panel, int slot) {
        if (!player.hasPermission("ecolobby.admin")) {
            player.closeInventory();
            return;
        }
        Messages messages = plugin.getMessages();
        if (slot == AdminPanelHolder.SLOT_CLOSE) {
            player.closeInventory();
        } else if (slot == AdminPanelHolder.SLOT_SET_SPAWN) {
            panel.setSpawnHere(player);
            player.sendMessage(messages.get("admin.set-spawn-done", Map.of()));
        } else if (slot == AdminPanelHolder.SLOT_RELOAD) {
            plugin.reload();
            player.sendMessage(messages.get("general.reloaded", Map.of()));
        } else {
            panel.toggleIfApplicable(slot);
        }
    }
}
