package com.yamakotaro.ecocosmetics.commands;

import com.yamakotaro.ecocosmetics.CosmeticDefinition;
import com.yamakotaro.ecocosmetics.CosmeticManager;
import com.yamakotaro.ecocosmetics.EcoCosmeticsPlugin;
import com.yamakotaro.ecocosmetics.gui.ShopHolder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CosmeticsCommand implements CommandExecutor, TabCompleter {

    private final EcoCosmeticsPlugin plugin;

    public CosmeticsCommand(EcoCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.messages().get("general.players-only"));
                return true;
            }
            if (!player.hasPermission("ecocosmetics.use")) {
                player.sendMessage(plugin.messages().get("general.no-permission"));
                return true;
            }
            player.openInventory(new ShopHolder(plugin, player.getUniqueId(), null).getInventory());
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("give")) {
            return handleGive(sender, args);
        }
        if (sub.equals("reload")) {
            return handleReload(sender);
        }
        sender.sendMessage(plugin.messages().get("command.usage"));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecocosmetics.admin")) {
            sender.sendMessage(plugin.messages().get("general.no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.messages().get("command.give-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.messages().get("command.player-offline"));
            return true;
        }
        String id = args[2];
        CosmeticManager manager = plugin.getCosmeticManager();
        CosmeticDefinition definition = manager.get(id);
        if (definition == null) {
            sender.sendMessage(plugin.messages().get("command.unknown-cosmetic", "id", id));
            return true;
        }
        manager.give(target.getUniqueId(), id);
        sender.sendMessage(plugin.messages().get("command.given", "player", target.getName(), "name", definition.displayName()));
        target.sendMessage(plugin.messages().get("command.received", "name", definition.displayName()));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("ecocosmetics.admin")) {
            sender.sendMessage(plugin.messages().get("general.no-permission"));
            return true;
        }
        plugin.reloadPluginConfig();
        plugin.getCosmeticManager().load();
        sender.sendMessage(plugin.messages().get("command.reloaded"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return new ArrayList<>(plugin.getCosmeticManager().getCatalog().keySet()).stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
