package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TabCompleteUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /delhome <名前> : 自分のホームを削除する (無料)。
 * 削除しても /sethome の回数カウント (料金の上昇) はリセットされない。
 */
public class DelHomeCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public DelHomeCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("sethome")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.sethome")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.msg("delhome.usage"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        String name = args[0];
        if (!plugin.getHomeManager().deleteHome(uuid, name)) {
            player.sendMessage(plugin.msg("delhome.not-found", "name", name));
            return true;
        }
        player.sendMessage(plugin.msg("delhome.success", "name", name));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return TabCompleteUtil.filterPrefix(plugin.getHomeManager().getHomeNames(player.getUniqueId()), args[0]);
        }
        return Collections.emptyList();
    }
}
