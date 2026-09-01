package com.yamakotaro.ecorail.commands;

import com.yamakotaro.ecorail.Messages;
import com.yamakotaro.ecorail.cart.CartManager;
import com.yamakotaro.ecorail.cart.ManagedCart;
import com.yamakotaro.ecorail.stop.StopPoint;
import com.yamakotaro.ecorail.stop.StopPointManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EcoRailCommand implements CommandExecutor, TabCompleter {

    private static final double COMMAND_SEARCH_RADIUS = 5.0;

    private final JavaPlugin plugin;
    private final StopPointManager stopPointManager;
    private final CartManager cartManager;
    private final Messages messages;

    public EcoRailCommand(JavaPlugin plugin, StopPointManager stopPointManager, CartManager cartManager, Messages messages) {
        this.plugin = plugin;
        this.stopPointManager = stopPointManager;
        this.cartManager = cartManager;
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
            case "cart" -> handleCart(sender, args);
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
        Optional<StopPoint> removed = stopPointManager.removeNearest(player.getLocation(), COMMAND_SEARCH_RADIUS);
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

    private void handleCart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("cart.usage", Map.of()));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "mark" -> handleCartMark(sender);
            case "unmark" -> handleCartUnmark(sender);
            default -> sender.sendMessage(messages.get("cart.usage", Map.of()));
        }
    }

    private void handleCartMark(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Minecart nearest = findNearestMinecart(player);
        if (nearest == null) {
            sender.sendMessage(messages.get("cart.no-cart-nearby", Map.of()));
            return;
        }
        if (cartManager.isManaged(nearest.getUniqueId())) {
            sender.sendMessage(messages.get("cart.already-managed", Map.of()));
            return;
        }
        int[] direction = directionOf(nearest, player);
        Location location = nearest.getLocation();
        cartManager.register(new ManagedCart(nearest.getUniqueId(), location.getWorld().getName(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4, direction[0], direction[1]));
        sender.sendMessage(messages.get("cart.marked", Map.of()));
    }

    private void handleCartUnmark(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Minecart nearest = findNearestManagedMinecart(player);
        if (nearest == null) {
            sender.sendMessage(messages.get("cart.no-managed-cart-nearby", Map.of()));
            return;
        }
        cartManager.unregisterAndRelease(nearest.getUniqueId(), plugin);
        sender.sendMessage(messages.get("cart.unmarked", Map.of()));
    }

    private Minecart findNearestMinecart(Player player) {
        Minecart nearest = null;
        double nearestDistanceSquared = COMMAND_SEARCH_RADIUS * COMMAND_SEARCH_RADIUS;
        for (Entity entity : player.getNearbyEntities(COMMAND_SEARCH_RADIUS, COMMAND_SEARCH_RADIUS, COMMAND_SEARCH_RADIUS)) {
            if (!(entity instanceof Minecart minecart)) {
                continue;
            }
            double distanceSquared = entity.getLocation().distanceSquared(player.getLocation());
            if (distanceSquared <= nearestDistanceSquared) {
                nearest = minecart;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private Minecart findNearestManagedMinecart(Player player) {
        Minecart nearest = null;
        double nearestDistanceSquared = COMMAND_SEARCH_RADIUS * COMMAND_SEARCH_RADIUS;
        for (Entity entity : player.getNearbyEntities(COMMAND_SEARCH_RADIUS, COMMAND_SEARCH_RADIUS, COMMAND_SEARCH_RADIUS)) {
            if (!(entity instanceof Minecart minecart) || !cartManager.isManaged(minecart.getUniqueId())) {
                continue;
            }
            double distanceSquared = entity.getLocation().distanceSquared(player.getLocation());
            if (distanceSquared <= nearestDistanceSquared) {
                nearest = minecart;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    /** Prefers the cart's own current heading (if it's already moving); falls back to the marking player's facing. */
    private static int[] directionOf(Minecart minecart, Player fallbackFacing) {
        Vector velocity = minecart.getVelocity();
        if (velocity.lengthSquared() > 0.0009) {
            return Math.abs(velocity.getX()) >= Math.abs(velocity.getZ())
                    ? new int[]{velocity.getX() > 0 ? 1 : -1, 0}
                    : new int[]{0, velocity.getZ() > 0 ? 1 : -1};
        }
        return cardinalDirection(fallbackFacing.getLocation().getYaw());
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("stop", "cart", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            return filterPrefix(List.of("create", "remove", "list"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cart")) {
            return filterPrefix(List.of("mark", "unmark"), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
