package com.yamakotaro.manhunt.commands;

import com.yamakotaro.manhunt.Messages;
import com.yamakotaro.manhunt.game.GameManager;
import com.yamakotaro.manhunt.game.ManhuntGame;
import com.yamakotaro.manhunt.game.Role;
import com.yamakotaro.manhunt.gui.StatusGuiBuilder;
import org.bukkit.Bukkit;
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
    private final StatusGuiBuilder statusGuiBuilder;

    public ManhuntCommand(JavaPlugin plugin, GameManager gameManager, Messages messages, StatusGuiBuilder statusGuiBuilder) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.messages = messages;
        this.statusGuiBuilder = statusGuiBuilder;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("manhunt.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "runner" -> handleSetRole(sender, args, Role.RUNNER);
            case "hunter" -> handleSetRole(sender, args, Role.HUNTER);
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

    private void handleSetRole(CommandSender sender, String[] args, Role role) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("manhunt.role-usage", Map.of()));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[1])));
            return;
        }
        String error = gameManager.setRole(target.getUniqueId(), role);
        if (error != null) {
            sender.sendMessage(messages.get(error, Map.of()));
            return;
        }
        String roleName = role == Role.RUNNER ? "runner" : "hunter";
        target.sendMessage(messages.get("manhunt.role-set-notice", Map.of("role", roleName)));
        if (!target.equals(sender)) {
            sender.sendMessage(messages.get("manhunt.role-set", Map.of("player", target.getName(), "role", roleName)));
        }
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
        if (sender instanceof Player player) {
            player.openInventory(statusGuiBuilder.build());
            return;
        }
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("runner", "hunter", "leave", "start", "stop", "status", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("runner") || args[0].equalsIgnoreCase("hunter"))) {
            return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
