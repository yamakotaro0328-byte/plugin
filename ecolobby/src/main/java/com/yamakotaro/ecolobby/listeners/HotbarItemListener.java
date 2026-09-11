package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import com.yamakotaro.ecolobby.LobbyMenuConfig;
import com.yamakotaro.ecolobby.Messages;
import com.yamakotaro.ecolobby.gui.AdminPanelHolder;
import com.yamakotaro.ecolobby.gui.LobbyMenuHolder;
import com.yamakotaro.ecolobby.gui.ServerMenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/** Right-clicking the compass/book handed out on join (see JoinListener) opens the server-select
 * menu or prints the links list - identified by a PersistentDataContainer marker rather than
 * material alone, so a player carrying an unrelated compass/book from another server isn't
 * mistaken for holding the lobby's own item. */
public class HotbarItemListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public HotbarItemListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        Player player = event.getPlayer();

        var pdc = meta.getPersistentDataContainer();
        if (Boolean.TRUE.equals(pdc.get(plugin.getServerMenuItemKey(), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            openServerMenu(player);
        } else if (Boolean.TRUE.equals(pdc.get(plugin.getLinksMenuItemKey(), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            sendLinks(player);
        } else if (Boolean.TRUE.equals(pdc.get(plugin.getLobbyMenuItemKey(), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            player.openInventory(new LobbyMenuHolder(plugin.getMessages()).getInventory());
        } else if (Boolean.TRUE.equals(pdc.get(plugin.getVisibilityToggleItemKey(), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            toggleVisibility(player);
        } else if (Boolean.TRUE.equals(pdc.get(plugin.getQuickReturnItemKey(), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            quickReturn(player);
        } else if (Boolean.TRUE.equals(pdc.get(plugin.getAdminPanelItemKey(), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            openAdminPanel(player);
        }
    }

    private void toggleVisibility(Player player) {
        Messages messages = plugin.getMessages();
        boolean nowHidden = plugin.getPlayerStateManager().toggleVisibility(player);
        player.sendMessage(messages.get(nowHidden ? "toggle.visibility-hidden" : "toggle.visibility-visible", Map.of()));
    }

    private void quickReturn(Player player) {
        Messages messages = plugin.getMessages();
        var spawn = plugin.getSpawnManager().getSpawn();
        if (spawn.isEmpty()) {
            player.sendMessage(messages.get("hub.not-configured", Map.of()));
            return;
        }
        player.teleport(spawn.get());
        player.sendMessage(messages.get("hub.teleported", Map.of()));
    }

    private void openAdminPanel(Player player) {
        if (!player.hasPermission("ecolobby.admin")) {
            player.sendMessage(plugin.getMessages().get("general.no-permission", Map.of()));
            return;
        }
        player.openInventory(new AdminPanelHolder(plugin).getInventory());
    }

    private void openServerMenu(Player player) {
        Messages messages = plugin.getMessages();
        player.openInventory(new ServerMenuHolder(messages, plugin.getMenuConfig().servers()).getInventory());
    }

    private void sendLinks(Player player) {
        Messages messages = plugin.getMessages();
        player.sendMessage(messages.get("menu.links-header", Map.of()));
        for (LobbyMenuConfig.LinkEntry link : plugin.getMenuConfig().links()) {
            Component line = messages.color(link.label()).clickEvent(ClickEvent.openUrl(link.url()));
            player.sendMessage(line);
        }
    }
}
