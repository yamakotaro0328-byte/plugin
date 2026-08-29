package com.yamakotaro.serverkit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Nothing in ServerKit hot-reloads config.yml on its own (Messages/managers read
 * plugin.getConfig() fresh on every call, but that in-memory config is only ever populated
 * from disk at startup) - this command is the only way to pick up an edited config.yml
 * (e.g. switching language) without restarting the whole server.
 */
public class ServerKitCommand implements CommandExecutor {

    private final ServerKitPlugin plugin;
    private final Messages messages;

    public ServerKitCommand(ServerKitPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverkit.admin")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(messages.get("general.reload-usage", Map.of()));
            return true;
        }
        plugin.reloadConfig();
        sender.sendMessage(messages.get("general.reloaded", Map.of()));
        return true;
    }
}
