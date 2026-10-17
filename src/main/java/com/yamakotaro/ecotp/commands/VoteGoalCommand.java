package com.yamakotaro.ecotp.commands;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.VoteGoalManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /votegoal : サーバー目標 (みんなの投票数の合計) の進み具合を表示する。
 */
public class VoteGoalCommand implements CommandExecutor {

    private final EcoTpPlugin plugin;

    public VoteGoalCommand(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        VoteGoalManager goal = plugin.getVoteGoalManager();
        if (!goal.isEnabled()) {
            sender.sendMessage(plugin.msg("general.feature-disabled"));
            return true;
        }
        int target = goal.getTarget();
        sender.sendMessage(plugin.msg("vote-goal.status",
                "progress", String.valueOf(goal.getProgress()), "target", String.valueOf(target),
                "remaining", String.valueOf(target - goal.getProgress()),
                "reward", ChatUtil.formatMoney(plugin.getConfig().getDouble("vote-goal.reward", 1000.0))));
        return true;
    }
}
