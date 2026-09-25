package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /reply <メッセージ> : /msg で直前にやり取りした相手へ、名前を打たずに返信する。
 */
public class ReplyCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public ReplyCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("msg")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.msg")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.msg("msg.usage-reply"));
            return true;
        }

        UUID targetUuid = plugin.getPrivateMessageManager().getReplyTarget(player.getUniqueId());
        if (targetUuid == null) {
            player.sendMessage(plugin.msg("msg.no-reply-target"));
            return true;
        }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.msg("general.player-offline"));
            return true;
        }

        String message = String.join(" ", args);
        plugin.getPrivateMessageManager().recordConversation(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(plugin.msg("msg.sent", "player", target.getName(), "message", message));
        target.sendMessage(plugin.msg("msg.received", "player", player.getName(), "message", message));
        if (plugin.getAfkManager().isAfk(target.getUniqueId())) {
            player.sendMessage(plugin.msg("msg.target-afk", "player", target.getName()));
        }
        return true;
    }
}
