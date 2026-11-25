package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.SeasonalEventManager;
import com.yamakotaro.ecotp.TabCompleteUtil;
import com.yamakotaro.ecotp.gui.EventShopHolder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** /event : 景品交換所を開く。/event hat で帽子の切り替え、/event coins <名前> <数> は管理者用。 */
public class EventCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public EventCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SeasonalEventManager manager = plugin.getSeasonalEventManager();
        SeasonalEventManager.SeasonalEvent active = manager.getActive();
        if (active == null) {
            sender.sendMessage(plugin.msg("event.none-active"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("coins")) {
            if (!sender.hasPermission("ecotp.admin")) {
                sender.sendMessage(plugin.msg("general.no-permission"));
                return true;
            }
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
            int amount;
            try {
                amount = args.length >= 3 ? Integer.parseInt(args[2]) : 10;
            } catch (NumberFormatException e) {
                amount = -1;
            }
            if (target == null || amount < 1) {
                sender.sendMessage(plugin.msg("event.coins-usage"));
                return true;
            }
            int left = amount;
            while (left > 0) {
                int stack = Math.min(64, left);
                manager.giveItem(target, manager.createCoins(active, stack));
                left -= stack;
            }
            sender.sendMessage(plugin.msg("event.coins-given", "player", target.getName(), "amount", String.valueOf(amount)));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("hat")) {
            if (active.hat() == null) {
                player.sendMessage(plugin.msg("event.no-hat"));
                return true;
            }
            boolean wearing = manager.toggleHat(player);
            player.sendMessage(plugin.msg(wearing ? "event.hat-on" : "event.hat-off"));
            return true;
        }
        player.openInventory(new EventShopHolder(plugin, player, active).getInventory());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(sender.hasPermission("ecotp.admin")
                    ? List.of("hat", "coins") : List.of("hat"), args[0]);
        }
        return Collections.emptyList();
    }
}
