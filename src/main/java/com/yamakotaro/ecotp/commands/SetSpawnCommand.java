package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
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
            sender.sendMessage("プレイヤーのみ実行できます。");
            return true;
        }
        if (!player.hasPermission("ecotp.setspawn")) {
            player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&cこのコマンドを使う権限がありません。"));
            return true;
        }

        plugin.getSpawnManager().setSpawn(player.getLocation());
        player.sendMessage(ChatUtil.color(plugin.getPrefix() + "&aスポーン地点を現在地に設定しました。"));
        return true;
    }
}
