package com.yamakotaro.ecolobby.commands;

import com.yamakotaro.ecolobby.BungeeConnector;
import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** /hub (alias /lobby): teleports to the lobby spawn if this IS the lobby server, otherwise sends
 * the player there over the network - see EcoLobbyPlugin#isLobbyServer. */
public class HubCommand implements CommandExecutor {

    private final EcoLobbyPlugin plugin;

    public HubCommand(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only", Map.of()));
            return true;
        }
        if (!player.hasPermission("ecolobby.use")) {
            player.sendMessage(plugin.getMessages().get("general.no-permission", Map.of()));
            return true;
        }

        if (plugin.isLobbyServer()) {
            var spawn = plugin.getSpawnManager().getSpawn();
            if (spawn.isEmpty()) {
                player.sendMessage(plugin.getMessages().get("hub.not-configured", Map.of()));
                return true;
            }
            player.teleport(spawn.get());
            player.sendMessage(plugin.getMessages().get("hub.teleported", Map.of()));
            return true;
        }

        player.sendMessage(plugin.getMessages().get("hub.sending", Map.of()));
        BungeeConnector.connect(plugin, player, plugin.lobbyServerName());
        return true;
    }
}
