package com.yamakotaro.ecolobby.listeners;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Bridges chat across every backend server running EcoLobby, using the "BungeeCord" plugin
 * messaging channel's "Forward" sub-command - the same mechanism BungeeConnector already relies
 * on for /hub, so no separate proxy-side plugin is needed here either (Velocity implements this
 * channel too, for Bukkit-plugin compatibility). A player's own message still shows locally
 * through vanilla chat as normal (this listener never cancels it); only the forwarded copy is
 * broadcast, and only on servers other than the one it came from - if a server ever receives its
 * own forwarded message back (some proxies include the origin server in an "ALL" forward), the
 * embedded origin-server name is compared against this server's own this-server-name and the
 * rebroadcast is skipped, so nothing is ever shown twice regardless of that proxy behavior.
 */
public class ChatSyncListener implements Listener, PluginMessageListener {

    private static final String CHANNEL = "EcoLobbyChat";

    private final EcoLobbyPlugin plugin;

    public ChatSyncListener(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public static String channel() {
        return CHANNEL;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!plugin.isFeatureEnabled("chat-sync")) {
            return;
        }
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        forward(event.getPlayer(), message);
    }

    private void forward(Player sender, String message) {
        String serverName = plugin.getConfig().getString("this-server-name", "server");

        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBytes);
        try {
            payload.writeUTF(serverName);
            payload.writeUTF(sender.getName());
            payload.writeUTF(message);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to build a chat-sync message", e);
            return;
        }

        ByteArrayOutputStream forwardBytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(forwardBytes);
        try {
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(CHANNEL);
            byte[] payloadArray = payloadBytes.toByteArray();
            out.writeShort(payloadArray.length);
            out.write(payloadArray);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to build a chat-sync forward packet", e);
            return;
        }
        sender.sendPluginMessage(plugin, "BungeeCord", forwardBytes.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL) || !plugin.isFeatureEnabled("chat-sync")) {
            return;
        }

        String originServer;
        String originPlayer;
        String text;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
        try {
            originServer = in.readUTF();
            originPlayer = in.readUTF();
            text = in.readUTF();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read a chat-sync message", e);
            return;
        }

        String thisServer = plugin.getConfig().getString("this-server-name", "server");
        if (originServer.equals(thisServer)) {
            return; // Our own message looping back through an "ALL" forward - already shown locally.
        }

        Component formatted = plugin.getMessages().formatChatSync(originServer, originPlayer, text);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(formatted);
        }
    }
}
