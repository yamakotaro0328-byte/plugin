package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import com.yamakotaro.ecolobby.Messages;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.Map;

/** On join (lobby server only, see EcoLobbyPlugin#onEnable): teleports to the lobby spawn, resets
 * the player to a clean slate, and hands out the server-select/links items. */
public class JoinListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public JoinListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isLobbyServer()) {
            return;
        }
        Player player = event.getPlayer();
        Messages messages = plugin.getMessages();

        if (plugin.isFeatureEnabled("join-broadcast")) {
            event.joinMessage(messages.get("join.broadcast", Map.of("player", player.getName())));
        } else {
            event.joinMessage(null);
        }

        plugin.getSpawnManager().getSpawn().ifPresent(player::teleport);

        if (plugin.isFeatureEnabled("clear-inventory-on-join")) {
            player.getInventory().clear();
            player.setFireTicks(0);
            player.setFallDistance(0);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttribute != null) {
                player.setHealth(maxHealthAttribute.getValue());
            }
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }

        if (plugin.isFeatureEnabled("double-jump")) {
            player.setAllowFlight(true);
        }

        if (plugin.isFeatureEnabled("server-menu")) {
            giveMenuItem(player, plugin.getServerMenuItemKey(), "items.server-menu-slot", "items.server-menu-material",
                    "menu.server-title");
        }
        if (plugin.isFeatureEnabled("links-menu")) {
            giveMenuItem(player, plugin.getLinksMenuItemKey(), "items.links-menu-slot", "items.links-menu-material",
                    "menu.links-title");
        }
        if (plugin.isFeatureEnabled("lobby-menu")) {
            giveMenuItem(player, plugin.getLobbyMenuItemKey(), "items.lobby-menu-slot", "items.lobby-menu-material",
                    "menu.lobby-title");
        }
        if (plugin.isFeatureEnabled("visibility-toggle")) {
            giveMenuItem(player, plugin.getVisibilityToggleItemKey(), "items.visibility-toggle-slot",
                    "items.visibility-toggle-material", "menu.visibility-toggle-title");
        }
        if (plugin.isFeatureEnabled("quick-return")) {
            giveMenuItem(player, plugin.getQuickReturnItemKey(), "items.quick-return-slot",
                    "items.quick-return-material", "menu.quick-return-title");
        }
        if (player.hasPermission("ecolobby.admin")) {
            giveMenuItem(player, plugin.getAdminPanelItemKey(), "items.admin-panel-slot",
                    "items.admin-panel-material", "menu.admin-panel-title");
        }
        if (plugin.isFeatureEnabled("ender-arrow")) {
            giveEnderArrowBow(player);
        }

        plugin.getPlayerStateManager().applyVisibilityTo(player);

        if (plugin.isFeatureEnabled("join-title")) {
            player.showTitle(Title.title(
                    messages.get("join.title", Map.of("player", player.getName())),
                    messages.get("join.subtitle", Map.of("online", String.valueOf(Bukkit.getOnlinePlayers().size()))),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        }
    }

    private void giveEnderArrowBow(Player player) {
        Messages messages = plugin.getMessages();
        int slot = plugin.getConfig().getInt("items.ender-arrow-slot", 2);

        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.displayName(messages.get("menu.ender-arrow-title", Map.of()));
        meta.getPersistentDataContainer().set(plugin.getEnderArrowBowKey(), PersistentDataType.BOOLEAN, true);
        bow.setItemMeta(meta);
        player.getInventory().setItem(slot, bow);
        player.getInventory().addItem(new ItemStack(Material.ARROW, 64));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerStateManager().clearOnQuit(event.getPlayer().getUniqueId());
    }

    private void giveMenuItem(Player player, NamespacedKey key, String slotConfigPath,
                               String materialConfigPath, String titleMessagePath) {
        Messages messages = plugin.getMessages();
        String materialName = plugin.getConfig().getString(materialConfigPath, "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.STONE;
        }
        int slot = plugin.getConfig().getInt(slotConfigPath, 0);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get(titleMessagePath, Map.of()));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);
    }
}
