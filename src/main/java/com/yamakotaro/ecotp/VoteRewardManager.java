package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    // The classic Votifier(V1) protocol never sends any success/failure acknowledgment back to
    // the voting site - if a site's own request appears to fail (timeout, no response) it may
    // silently retry, and each retry looks like a brand new, entirely valid vote to us with no
    // way to tell it apart from a genuine repeat vote. Without this, a site retrying a "failed"
    // request would reward the player once per retry even though the site itself reports failure.
    private final Map<String, Long> lastRewardMillis = new HashMap<>();

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

    private long duplicateWindowMillis() {
        return plugin.getConfig().getLong("vote-reward.duplicate-window-seconds", 60) * 1000L;
    }

    public void handleVote(String username, String serviceName) {
        if (!isEnabled()) {
            return;
        }
        if (isRecentDuplicate(username)) {
            plugin.getLogger().info("Ignored a vote for " + username + " (" + serviceName
                    + ") - the same username was already rewarded within the last "
                    + (duplicateWindowMillis() / 1000L) + "s, most likely a retried connection "
                    + "from the voting site rather than a second real vote.");
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

    /** @return true if this username was already credited for a vote within the duplicate
     * window - marks the current attempt's timestamp as a side effect either way, since a
     * genuinely new vote (accepted or not) is what starts the next window. */
    private boolean isRecentDuplicate(String username) {
        String key = username.toLowerCase();
        long now = System.currentTimeMillis();
        Long last = lastRewardMillis.get(key);
        boolean duplicate = last != null && now - last < duplicateWindowMillis();
        if (!duplicate) {
            lastRewardMillis.put(key, now);
        }
        return duplicate;
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
            // Previously logged to console only - the voting player saw nothing at all and had
            // no way to know their vote had been received but not rewarded.
            plugin.getLogger().warning("Could not reward " + player.getName() + " for voting: no economy available yet.");
            player.sendMessage(plugin.msg("general.no-economy"));
            return;
        }
        double amount = rewardAmount();
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            // Previously ignored entirely - the player got the "thanks for voting" message even
            // when the deposit itself failed (only possible with an external Vault economy).
            plugin.getLogger().warning("Failed to deposit the vote reward for " + player.getName() + ": " + response.errorMessage);
            player.sendMessage(plugin.msg("general.no-economy"));
            return;
        }
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
