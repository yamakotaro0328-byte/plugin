package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tpacancel : 自分が送った /tpa, /tphere のリクエストを、相手の応答を待たずに取り消す。
 */
public class TpaCancelCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public TpaCancelCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.getTpaManager().cancelByRequester(player)) {
            player.sendMessage(plugin.msg("tpa.no-outgoing-request"));
            return true;
        }
        player.sendMessage(plugin.msg("tpa.outgoing-cancelled"));
        return true;
    }
}
