package com.yamakotaro.ecoboss.commands;

import com.yamakotaro.ecoboss.Messages;
import com.yamakotaro.ecoboss.boss.BossDefinition;
import com.yamakotaro.ecoboss.boss.BossManager;
import com.yamakotaro.ecoboss.location.Box;
import com.yamakotaro.ecoboss.location.Point;
import com.yamakotaro.ecoboss.selection.SelectionManager;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BossCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final BossManager bossManager;
    private final SelectionManager selectionManager;
    private final NamespacedKey wandKey;
    private final Messages messages;

    public BossCommand(JavaPlugin plugin, BossManager bossManager, SelectionManager selectionManager,
                        NamespacedKey wandKey, Messages messages) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.selectionManager = selectionManager;
        this.wandKey = wandKey;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("boss.usage", Map.of()));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawn(sender, args);
            case "stop" -> handleStop(sender, args);
            case "wand" -> handleWand(sender);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "setregion" -> handleSetRegion(sender, args);
            case "clearspawns" -> handleClearSpawns(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage(messages.get("boss.usage", Map.of()));
        }
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("ecoboss.admin")) {
            return true;
        }
        sender.sendMessage(messages.get("general.no-permission", Map.of()));
        return false;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("boss.spawn-usage", Map.of()));
            return;
        }
        Optional<BossDefinition> defOpt = bossManager.find(args[1]);
        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.get("boss.not-found", Map.of("id", args[1])));
            return;
        }
        BossDefinition definition = defOpt.get();
        String error = bossManager.spawn(definition, player.getLocation());
        if (error != null) {
            sender.sendMessage(messages.get(error, Map.of("minutes", String.valueOf(bossManager.cooldownRemainingMinutes(definition.id())))));
            return;
        }
        sender.sendMessage(messages.get("boss.spawned", Map.of("boss", definition.displayName())));
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("boss.stop-usage", Map.of()));
            return;
        }
        Optional<BossDefinition> defOpt = bossManager.find(args[1]);
        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.get("boss.not-found", Map.of("id", args[1])));
            return;
        }
        String error = bossManager.stop(defOpt.get().id());
        sender.sendMessage(messages.get(error != null ? error : "boss.stopped", Map.of("boss", defOpt.get().displayName())));
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
        meta.displayName(messages.get("boss.wand-item-name", Map.of()));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BOOLEAN, true);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(messages.get("boss.wand-given", Map.of()));
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("boss.setspawn-usage", Map.of()));
            return;
        }
        Optional<BossDefinition> defOpt = bossManager.find(args[1]);
        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.get("boss.not-found", Map.of("id", args[1])));
            return;
        }
        int count = bossManager.locations().addSpawnPoint(defOpt.get().id(), Point.fromLocation(player.getLocation()));
        sender.sendMessage(messages.get("boss.spawn-point-added", Map.of("boss", defOpt.get().displayName(), "count", String.valueOf(count))));
    }

    private void handleSetRegion(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("general.player-only", Map.of()));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("boss.setregion-usage", Map.of()));
            return;
        }
        Optional<BossDefinition> defOpt = bossManager.find(args[1]);
        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.get("boss.not-found", Map.of("id", args[1])));
            return;
        }
        Optional<Box> selection = selectionManager.getCompleteSelection(player.getUniqueId(), player.getWorld().getName());
        if (selection.isEmpty()) {
            sender.sendMessage(messages.get("boss.selection-incomplete", Map.of()));
            return;
        }
        bossManager.locations().setRegion(defOpt.get().id(), selection.get());
        sender.sendMessage(messages.get("boss.region-set", Map.of("boss", defOpt.get().displayName())));
    }

    private void handleClearSpawns(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("boss.clearspawns-usage", Map.of()));
            return;
        }
        Optional<BossDefinition> defOpt = bossManager.find(args[1]);
        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.get("boss.not-found", Map.of("id", args[1])));
            return;
        }
        bossManager.locations().clearSpawnPoints(defOpt.get().id());
        sender.sendMessage(messages.get("boss.spawns-cleared", Map.of("boss", defOpt.get().displayName())));
    }

    private void handleList(CommandSender sender) {
        var bosses = bossManager.all();
        sender.sendMessage(messages.get("boss.list-header", Map.of("count", String.valueOf(bosses.size()))));
        for (BossDefinition definition : bosses) {
            sender.sendMessage(messages.get("boss.list-entry",
                    Map.of("id", definition.id(), "type", definition.type().name(), "name", definition.displayName())));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("boss.usage", Map.of()));
            return;
        }
        Optional<BossDefinition> defOpt = bossManager.find(args[1]);
        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.get("boss.not-found", Map.of("id", args[1])));
            return;
        }
        BossDefinition definition = defOpt.get();
        sender.sendMessage(messages.get("boss.info-header", Map.of("id", definition.id(), "name", definition.displayName())));
        sender.sendMessage(messages.get("boss.info-type", Map.of("type", definition.type().name())));
        if (bossManager.isActive(definition.id())) {
            sender.sendMessage(messages.get("boss.info-status-active", Map.of()));
            return;
        }
        long cooldown = bossManager.cooldownRemainingMinutes(definition.id());
        sender.sendMessage(messages.get(cooldown > 0 ? "boss.info-status-cooldown" : "boss.info-status-ready",
                Map.of("minutes", String.valueOf(cooldown))));
    }

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reloadConfig();
        bossManager.load();
        sender.sendMessage(messages.get("boss.reloaded", Map.of()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("spawn", "stop", "wand", "setspawn", "setregion", "clearspawns", "list", "info", "reload"), args[0]);
        }
        if (args.length == 2 && List.of("spawn", "stop", "setspawn", "setregion", "clearspawns", "info").contains(args[0].toLowerCase())) {
            return filterPrefix(bossManager.all().stream().map(BossDefinition::id).toList(), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).toList();
    }
}
