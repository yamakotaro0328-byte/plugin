package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 支払いの確認 (チャットクリック) を統合版 (Bedrock) でも行えるようにするコマンド。
 * /accept        -> 保留中の操作を承諾する
 * /accept cancel -> 保留中の操作をキャンセルする
 */
public class AcceptCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public AcceptCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行できます。");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            plugin.getConfirmationManager().cancel(player);
        } else {
            plugin.getConfirmationManager().confirm(player);
        }
        return true;
    }
}
