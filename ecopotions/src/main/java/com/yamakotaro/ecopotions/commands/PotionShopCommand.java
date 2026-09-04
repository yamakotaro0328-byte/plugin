package com.yamakotaro.ecopotions.commands;

import com.yamakotaro.ecopotions.EcoPotionsPlugin;
import com.yamakotaro.ecopotions.PotionDefinition;
import com.yamakotaro.ecopotions.PotionManager;
import com.yamakotaro.ecopotions.gui.PotionShopHolder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PotionShopCommand implements CommandExecutor, TabCompleter {

    private final EcoPotionsPlugin plugin;

    public PotionShopCommand(EcoPotionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessages().get("general.players-only"));
                return true;
            }
            if (!player.hasPermission("ecopotions.use")) {
                player.sendMessage(plugin.getMessages().get("general.no-permission"));
                return true;
            }
            player.openInventory(new PotionShopHolder(plugin).getInventory());
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("give")) {
            return handleGive(sender, args);
        }
        if (sub.equals("reload")) {
            return handleReload(sender);
        }
        sender.sendMessage(plugin.getMessages().get("command.usage"));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("ecopotions.admin")) {
            sender.sendMessage(plugin.getMessages().get("general.no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessages().get("command.give-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessages().get("command.player-offline"));
            return true;
        }
        String id = args[2];
        PotionManager manager = plugin.getPotionManager();
        PotionDefinition definition = manager.get(id);
        if (definition == null) {
            sender.sendMessage(plugin.getMessages().get("command.unknown-potion", "id", id));
            return true;
        }
        int quantity = 1;
        if (args.length >= 4) {
            try {
                quantity = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessages().get("command.invalid-quantity"));
                return true;
            }
            if (quantity <= 0) {
                sender.sendMessage(plugin.getMessages().get("command.invalid-quantity"));
                return true;
            }
        }
        manager.give(target, definition, quantity);
        sender.sendMessage(plugin.getMessages().get("command.given", "player", target.getName(), "name", definition.displayName(), "quantity", quantity));
        target.sendMessage(plugin.getMessages().get("command.received", "name", definition.displayName(), "quantity", quantity));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("ecopotions.admin")) {
            sender.sendMessage(plugin.getMessages().get("general.no-permission"));
            return true;
        }
        plugin.reloadPluginConfig();
        plugin.getPotionManager().load();
        sender.sendMessage(plugin.getMessages().get("command.reloaded"));
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
            return new ArrayList<>(plugin.getPotionManager().getCatalog().keySet()).stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
