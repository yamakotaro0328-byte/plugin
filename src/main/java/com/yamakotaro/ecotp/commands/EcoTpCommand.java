package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.VotifierServer;
import com.yamakotaro.ecotp.TabCompleteUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

/**
 * 管理者用: /ecotp reload — config.yml と messages.yml を再読み込みする。
 * storage.type や economy.enabled のようにサーバー起動時にしか決まらない設定は
 * このコマンドでは反映されない (再起動が必要)。
 */
public class EcoTpCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "votetest");

    private final EcoTpPlugin plugin;

    public EcoTpCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecotp.admin")) {
            sender.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }
        if (args.length >= 2 && "votetest".equalsIgnoreCase(args[0])) {
            return voteTest(sender, args);
        }
        if (args.length != 1 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(plugin.msg("ecotp.usage"));
            return true;
        }

        plugin.reloadConfig();
        plugin.getMessages().reload();
        sender.sendMessage(plugin.msg("ecotp.reloaded"));
        return true;
    }

    /** /ecotp votetest <名前> [v1|v2|v1-badkey|v2-badtoken|empty|slow] [real] */
    private boolean voteTest(CommandSender sender, String[] args) {
        VotifierServer server = plugin.getVotifierServer();
        if (server == null || !server.isRunning()) {
            sender.sendMessage(plugin.msg("ecotp.votetest-not-running"));
            return true;
        }
        String name = args[1];
        VotifierServer.TestMode mode = args.length >= 3 ? VotifierServer.TestMode.parse(args[2]) : VotifierServer.TestMode.V2;
        if (mode == null) {
            sender.sendMessage(plugin.msg("ecotp.usage"));
            return true;
        }
        boolean real = args.length >= 4 && "real".equalsIgnoreCase(args[3]);
        sender.sendMessage(plugin.msg("ecotp.votetest-sending", "mode", mode.name().toLowerCase().replace('_', '-'),
                "player", name));
        server.sendTestVote(name, mode, real, result ->
                sender.sendMessage(plugin.msg("ecotp.votetest-result", "result", result)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 3 && "votetest".equalsIgnoreCase(args[0])) {
            return TabCompleteUtil.filterPrefix(List.of("v1", "v2", "v1-badkey", "v2-badtoken", "empty", "slow"), args[2]);
        }
        if (args.length == 4 && "votetest".equalsIgnoreCase(args[0])) {
            return TabCompleteUtil.filterPrefix(List.of("real"), args[3]);
        }
        return Collections.emptyList();
    }
}
