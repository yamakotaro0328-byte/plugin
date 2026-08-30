package com.yamakotaro.serverkit;

import com.yamakotaro.serverkit.menu.MenuBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * /serverkit reload: nothing in ServerKit hot-reloads config.yml on its own (Messages/managers
 * read plugin.getConfig() fresh on every call, but that in-memory config is only ever populated
 * from disk at startup) - this is the only way to pick up an edited config.yml without
 * restarting the whole server.
 * /serverkit menu: opens the cross-module GUI (vanish/staffchat/freeze/dragon fight/claims).
 */
public class ServerKitCommand implements CommandExecutor, TabCompleter {

    private final ServerKitPlugin plugin;
    private final Messages messages;

    public ServerKitCommand(ServerKitPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.get("general.players-only", Map.of()));
                return true;
            }
            if (!player.hasPermission("serverkit.menu")) {
                player.sendMessage(messages.get("general.no-permission", Map.of()));
                return true;
            }
            player.openInventory(MenuBuilder.buildMain(plugin, messages, player).getInventory());
            return true;
        }
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(List.of("reload", "menu"), args[0]);
        }
        return Collections.emptyList();
    }
}
