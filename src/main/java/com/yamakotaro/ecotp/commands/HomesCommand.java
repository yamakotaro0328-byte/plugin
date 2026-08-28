package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 自分が設定しているホームの名前一覧を表示する (無料)。
 */
public class HomesCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public HomesCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("ecotp.home")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        List<String> names = plugin.getHomeManager().getHomeNames(player.getUniqueId());
        if (names.isEmpty()) {
            player.sendMessage(plugin.msg("homes.empty"));
            return true;
        }
        player.sendMessage(plugin.msg("homes.list", "names", String.join(", ", names)));
        return true;
    }
}
