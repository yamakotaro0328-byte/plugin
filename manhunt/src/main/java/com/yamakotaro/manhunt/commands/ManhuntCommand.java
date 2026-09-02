package com.yamakotaro.manhunt.commands;

import com.yamakotaro.manhunt.Messages;
import com.yamakotaro.manhunt.game.GameManager;
import com.yamakotaro.manhunt.game.ManhuntGame;
import com.yamakotaro.manhunt.game.Role;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ManhuntCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final Messages messages;

    public ManhuntCommand(JavaPlugin plugin, GameManager gameManager, Messages messages) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("manhunt.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "role" -> handleRole(sender, args);
            case "leave" -> handleLeave(sender);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(messages.get("manhunt.usage", Map.of()));
        }
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("manhunt.admin")) {
            return true;
        }
        sender.sendMessage(messages.get("general.no-permission", Map.of()));
        return false;
    }

    private void handleRole(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        if (args.length < 2 || !isRole(args[1])) {
            sender.sendMessage(messages.get("manhunt.role-usage", Map.of()));
            return;
        }
        Role role = args[1].equalsIgnoreCase("runner") ? Role.RUNNER : Role.HUNTER;
        String error = gameManager.setRole(player.getUniqueId(), role);
        sender.sendMessage(messages.get(error != null ? error : "manhunt.role-set",
                Map.of("role", args[1].toLowerCase())));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        gameManager.leave(player.getUniqueId());
        sender.sendMessage(messages.get("manhunt.left", Map.of()));
    }

    private void handleStart(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        long headStartSeconds = plugin.getConfig().getLong("head-start-seconds", 60);
        String error = gameManager.start(headStartSeconds);
        // On success the game-wide broadcast already covers the confirmation; only errors need
        // a reply here.
        if (error != null) {
            sender.sendMessage(messages.get(error, Map.of()));
        }
    }

    private void handleStop(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        String error = gameManager.stopManually();
        if (error != null) {
            sender.sendMessage(messages.get(error, Map.of()));
        }
    }

    private void handleStatus(CommandSender sender) {
        ManhuntGame game = gameManager.game();
        long runners = game.getRoles().values().stream().filter(role -> role == Role.RUNNER).count();
        long hunters = game.getRoles().values().stream().filter(role -> role == Role.HUNTER).count();
        String stateKey = !game.isRunning() ? "manhunt.status-waiting"
                : game.isHeadStartActive() ? "manhunt.status-headstart" : "manhunt.status-running";
        sender.sendMessage(messages.get(stateKey, Map.of()));
        sender.sendMessage(messages.get("manhunt.status-counts",
                Map.of("runners", String.valueOf(runners), "hunters", String.valueOf(hunters))));
    }

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage(messages.get("manhunt.reloaded", Map.of()));
    }

    private static boolean isRole(String value) {
        return value.equalsIgnoreCase("runner") || value.equalsIgnoreCase("hunter");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("role", "leave", "start", "stop", "status", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("role")) {
            return filterPrefix(List.of("runner", "hunter"), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
