package com.yamakotaro.ecoevents.commands;

import com.yamakotaro.ecoevents.Messages;
import com.yamakotaro.ecoevents.event.EventDefinition;
import com.yamakotaro.ecoevents.event.EventManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final EventManager eventManager;
    private final Messages messages;

    public EventCommand(JavaPlugin plugin, EventManager eventManager, Messages messages) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("event.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "trigger" -> handleTrigger(sender, args);
            case "toggle" -> handleToggle(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(messages.get("event.usage", Map.of()));
        }
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("ecoevents.admin")) {
            return true;
        }
        sender.sendMessage(messages.get("general.no-permission", Map.of()));
        return false;
    }

    private void handleTrigger(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("event.trigger-usage", Map.of()));
            return;
        }
        Optional<EventDefinition> defOpt = eventManager.find(args[1]);
        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.get("event.not-found", Map.of("id", args[1])));
            return;
        }
        eventManager.fireEvent(defOpt.get());
        sender.sendMessage(messages.get("event.triggered", Map.of("event", defOpt.get().displayName())));
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("event.toggle-usage", Map.of()));
            return;
        }
        Optional<EventDefinition> defOpt = eventManager.find(args[1]);
        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.get("event.not-found", Map.of("id", args[1])));
            return;
        }
        boolean nowEnabled = eventManager.toggle(defOpt.get().id());
        sender.sendMessage(messages.get(nowEnabled ? "event.toggled-on" : "event.toggled-off",
                Map.of("event", defOpt.get().displayName())));
    }

    private void handleList(CommandSender sender) {
        Collection<EventDefinition> events = eventManager.all();
        sender.sendMessage(messages.get("event.list-header", Map.of("count", String.valueOf(events.size()))));
        for (EventDefinition def : events) {
            String badge = messages.raw(eventManager.isEnabled(def.id()) ? "event.badge-enabled" : "event.badge-disabled", Map.of());
            sender.sendMessage(messages.get("event.list-entry", Map.of("id", def.id(), "status", badge, "name", def.displayName())));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reloadConfig();
        eventManager.load();
        sender.sendMessage(messages.get("event.reloaded", Map.of()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("trigger", "toggle", "list", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trigger") || args[0].equalsIgnoreCase("toggle"))) {
            return filterPrefix(eventManager.all().stream().map(EventDefinition::id).toList(), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
