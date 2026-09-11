package com.yamakotaro.ecolobby;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Sends a player to another server over the network via the legacy "BungeeCord" plugin messaging
 * channel - Velocity (and BungeeCord itself) both understand this "Connect" sub-channel without
 * needing any proxy-side plugin installed, which is why this whole feature works from a plain
 * backend Paper plugin alone.
 */
public final class BungeeConnector {

    private BungeeConnector() {
    }

    public static final String CHANNEL = "BungeeCord";

    public static void register(JavaPlugin plugin) {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public static void connect(JavaPlugin plugin, Player player, String serverName) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteStream);
        try {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to build a server-connect plugin message", e);
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, byteStream.toByteArray());
    }
}
