package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.TabCompleteUtil;
import com.yamakotaro.ecotp.TpaModeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** /tpamode [ask|accept|deny] : tpa / tphere を受けた時の動きを決める。引数なしで今の設定を表示。 */
public class TpaModeCommand implements CommandExecutor, TabCompleter {

    private final EcoTpPlugin plugin;

    public TpaModeCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("general.players-only"));
            return true;
        }
        if (!player.hasPermission("ecotp.tpa")) {
            player.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        TpaModeManager modes = plugin.getTpaModeManager();
        if (args.length == 0) {
            player.sendMessage(plugin.msg("tpamode.current",
                    "mode", plugin.getMessages().get("tpamode.names." + modes.getMode(player.getUniqueId()).key())));
            player.sendMessage(plugin.msg("tpamode.usage"));
            return true;
        }
        TpaModeManager.Mode mode = TpaModeManager.Mode.parse(args[0]);
        if (mode == null) {
            player.sendMessage(plugin.msg("tpamode.usage"));
            return true;
        }
        modes.setMode(player.getUniqueId(), mode);
        player.sendMessage(plugin.msg("tpamode.changed",
                "mode", plugin.getMessages().get("tpamode.names." + mode.key())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(List.of("ask", "accept", "deny"), args[0]);
        }
        return Collections.emptyList();
    }
}
