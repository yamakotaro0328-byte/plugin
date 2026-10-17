package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * サーバー目標: 全員の投票数の合計が vote-goal.target に達すると、その時オンラインの
 * 全員に報酬を配り、カウントは 0 からやり直しになる。進み具合は vote-goal.yml に保存する。
 */
public class VoteGoalManager {

    private final EcoTpPlugin plugin;
    private final File file;
    private int progress;

    public VoteGoalManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vote-goal.yml");
        this.progress = YamlConfiguration.loadConfiguration(file).getInt("progress", 0);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("vote-goal.enabled", true);
    }

    public int getTarget() {
        return Math.max(1, plugin.getConfig().getInt("vote-goal.target", 50));
    }

    public int getProgress() {
        return progress;
    }

    /** 投票が1件届くたびに呼ぶ (オフラインの人への投票も数える)。 */
    public void addVote() {
        if (!isEnabled()) {
            return;
        }
        progress++;
        int target = getTarget();
        if (progress >= target) {
            progress = 0;
            save();
            reachGoal(target);
            return;
        }
        save();
        int every = plugin.getConfig().getInt("vote-goal.announce-every", 10);
        if (every > 0 && progress % every == 0) {
            Bukkit.broadcastMessage(plugin.getMessages().get("vote-goal.progress",
                    "progress", String.valueOf(progress), "target", String.valueOf(target),
                    "remaining", String.valueOf(target - progress)));
        }
    }

    private void reachGoal(int target) {
        double reward = plugin.getConfig().getDouble("vote-goal.reward", 1000.0);
        Bukkit.broadcastMessage(plugin.getMessages().get("vote-goal.reached",
                "target", String.valueOf(target), "reward", ChatUtil.formatMoney(reward)));
        Economy economy = plugin.getEconomyHolder().get();
        if (reward <= 0 || economy == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (economy.depositPlayer(player, reward).transactionSuccess()) {
                player.sendMessage(plugin.msg("vote-goal.received", "reward", ChatUtil.formatMoney(reward)));
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("progress", progress);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save vote-goal.yml", e);
        }
    }
}
