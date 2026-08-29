package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /donatemessage <メッセージ>|reset : /donate で自分が寄付を受けた時に全体へ流れる
 * お礼メッセージを、自分専用の内容に設定(またはデフォルトに戻す)。
 */
public class DonateMessageCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public DonateMessageCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("donate")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.donate")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(plugin.msg("donatemessage.usage"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
            plugin.getDonationManager().resetMessageTemplate(player.getUniqueId());
            player.sendMessage(plugin.msg("donatemessage.reset"));
            return true;
        }
        String template = String.join(" ", args);
        plugin.getDonationManager().setMessageTemplate(player.getUniqueId(), template);
        player.sendMessage(plugin.msg("donatemessage.set"));
        return true;
    }
}
