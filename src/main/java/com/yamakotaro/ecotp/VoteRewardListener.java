package com.yamakotaro.ecotp;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.util.logging.Level;

/**
 * NuVotifier(および同じイベント/メソッド名を使う互換フォーク: Votifier, VotifierPlus等)
 * からの投票を、コンパイル時の依存なしで検知する。com.vexsoftware.votifier.model.VotifierEvent
 * をこのクラスのメソッドシグネチャに直接書くと、そのクラスが存在しないサーバーでは
 * NoClassDefFoundError でロードそのものに失敗する(EcoTpPluginがVaultのEconomy型を
 * 直接持てないのと同じ理由)ため、リフレクションで全て呼び出す。
 */
public class VoteRewardListener implements Listener {

    private final EcoTpPlugin plugin;

    public VoteRewardListener(EcoTpPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return NuVotifier(互換品含む)が見つかり、登録できた場合は true。 */
    @SuppressWarnings("unchecked")
    public boolean register() {
        Class<? extends Event> eventClass;
        try {
            eventClass = (Class<? extends Event>) Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
        } catch (ClassNotFoundException e) {
            return false;
        }
        EventExecutor executor = (listener, event) -> handle(event);
        Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin, false);
        plugin.getLogger().info("A Votifier-compatible plugin was found: vote rewards enabled.");
        return true;
    }

    private void handle(Event event) {
        try {
            Object vote = event.getClass().getMethod("getVote").invoke(event);
            String username = (String) vote.getClass().getMethod("getUsername").invoke(vote);
            String serviceName = (String) vote.getClass().getMethod("getServiceName").invoke(vote);
            plugin.getVoteRewardManager().handleVote(username, serviceName);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read a vote event via reflection", e);
        }
    }
}
