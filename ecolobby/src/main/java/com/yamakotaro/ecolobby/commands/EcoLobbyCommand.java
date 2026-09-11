package com.yamakotaro.ecolobby.commands;

import com.yamakotaro.ecolobby.EcoLobbyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** /ecolobby <reload|setspawn> - admin utilities. */
public class EcoLobbyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "setspawn");

    private final EcoLobbyPlugin plugin;

    public EcoLobbyCommand(EcoLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecolobby.admin")) {
            sender.sendMessage(plugin.getMessages().get("general.no-permission", Map.of()));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessages().get("general.usage", Map.of()));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.getMessages().get("general.reloaded", Map.of()));
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessages().get("general.players-only", Map.of()));
                    return true;
                }
                plugin.getSpawnManager().setSpawn(player.getLocation());
                player.sendMessage(plugin.getMessages().get("setspawn.success", Map.of()));
            }
            default -> sender.sendMessage(plugin.getMessages().get("general.usage", Map.of()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase();
        return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(prefix)).toList();
    }
}
