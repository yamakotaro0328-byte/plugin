package com.yamakotaro.ecorail.commands;

import com.yamakotaro.ecorail.Messages;
import com.yamakotaro.ecorail.stop.StopPoint;
import com.yamakotaro.ecorail.stop.StopPointManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EcoRailCommand implements CommandExecutor, TabCompleter {

    private static final double STOP_REMOVE_SEARCH_RADIUS = 5.0;

    private final JavaPlugin plugin;
    private final StopPointManager stopPointManager;
    private final Messages messages;

    public EcoRailCommand(JavaPlugin plugin, StopPointManager stopPointManager, Messages messages) {
        this.plugin = plugin;
        this.stopPointManager = stopPointManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecorail.admin")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.get("ecorail.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "stop" -> handleStop(sender, args);
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(messages.get("ecorail.reloaded", Map.of()));
            }
            default -> sender.sendMessage(messages.get("ecorail.usage", Map.of()));
        }
        return true;
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("stop.usage", Map.of()));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> handleStopCreate(sender, args);
            case "remove" -> handleStopRemove(sender);
            case "list" -> handleStopList(sender);
            default -> sender.sendMessage(messages.get("stop.usage", Map.of()));
        }
    }

    private void handleStopCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("stop.create-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[2]);
            if (seconds <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.get("stop.invalid-seconds", Map.of()));
            return;
        }
        Location location = player.getLocation();
        stopPointManager.create(new StopPoint(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), seconds));
        sender.sendMessage(messages.get("stop.created", Map.of("seconds", String.valueOf(seconds))));
    }

    private void handleStopRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Optional<StopPoint> removed = stopPointManager.removeNearest(player.getLocation(), STOP_REMOVE_SEARCH_RADIUS);
        sender.sendMessage(messages.get(removed.isPresent() ? "stop.removed" : "stop.not-found-nearby", Map.of()));
    }

    private void handleStopList(CommandSender sender) {
        var stops = stopPointManager.all();
        sender.sendMessage(messages.get("stop.list-header", Map.of("count", String.valueOf(stops.size()))));
        if (stops.isEmpty()) {
            sender.sendMessage(messages.get("stop.list-empty", Map.of()));
            return;
        }
        for (StopPoint stop : stops) {
            sender.sendMessage(messages.get("stop.list-entry", Map.of(
                    "world", stop.world(), "x", String.valueOf(stop.x()), "y", String.valueOf(stop.y()),
                    "z", String.valueOf(stop.z()), "seconds", String.valueOf(stop.dwellSeconds()))));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("stop", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            return filterPrefix(List.of("create", "remove", "list"), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
