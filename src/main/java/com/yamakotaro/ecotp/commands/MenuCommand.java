package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.MenuItemManager;
import com.yamakotaro.ecotp.gui.MainMenuHolder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MenuCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public MenuCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("menu")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.menu")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("item")) {
            return handleItemToggle(player, args);
        }
        player.openInventory(new MainMenuHolder(plugin, player).getInventory());
        return true;
    }

    private boolean handleItemToggle(Player player, String[] args) {
        MenuItemManager manager = plugin.getMenuItemManager();
        boolean target;
        if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            target = false;
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("on")) {
            target = true;
        } else {
            target = !manager.isEnabled(player.getUniqueId());
        }
        manager.setEnabled(player, target);
        player.sendMessage(plugin.msg(target ? "menu-item.toggle-on" : "menu-item.toggle-off"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("item")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            return Stream.of("on", "off")
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
