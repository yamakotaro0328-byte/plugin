package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/**
 * Shares a player's main inventory, armor, off-hand item and ender chest between the specific
 * backend servers listed in inventory-sync-servers - not via a database, but one YAML file per
 * player under inventory-sync-directory (Bukkit's ItemStack is natively YAML-serializable). This
 * only actually syncs across separate server processes if that directory is a genuinely shared
 * location (the same disk, or a mounted network path) - each server otherwise only ever sees its
 * own local copy of the folder, and this feature quietly does nothing useful in that case.
 */
public class InventorySyncListener implements Listener {

    private final EcoLobbyPlugin plugin;

    public InventorySyncListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!isActive()) {
            return;
        }
        File file = fileFor(event.getPlayer());
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        Player player = event.getPlayer();
        player.getInventory().setContents(toItemStackArray(data.getList("contents")));
        player.getInventory().setArmorContents(toItemStackArray(data.getList("armor")));
        ItemStack offHand = data.getItemStack("offhand");
        player.getInventory().setItemInOffHand(offHand != null ? offHand : new ItemStack(Material.AIR));
        player.getEnderChest().setContents(toItemStackArray(data.getList("enderchest")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!isActive()) {
            return;
        }
        Player player = event.getPlayer();
        YamlConfiguration data = new YamlConfiguration();
        data.set("contents", player.getInventory().getContents());
        data.set("armor", player.getInventory().getArmorContents());
        data.set("offhand", player.getInventory().getItemInOffHand());
        data.set("enderchest", player.getEnderChest().getContents());
        try {
            data.save(fileFor(player));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save inventory-sync data for " + player.getName(), e);
        }
    }

    private boolean isActive() {
        if (!plugin.isFeatureEnabled("inventory-sync")) {
            return false;
        }
        String thisServer = plugin.getConfig().getString("this-server-name", "server");
        return plugin.getConfig().getStringList("inventory-sync-servers").contains(thisServer);
    }

    private File fileFor(Player player) {
        File directory = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("inventory-sync-directory", "inventory-sync"));
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return new File(directory, player.getUniqueId() + ".yml");
    }

    private ItemStack[] toItemStackArray(List<?> list) {
        if (list == null) {
            return new ItemStack[0];
        }
        ItemStack[] array = new ItemStack[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            array[i] = element instanceof ItemStack stack ? stack : null;
        }
        return array;
    }
}
