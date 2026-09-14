package com.yamakotaro.velodicordbridge;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class PosCommand implements CommandExecutor, TabCompleter {

    private final BridgeServer server;
    private final String serverName;

    public PosCommand(BridgeServer server, String serverName) {
        this.server = server;
        this.serverName = serverName;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行できます");
            return true;
        }

        var location = player.getLocation();
        String dimension = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        if (args.length == 0) {
            server.sendMessage("POS&%s&%s&%s&(%d, %d, %d)".formatted(serverName, player.getName(), dimension, x, y, z));
            player.sendMessage("現在地をDiscordに共有しました");
        } else {
            String name = args[0];
            server.sendMessage("NPOS&%s&%s&%s&(%d, %d, %d)&%s".formatted(serverName, player.getName(), dimension, x, y, z, name));
            player.sendMessage("現在地を「%s」としてDiscordに共有しました".formatted(name));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
