package com.yamakotaro.ecorail.commands;

import com.yamakotaro.ecorail.Messages;
import com.yamakotaro.ecorail.items.TicketItemFactory;
import com.yamakotaro.ecorail.settings.PlayerSettingsManager;
import com.yamakotaro.ecorail.settings.SettingsMenu;
import com.yamakotaro.ecorail.station.Station;
import com.yamakotaro.ecorail.station.StationManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EcoRailCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final StationManager stationManager;
    private final TicketItemFactory ticketItemFactory;
    private final PlayerSettingsManager settingsManager;
    private final Messages messages;

    public EcoRailCommand(JavaPlugin plugin, StationManager stationManager, TicketItemFactory ticketItemFactory,
                           PlayerSettingsManager settingsManager, Messages messages) {
        this.plugin = plugin;
        this.stationManager = stationManager;
        this.ticketItemFactory = ticketItemFactory;
        this.settingsManager = settingsManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("ecorail.usage", Map.of()));
            return true;
        }
        // settings is a personal preference, not an admin action - everyone else needs ecorail.admin.
        if (args[0].equalsIgnoreCase("settings")) {
            handleSettings(sender);
            return true;
        }
        if (!sender.hasPermission("ecorail.admin")) {
            sender.sendMessage(messages.get("general.no-permission", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "station" -> handleStation(sender, args);
            case "ticket" -> handleTicket(sender, args);
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(messages.get("ecorail.reloaded", Map.of()));
            }
            default -> sender.sendMessage(messages.get("ecorail.usage", Map.of()));
        }
        return true;
    }

    private void handleSettings(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        SettingsMenu.open(player, settingsManager, messages);
    }

    private void handleStation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("station.usage", Map.of()));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> handleStationCreate(sender, args);
            case "remove" -> handleStationRemove(sender, args);
            case "list" -> handleStationList(sender);
            default -> sender.sendMessage(messages.get("station.usage", Map.of()));
        }
    }

    private void handleStationCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("station.create-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        String name = args[2];
        if (stationManager.find(name).isPresent()) {
            sender.sendMessage(messages.get("station.already-exists", Map.of("name", name)));
            return;
        }
        Location location = player.getLocation();
        int[] direction = cardinalDirection(location.getYaw());
        Station station = new Station(StationManager.normalize(name), name, location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), direction[0], direction[1]);
        stationManager.create(station);
        sender.sendMessage(messages.get("station.created", Map.of("name", name, "direction", directionName(direction, messages))));
    }

    private void handleStationRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("station.remove-usage", Map.of()));
            return;
        }
        String name = args[2];
        boolean removed = stationManager.remove(name);
        sender.sendMessage(messages.get(removed ? "station.removed" : "station.not-found", Map.of("name", name)));
    }

    private void handleStationList(CommandSender sender) {
        var stations = stationManager.all();
        sender.sendMessage(messages.get("station.list-header", Map.of("count", String.valueOf(stations.size()))));
        if (stations.isEmpty()) {
            sender.sendMessage(messages.get("station.list-empty", Map.of()));
            return;
        }
        for (Station station : stations) {
            sender.sendMessage(messages.get("station.list-entry", Map.of(
                    "name", station.name(), "world", station.world(),
                    "x", String.valueOf(station.x()), "y", String.valueOf(station.y()), "z", String.valueOf(station.z()))));
        }
    }

    private void handleTicket(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(messages.get("ticket.give-usage", Map.of()));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(messages.get("ticket.give-usage", Map.of()));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(messages.get("general.player-not-found", Map.of("player", args[2])));
            return;
        }
        Optional<Station> from = stationManager.find(args[3]);
        if (from.isEmpty()) {
            sender.sendMessage(messages.get("station.not-found", Map.of("name", args[3])));
            return;
        }
        Optional<Station> to = stationManager.find(args[4]);
        if (to.isEmpty()) {
            sender.sendMessage(messages.get("station.not-found", Map.of("name", args[4])));
            return;
        }
        ItemStack ticket = ticketItemFactory.create(from.get().id(), to.get().id());
        target.getInventory().addItem(ticket);
        target.sendMessage(messages.get("ticket.received", Map.of("from", from.get().name(), "to", to.get().name())));
        sender.sendMessage(messages.get("ticket.gave", Map.of(
                "player", target.getName(), "from", from.get().name(), "to", to.get().name())));
    }

    private static int[] cardinalDirection(float yaw) {
        float normalized = ((yaw % 360) + 360) % 360;
        if (normalized >= 315 || normalized < 45) {
            return new int[]{0, 1};
        } else if (normalized < 135) {
            return new int[]{-1, 0};
        } else if (normalized < 225) {
            return new int[]{0, -1};
        } else {
            return new int[]{1, 0};
        }
    }

    private static String directionName(int[] direction, Messages messages) {
        if (direction[1] == 1) return messages.raw("station.direction-south", Map.of());
        if (direction[0] == -1) return messages.raw("station.direction-west", Map.of());
        if (direction[1] == -1) return messages.raw("station.direction-north", Map.of());
        return messages.raw("station.direction-east", Map.of());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("station", "ticket", "reload", "settings"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("station")) {
            return filterPrefix(List.of("create", "remove", "list"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ticket")) {
            return filterPrefix(List.of("give"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("station") && args[1].equalsIgnoreCase("remove")) {
            return filterPrefix(stationManager.all().stream().map(Station::name).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ticket") && args[1].equalsIgnoreCase("give")) {
            return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if ((args.length == 4 || args.length == 5) && args[0].equalsIgnoreCase("ticket") && args[1].equalsIgnoreCase("give")) {
            return filterPrefix(stationManager.all().stream().map(Station::name).toList(), args[args.length - 1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
