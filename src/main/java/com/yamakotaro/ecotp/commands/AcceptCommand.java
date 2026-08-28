package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 承諾/キャンセルを統合版 (Bedrock) でも行えるようにする共通コマンド。
 * 支払いの確認待ちがあればそれを、無ければ受け取っているテレポートリクエスト (/tpa) を
 * 承諾/拒否する。/tpaccept, /tpdeny を覚えていなくても、これ一つで完結する。
 * /accept        -> 承諾する
 * /accept cancel -> キャンセル/拒否する
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

        boolean cancel = args.length > 0 && args[0].equalsIgnoreCase("cancel");

        if (plugin.getConfirmationManager().hasPending(player.getUniqueId())) {
            if (cancel) {
                plugin.getConfirmationManager().cancel(player);
            } else {
                plugin.getConfirmationManager().confirm(player);
            }
            return true;
        }

        if (cancel) {
            plugin.getTpaManager().denyRequest(player);
        } else {
            plugin.getTpaManager().acceptRequest(player);
        }
        return true;
    }
}
