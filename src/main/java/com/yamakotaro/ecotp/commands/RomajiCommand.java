package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /roma : 自分のチャットメッセージのローマ字→日本語自動変換 (RomajiConversionListener 参照)
 * をオン/オフする。
 */
public class RomajiCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public RomajiCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!plugin.isFeatureEnabled("romaji-conversion")) {
            player.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!player.hasPermission("ecotp.romaji")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        boolean enabled = plugin.getRomajiConversionManager().toggle(player.getUniqueId());
        player.sendMessage(plugin.msg(enabled ? "romaji.enabled" : "romaji.disabled"));
        return true;
    }
}
