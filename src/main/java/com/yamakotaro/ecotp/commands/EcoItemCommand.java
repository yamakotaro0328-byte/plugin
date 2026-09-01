package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TabCompleteUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 管理者用: /ecoitem give <プレイヤー名> <金額> [個数] - 右クリックで換金できる
 * 「物理通貨」アイテムを生成して渡す (EcoItemManager/EcoItemListener 参照)。
 */
public class EcoItemCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_QUANTITY = 64;

    private final EcoTpPlugin plugin;

    public EcoItemCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("eco-item")) {
            sender.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!sender.hasPermission("ecotp.ecoitem")) {
            sender.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(plugin.msg("ecoitem.usage"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.msg("general.player-offline"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.msg("pay.invalid-amount"));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(plugin.msg("pay.amount-too-low"));
            return true;
        }

        int quantity = 1;
        if (args.length >= 4) {
            try {
                quantity = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.msg("ecoitem.invalid-quantity"));
                return true;
            }
        }
        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            sender.sendMessage(plugin.msg("ecoitem.invalid-quantity"));
            return true;
        }

        ItemStack item = plugin.getEcoItemManager().createItem(amount, quantity);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        String formattedAmount = ChatUtil.formatMoney(amount);
        sender.sendMessage(plugin.msg("ecoitem.given", "player", target.getName(), "amount", formattedAmount, "quantity", String.valueOf(quantity)));
        target.sendMessage(plugin.msg("ecoitem.received", "amount", formattedAmount, "quantity", String.valueOf(quantity)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(List.of("give"), args[0]);
        }
        if (args.length == 2) {
            return TabCompleteUtil.onlinePlayerNames(args[1], null);
        }
        return Collections.emptyList();
    }
}
