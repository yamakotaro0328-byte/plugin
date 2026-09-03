package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * 投票サイトでの投票(NuVotifier等)への報酬。投票時にオンラインならその場で、
 * オフラインなら次回ログイン時に付与する(pending-votes.ymlで保留分を永続化)。
 */
public class VoteRewardManager {

    private final EcoTpPlugin plugin;
    private final Set<String> pendingUsernames = new HashSet<>();
    private final File file;

    public VoteRewardManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-votes.yml");
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("vote-reward.enabled", true);
    }

    private double rewardAmount() {
        return plugin.getConfig().getDouble("vote-reward.amount", 1000.0);
    }

    public void handleVote(String username, String serviceName) {
        if (!isEnabled()) {
            return;
        }
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            reward(online);
            return;
        }
        pendingUsernames.add(username.toLowerCase());
        save();
    }

    /** ログイン時に呼び出す。オフライン中に届いた投票報酬が保留されていれば付与する。 */
    public void onPlayerJoin(Player player) {
        if (pendingUsernames.remove(player.getName().toLowerCase())) {
            save();
            reward(player);
        }
    }

    private void reward(Player player) {
        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            plugin.getLogger().warning("Could not reward " + player.getName() + " for voting: no economy available yet.");
            return;
        }
        double amount = rewardAmount();
        economy.depositPlayer(player, amount);
        player.sendMessage(plugin.msg("vote.thanks", "amount", ChatUtil.formatMoney(amount)));
        Bukkit.broadcastMessage(plugin.getMessages().get("vote.broadcast",
                "player", player.getName(), "amount", ChatUtil.formatMoney(amount)));
    }

    private void load() {
        pendingUsernames.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlIo.load(file);
        pendingUsernames.addAll(data.getStringList("pending"));
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("pending", new ArrayList<>(pendingUsernames));
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save pending-votes.yml", e);
        }
    }
}
