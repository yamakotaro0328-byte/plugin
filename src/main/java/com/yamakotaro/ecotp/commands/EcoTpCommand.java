package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
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

    private static final List<String> SUBCOMMANDS = List.of("reload");

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
        if (args.length != 1 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(plugin.msg("ecotp.usage"));
            return true;
        }

        plugin.reloadConfig();
        plugin.getMessages().reload();
        sender.sendMessage(plugin.msg("ecotp.reloaded"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.filterPrefix(SUBCOMMANDS, args[0]);
        }
        return Collections.emptyList();
    }
}
