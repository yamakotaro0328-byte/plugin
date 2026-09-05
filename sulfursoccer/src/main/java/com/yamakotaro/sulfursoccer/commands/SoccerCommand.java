package com.yamakotaro.sulfursoccer.commands;

import com.yamakotaro.sulfursoccer.Messages;
import com.yamakotaro.sulfursoccer.arena.Arena;
import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.arena.ArenaTreeBuilder;
import com.yamakotaro.sulfursoccer.arena.Box;
import com.yamakotaro.sulfursoccer.arena.Point;
import com.yamakotaro.sulfursoccer.match.JoinResult;
import com.yamakotaro.sulfursoccer.match.MatchManager;
import com.yamakotaro.sulfursoccer.selection.SelectionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SoccerCommand implements CommandExecutor, TabCompleter {

    private final ArenaManager arenaManager;
    private final MatchManager matchManager;
    private final SelectionManager selectionManager;
    private final NamespacedKey wandKey;
    private final Messages messages;

    public SoccerCommand(ArenaManager arenaManager, MatchManager matchManager, SelectionManager selectionManager,
                          NamespacedKey wandKey, Messages messages) {
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
        this.selectionManager = selectionManager;
        this.wandKey = wandKey;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("soccer.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "wand" -> handleWand(sender);
            case "arena" -> handleArena(sender, args);
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender);
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender, args);
            default -> sender.sendMessage(messages.get("soccer.usage", Map.of()));
        }
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("sulfursoccer.admin")) {
            return true;
        }
        sender.sendMessage(messages.get("general.no-permission", Map.of()));
        return false;
    }

    /** When no arena name is given, falls back to the sole registered arena - errors if there's none or more than one. */
    private Optional<String> resolveArenaName(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            return Optional.of(args[index]);
        }
        Collection<Arena> arenas = arenaManager.all();
        if (arenas.size() == 1) {
            return Optional.of(arenas.iterator().next().id());
        }
        sender.sendMessage(messages.get(arenas.isEmpty() ? "arena.none-exist" : "arena.name-required", Map.of()));
        return Optional.empty();
    }

    private void handleWand(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(messages.get("wand.item-name", Map.of()));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BOOLEAN, true);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(messages.get("wand.given", Map.of()));
    }

    private void handleArena(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("arena.usage", Map.of()));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> handleArenaCreate(sender, args);
            case "setgoal" -> handleArenaSetGoal(sender, args);
            case "setspawn" -> handleArenaSetSpawn(sender, args);
            case "setkickoff" -> handleArenaSetKickoff(sender, args);
            case "setfield" -> handleArenaSetField(sender, args);
            case "tree" -> handleArenaTree(sender, args);
            case "remove" -> handleArenaRemove(sender, args);
            case "list" -> handleArenaList(sender);
            default -> sender.sendMessage(messages.get("arena.usage", Map.of()));
        }
    }

    private void handleArenaCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("arena.create-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        String name = args[2];
        if (arenaManager.find(name).isPresent()) {
            sender.sendMessage(messages.get("arena.already-exists", Map.of("name", name)));
            return;
        }
        arenaManager.create(name, player.getWorld().getName());
        sender.sendMessage(messages.get("arena.created", Map.of("name", name)));
    }

    private void handleArenaSetGoal(CommandSender sender, String[] args) {
        if (args.length < 4 || !isTeam(args[3])) {
            sender.sendMessage(messages.get("arena.setgoal-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Optional<Arena> arenaOpt = arenaManager.find(args[2]);
        if (arenaOpt.isEmpty()) {
            sender.sendMessage(messages.get("arena.not-found", Map.of("name", args[2])));
            return;
        }
        Optional<Box> selection = selectionManager.getCompleteSelection(player.getUniqueId(), player.getWorld().getName());
        if (selection.isEmpty()) {
            sender.sendMessage(messages.get("wand.selection-incomplete", Map.of()));
            return;
        }
        char team = args[3].toLowerCase().charAt(0);
        Arena arena = arenaOpt.get();
        arenaManager.update(team == 'a' ? arena.withGoalA(selection.get()) : arena.withGoalB(selection.get()));
        sender.sendMessage(messages.get("arena.goal-set", Map.of("name", arena.id(), "team", args[3].toUpperCase())));
    }

    private void handleArenaSetSpawn(CommandSender sender, String[] args) {
        if (args.length < 4 || !isTeam(args[3])) {
            sender.sendMessage(messages.get("arena.setspawn-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Optional<Arena> arenaOpt = arenaManager.find(args[2]);
        if (arenaOpt.isEmpty()) {
            sender.sendMessage(messages.get("arena.not-found", Map.of("name", args[2])));
            return;
        }
        char team = args[3].toLowerCase().charAt(0);
        Arena arena = arenaOpt.get();
        Point point = toPoint(player.getLocation());
        arenaManager.update(team == 'a' ? arena.withSpawnA(point) : arena.withSpawnB(point));
        sender.sendMessage(messages.get("arena.spawn-set", Map.of("name", arena.id(), "team", args[3].toUpperCase())));
    }

    private void handleArenaSetKickoff(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("arena.setkickoff-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Optional<Arena> arenaOpt = arenaManager.find(args[2]);
        if (arenaOpt.isEmpty()) {
            sender.sendMessage(messages.get("arena.not-found", Map.of("name", args[2])));
            return;
        }
        Arena arena = arenaOpt.get();
        arenaManager.update(arena.withKickoff(toPoint(player.getLocation())));
        sender.sendMessage(messages.get("arena.kickoff-set", Map.of("name", arena.id())));
    }

    private void handleArenaSetField(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("arena.setfield-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Optional<Arena> arenaOpt = arenaManager.find(args[2]);
        if (arenaOpt.isEmpty()) {
            sender.sendMessage(messages.get("arena.not-found", Map.of("name", args[2])));
            return;
        }
        Optional<Box> selection = selectionManager.getCompleteSelection(player.getUniqueId(), player.getWorld().getName());
        if (selection.isEmpty()) {
            sender.sendMessage(messages.get("wand.selection-incomplete", Map.of()));
            return;
        }
        Arena arena = arenaOpt.get();
        arenaManager.update(arena.withField(selection.get()));
        sender.sendMessage(messages.get("arena.field-set", Map.of("name", arena.id())));
    }

    private void handleArenaTree(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("arena.tree-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Optional<Arena> arenaOpt = arenaManager.find(args[2]);
        if (arenaOpt.isEmpty()) {
            sender.sendMessage(messages.get("arena.not-found", Map.of("name", args[2])));
            return;
        }
        Arena arena = arenaOpt.get();
        ArenaTreeBuilder.place(player.getLocation().getBlock());
        sender.sendMessage(messages.get("arena.tree-placed", Map.of("name", arena.id())));
    }

    private void handleArenaRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("arena.remove-usage", Map.of()));
            return;
        }
        boolean removed = arenaManager.remove(args[2]);
        sender.sendMessage(messages.get(removed ? "arena.removed" : "arena.not-found", Map.of("name", args[2])));
    }

    private void handleArenaList(CommandSender sender) {
        var arenas = arenaManager.all();
        sender.sendMessage(messages.get("arena.list-header", Map.of("count", String.valueOf(arenas.size()))));
        if (arenas.isEmpty()) {
            sender.sendMessage(messages.get("arena.list-empty", Map.of()));
            return;
        }
        for (Arena arena : arenas) {
            Component readyBadge = messages.get(arena.isReady() ? "arena.status-ready" : "arena.status-incomplete", Map.of());
            sender.sendMessage(messages.get("arena.list-entry", Map.of("name", arena.id(), "world", arena.world()))
                    .append(Component.text(" ")).append(readyBadge));
        }
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Optional<String> arenaName = resolveArenaName(sender, args, 1);
        if (arenaName.isEmpty()) {
            return;
        }
        JoinResult result = matchManager.join(player.getUniqueId(), arenaName.get());
        if (result.isError()) {
            sender.sendMessage(messages.get(result.errorKey(), Map.of("name", arenaName.get())));
            return;
        }
        sender.sendMessage(messages.get("soccer.joined",
                Map.of("name", arenaName.get(), "team", String.valueOf(result.team()).toUpperCase())));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        matchManager.leave(player.getUniqueId());
        sender.sendMessage(messages.get("soccer.left", Map.of()));
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Optional<String> arenaName = resolveArenaName(sender, args, 1);
        if (arenaName.isEmpty()) {
            return;
        }
        String error = matchManager.start(arenaName.get());
        sender.sendMessage(messages.get(error != null ? error : "soccer.started", Map.of("name", arenaName.get())));
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Optional<String> arenaName = resolveArenaName(sender, args, 1);
        if (arenaName.isEmpty()) {
            return;
        }
        String error = matchManager.stopWithMessage(arenaName.get(), "soccer.stopped-manually");
        sender.sendMessage(messages.get(error != null ? error : "soccer.stopped-manually", Map.of("name", arenaName.get())));
    }

    private static boolean isTeam(String value) {
        return value.equalsIgnoreCase("a") || value.equalsIgnoreCase("b");
    }

    private static Point toPoint(Location location) {
        return new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("wand", "arena", "join", "leave", "start", "stop"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            return filterPrefix(List.of("create", "setgoal", "setspawn", "setkickoff", "setfield", "tree", "remove", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("arena")
                && List.of("setgoal", "setspawn", "setkickoff", "setfield", "tree", "remove").contains(args[1].toLowerCase())) {
            return filterPrefix(arenaManager.all().stream().map(Arena::id).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("arena")
                && (args[1].equalsIgnoreCase("setgoal") || args[1].equalsIgnoreCase("setspawn"))) {
            return filterPrefix(List.of("a", "b"), args[3]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            return filterPrefix(arenaManager.all().stream().map(Arena::id).toList(), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
