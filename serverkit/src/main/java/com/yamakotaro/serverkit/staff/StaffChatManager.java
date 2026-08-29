package com.yamakotaro.serverkit.staff;

import com.yamakotaro.serverkit.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class StaffChatManager {

    private final Plugin plugin;
    private final Messages messages;
    private final Set<UUID> toggledOn = new HashSet<>();

    public StaffChatManager(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public boolean isToggled(UUID uuid) {
        return toggledOn.contains(uuid);
    }

    /** @return true if staff chat mode is now on, false if it was turned off */
    public boolean toggle(UUID uuid) {
        if (toggledOn.remove(uuid)) {
            return false;
        }
        toggledOn.add(uuid);
        return true;
    }

    public void clear(UUID uuid) {
        toggledOn.remove(uuid);
    }

    public void broadcast(CommandSender sender, String message) {
        FileConfiguration config = plugin.getConfig();
        String format = config.getString("staffchat.format", "&8[&bSTAFF&8]&7 {player}&8: &f{message}")
                .replace("{player}", sender.getName())
                .replace("{message}", message);
        String legacy = format.replace('&', '§');
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.hasPermission("serverkit.staff.staffchat")) {
                viewer.sendMessage(legacy);
            }
        }
        Bukkit.getConsoleSender().sendMessage(legacy);
    }
}
