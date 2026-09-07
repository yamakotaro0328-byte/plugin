package com.yamakotaro.sulfursoccer.commands;

import com.yamakotaro.sulfursoccer.Messages;
import com.yamakotaro.sulfursoccer.arena.Arena;
import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.arena.ArenaTreeBuilder;
import com.yamakotaro.sulfursoccer.arena.ArenaWallBuilder;
import com.yamakotaro.sulfursoccer.arena.Box;
import com.yamakotaro.sulfursoccer.arena.Point;
import com.yamakotaro.sulfursoccer.gimmick.Gimmick;
import com.yamakotaro.sulfursoccer.gimmick.GimmickBuilder;
import com.yamakotaro.sulfursoccer.gimmick.GimmickManager;
import com.yamakotaro.sulfursoccer.gimmick.GimmickType;
import com.yamakotaro.sulfursoccer.match.JoinResult;
import com.yamakotaro.sulfursoccer.match.MatchManager;
import com.yamakotaro.sulfursoccer.selection.SelectionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class SoccerCommand implements CommandExecutor, TabCompleter {

    private static final Random RANDOM = new Random();

    private final ArenaManager arenaManager;
    private final MatchManager matchManager;
    private final SelectionManager selectionManager;
    private final GimmickManager gimmickManager;
    private final NamespacedKey wandKey;
    private final Messages messages;

    public SoccerCommand(ArenaManager arenaManager, MatchManager matchManager, SelectionManager selectionManager,
                          GimmickManager gimmickManager, NamespacedKey wandKey, Messages messages) {
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
        this.selectionManager = selectionManager;
        this.gimmickManager = gimmickManager;
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
            case "gimmick" -> handleArenaGimmick(sender, args);
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
        Arena updated = team == 'a' ? arena.withGoalA(selection.get()) : arena.withGoalB(selection.get());
        arenaManager.update(updated);
        sender.sendMessage(messages.get("arena.goal-set", Map.of("name", arena.id(), "team", args[3].toUpperCase())));
        maybeBuildWalls(sender, updated);
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
        Arena updated = arena.withField(selection.get());
        arenaManager.update(updated);
        sender.sendMessage(messages.get("arena.field-set", Map.of("name", arena.id())));
        maybeBuildWalls(sender, updated);
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

    private void handleArenaGimmick(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.get("arena.gimmick-usage", Map.of()));
            return;
        }
        switch (args[2].toLowerCase()) {
            case "add" -> handleArenaGimmickAdd(sender, args);
            case "remove" -> handleArenaGimmickRemove(sender, args);
            case "list" -> handleArenaGimmickList(sender, args);
            default -> sender.sendMessage(messages.get("arena.gimmick-usage", Map.of()));
        }
    }

    private void handleArenaGimmickAdd(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(messages.get("arena.gimmick-add-usage", Map.of()));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        Optional<Arena> arenaOpt = arenaManager.find(args[3]);
        if (arenaOpt.isEmpty()) {
            sender.sendMessage(messages.get("arena.not-found", Map.of("name", args[3])));
            return;
        }
        String gimmickId = args[4];
        GimmickType type;
        try {
            type = GimmickType.valueOf(args[5].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(messages.get("arena.gimmick-invalid-type", Map.of("type", args[5])));
            return;
        }
        Optional<Box> selection = selectionManager.getCompleteSelection(player.getUniqueId(), player.getWorld().getName());
        if (selection.isEmpty()) {
            sender.sendMessage(messages.get("wand.selection-incomplete", Map.of()));
            return;
        }
        Arena arena = arenaOpt.get();
        // The wand selection is a spawn range, not the gimmick's own footprint - every type gets
        // one random column picked from inside it (see randomPositionWithin), so the same "bump"
        // or "wind" zone doesn't sit in the exact same spot (or cover the whole selection at once)
        // every time it's placed.
        Box region = randomPositionWithin(selection.get());
        Gimmick gimmick = new Gimmick(gimmickId, type, region);
        if (!gimmickManager.add(arena.id(), gimmick)) {
            sender.sendMessage(messages.get("arena.gimmick-duplicate-id", Map.of("id", gimmickId)));
            return;
        }
        World world = Bukkit.getWorld(arena.world());
        if (world != null) {
            GimmickBuilder.build(world, gimmick);
        }
        sender.sendMessage(messages.get("arena.gimmick-added", Map.of(
                "id", gimmickId, "type", type.name(), "name", arena.id(),
                "x", String.valueOf(region.minX()), "z", String.valueOf(region.minZ()))));
    }

    /** Picks one random (x, z) column from within the given selection, keeping its original
     * y-range - a single-column Box rather than the full selection. */
    private static Box randomPositionWithin(Box selection) {
        int x = selection.minX() + RANDOM.nextInt(selection.maxX() - selection.minX() + 1);
        int z = selection.minZ() + RANDOM.nextInt(selection.maxZ() - selection.minZ() + 1);
        return new Box(new Point(x, selection.minY(), z), new Point(x, selection.maxY(), z));
    }

    private void handleArenaGimmickRemove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(messages.get("arena.gimmick-remove-usage", Map.of()));
            return;
        }
        Optional<Arena> arenaOpt = arenaManager.find(args[3]);
        if (arenaOpt.isEmpty()) {
            sender.sendMessage(messages.get("arena.not-found", Map.of("name", args[3])));
            return;
        }
        String gimmickId = args[4];
        boolean removed = gimmickManager.remove(arenaOpt.get().id(), gimmickId);
        sender.sendMessage(messages.get(removed ? "arena.gimmick-removed" : "arena.gimmick-not-found",
                Map.of("id", gimmickId, "name", arenaOpt.get().id())));
    }

    private void handleArenaGimmickList(CommandSender sender, String[] args) {
        Optional<Arena> arenaOpt = arenaManager.find(args[3]);
        if (arenaOpt.isEmpty()) {
            sender.sendMessage(messages.get("arena.not-found", Map.of("name", args[3])));
            return;
        }
        List<Gimmick> gimmicks = gimmickManager.forArena(arenaOpt.get().id());
        sender.sendMessage(messages.get("arena.gimmick-list-header",
                Map.of("name", arenaOpt.get().id(), "count", String.valueOf(gimmicks.size()))));
        for (Gimmick gimmick : gimmicks) {
            sender.sendMessage(messages.get("arena.gimmick-list-entry",
                    Map.of("id", gimmick.id(), "type", gimmick.type().name())));
        }
    }

    /** Once field, goalA, and goalB are all set (in any order), wraps the field in real barrier
     * walls - see ArenaWallBuilder. Called after every setfield/setgoal so the walls always match
     * whichever piece was set last (e.g. moving a goal after the field was already walled). */
    private void maybeBuildWalls(CommandSender sender, Arena arena) {
        if (arena.field() == null || arena.goalA() == null || arena.goalB() == null) {
            return;
        }
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return;
        }
        ArenaWallBuilder.build(world, arena.field(), arena.goalA(), arena.goalB());
        sender.sendMessage(messages.get("arena.walls-built", Map.of("name", arena.id())));
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
            return filterPrefix(List.of("create", "setgoal", "setspawn", "setkickoff", "setfield", "gimmick", "tree", "remove", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("arena")
                && List.of("setgoal", "setspawn", "setkickoff", "setfield", "tree", "remove").contains(args[1].toLowerCase())) {
            return filterPrefix(arenaManager.all().stream().map(Arena::id).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("arena")
                && (args[1].equalsIgnoreCase("setgoal") || args[1].equalsIgnoreCase("setspawn"))) {
            return filterPrefix(List.of("a", "b"), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("gimmick")) {
            return filterPrefix(List.of("add", "remove", "list"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("gimmick")
                && List.of("add", "remove", "list").contains(args[2].toLowerCase())) {
            return filterPrefix(arenaManager.all().stream().map(Arena::id).toList(), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("gimmick") && args[2].equalsIgnoreCase("remove")) {
            return filterPrefix(gimmickManager.forArena(args[3]).stream().map(Gimmick::id).toList(), args[4]);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("gimmick") && args[2].equalsIgnoreCase("add")) {
            return filterPrefix(Arrays.stream(GimmickType.values()).map(t -> t.name().toLowerCase()).toList(), args[5]);
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
