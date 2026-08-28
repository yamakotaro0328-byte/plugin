package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public SetSpawnCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("ecotp.setspawn")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        plugin.getSpawnManager().setSpawn(player.getLocation());
        player.sendMessage(plugin.msg("setspawn.success"));
        return true;
    }
}
