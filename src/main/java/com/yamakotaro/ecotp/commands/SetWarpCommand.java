package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.HomeManager;
import com.yamakotaro.ecotp.TabCompleteUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /setwarp <名前> と /delwarp <名前> (管理者用)。名前の規則はホームと同じ。
 */
public class SetWarpCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;
    private final boolean delete;

    public SetWarpCommand(EcoTpPlugin plugin, boolean delete) {
        this.plugin = plugin;
        this.delete = delete;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecotp.setwarp")) {
            sender.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(plugin.msg(delete ? "delwarp.usage" : "setwarp.usage"));
            return true;
        }
        String name = args[0];

        if (delete) {
            if (plugin.getWarpManager().deleteWarp(name)) {
                sender.sendMessage(plugin.msg("delwarp.success", "name", name));
            } else {
                sender.sendMessage(plugin.msg("warp.not-found", "name", name));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!HomeManager.VALID_NAME.matcher(name).matches()) {
            player.sendMessage(plugin.msg("setwarp.invalid-name"));
            return true;
        }
        plugin.getWarpManager().setWarp(name, player.getLocation());
        player.sendMessage(plugin.msg("setwarp.success", "name", name));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(plugin.getWarpManager().getWarpNames(), args[0]);
        }
        return Collections.emptyList();
    }
}
