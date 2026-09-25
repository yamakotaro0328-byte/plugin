package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.VoteRewardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /votetop : 累計投票数のランキング。プレイヤーが実行した場合は自分の投票数も表示する。
 */
public class VoteTopCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public VoteTopCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        VoteRewardManager votes = plugin.getVoteRewardManager();
        if (!votes.isEnabled()) {
            sender.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        if (!sender.hasPermission("ecotp.votetop")) {
            sender.sendMessage(plugin.msg("general.no-permission"));
            return true;
        }

        List<VoteRewardManager.VoteEntry> top = votes.getTopVoters(plugin.getConfig().getInt("vote-reward.top-limit", 10));
        if (top.isEmpty()) {
            sender.sendMessage(plugin.msg("votetop.empty"));
            return true;
        }
        sender.sendMessage(plugin.msg("votetop.header"));
        int rank = 1;
        for (VoteRewardManager.VoteEntry entry : top) {
            sender.sendMessage(plugin.getMessages().get("votetop.line",
                    "rank", rank, "player", entry.name(), "count", entry.votes()));
            rank++;
        }
        if (sender instanceof Player player) {
            sender.sendMessage(plugin.getMessages().get("votetop.self", "count", votes.getVoteCount(player.getName())));
        }
        return true;
    }
}
