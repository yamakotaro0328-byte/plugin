package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import com.yamakotaro.ecolobby.LobbyMenuConfig;
import com.yamakotaro.ecolobby.Messages;
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

        if (Boolean.TRUE.equals(meta.getPersistentDataContainer().get(plugin.getServerMenuItemKey(), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            openServerMenu(player);
        } else if (Boolean.TRUE.equals(meta.getPersistentDataContainer().get(plugin.getLinksMenuItemKey(), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            sendLinks(player);
        }
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
