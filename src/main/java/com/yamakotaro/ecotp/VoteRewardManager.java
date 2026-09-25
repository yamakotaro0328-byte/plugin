package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 投票サイトでの投票(NuVotifier等)への報酬。投票時にオンラインならその場で、
 * オフラインなら次回ログイン時に付与する(pending-votes.ymlで保留分を永続化)。
 * 累計投票数も記録し (vote-counts.yml)、/votetop のランキングと、
 * vote-reward.milestones で設定した回数に達した時の追加報酬に使う。
 */
public class VoteRewardManager {

    private final EcoTpPlugin plugin;
    private final Map<String, Double> pendingAmounts = new HashMap<>();
    // PlaceholderAPI (%ecotp_votes%) が非同期スレッドから読むことがあるため concurrent にしておく。
    private final Map<String, Integer> voteCounts = new ConcurrentHashMap<>();
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();
    private final File file;
    private final File countsFile;
    // The classic Votifier(V1) protocol never sends any success/failure acknowledgment back to
    // the voting site - if a site's own request appears to fail (timeout, no response) it may
    // silently retry, and each retry looks like a brand new, entirely valid vote to us with no
    // way to tell it apart from a genuine repeat vote. Without this, a site retrying a "failed"
    // request would reward the player once per retry even though the site itself reports failure.
    private final Map<String, Long> lastRewardMillis = new HashMap<>();

    public VoteRewardManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-votes.yml");
        this.countsFile = new File(plugin.getDataFolder(), "vote-counts.yml");
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("vote-reward.enabled", true);
    }

    public double rewardAmount() {
        return plugin.getConfig().getDouble("vote-reward.amount", 1000.0);
    }

    private long duplicateWindowMillis() {
        return plugin.getConfig().getLong("vote-reward.duplicate-window-seconds", 60) * 1000L;
    }

    /** config.yml の vote-reward.sites (name/url のペアの一覧)。管理者が未設定なら空。 */
    public List<VoteSite> getSites() {
        List<VoteSite> result = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("vote-reward.sites")) {
            Object name = entry.get("name");
            Object url = entry.get("url");
            if (name != null && url != null) {
                result.add(new VoteSite(name.toString(), url.toString()));
            }
        }
        return result;
    }

    public record VoteSite(String name, String url) {
    }

    public void handleVote(String username, String serviceName) {
        if (!isEnabled()) {
            return;
        }
        if (isRecentDuplicate(username, serviceName)) {
            plugin.getLogger().info("Ignored a vote for " + username + " (" + serviceName
                    + ") - the same username was already rewarded by this same site within the last "
                    + (duplicateWindowMillis() / 1000L) + "s, most likely a retried connection "
                    + "from the voting site rather than a second real vote.");
            return;
        }
        String key = username.toLowerCase();
        int count = voteCounts.merge(key, 1, Integer::sum);
        double bonus = milestoneBonus(count);
        double amount = rewardAmount() + bonus;

        Player online = Bukkit.getPlayerExact(username);
        displayNames.put(key, online != null ? online.getName() : username);
        saveCounts();

        if (online != null) {
            reward(online, amount);
        } else {
            // オフラインだと reward() (チャット通知・全体ブロードキャスト) が一切呼ばれないため、
            // このログが無いと投票が実際に届いたことをコンソールから確認する手段が無かった。
            plugin.getLogger().info("Received a vote for " + username + " (" + serviceName
                    + ") while offline - reward queued and will be paid out on next login.");
            pendingAmounts.merge(key, amount, Double::sum);
            savePending();
        }
        if (bonus > 0) {
            Bukkit.broadcastMessage(plugin.getMessages().get("vote.milestone",
                    "player", displayNames.get(key), "count", count, "bonus", ChatUtil.formatMoney(bonus)));
        }
    }

    /** config.yml の vote-reward.milestones で、累計投票数がちょうどその回数に達した時の追加報酬。 */
    private double milestoneBonus(int count) {
        return plugin.getConfig().getDouble("vote-reward.milestones." + count, 0.0);
    }

    public int getVoteCount(String username) {
        return voteCounts.getOrDefault(username.toLowerCase(), 0);
    }

    /** @return 累計投票数の多い順 (同数なら名前順)。 */
    public List<VoteEntry> getTopVoters(int limit) {
        List<VoteEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            entries.add(new VoteEntry(displayNames.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()));
        }
        entries.sort(Comparator.comparingInt(VoteEntry::votes).reversed()
                .thenComparing(VoteEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    public record VoteEntry(String name, int votes) {
    }

    /** @return true if this username was already credited for a vote within the duplicate
     * window - marks the current attempt's timestamp as a side effect either way, since a
     * genuinely new vote (accepted or not) is what starts the next window. */
    private boolean isRecentDuplicate(String username, String serviceName) {
        // サイトごとに判定する: 再送は必ず同じサイトから来るので、別々のサイトでの続けての
        // 投票まで「再送」扱いで捨ててしまわないように。
        String key = username.toLowerCase() + "|" + serviceName;
        long now = System.currentTimeMillis();
        Long last = lastRewardMillis.get(key);
        boolean duplicate = last != null && now - last < duplicateWindowMillis();
        if (!duplicate) {
            lastRewardMillis.put(key, now);
        }
        return duplicate;
    }

    /** ログイン時に呼び出す。オフライン中に届いた投票報酬が保留されていれば、まとめて付与する。 */
    public void onPlayerJoin(Player player) {
        String key = player.getName().toLowerCase();
        if (voteCounts.containsKey(key)) {
            displayNames.put(key, player.getName());
        }
        Double amount = pendingAmounts.remove(key);
        if (amount != null) {
            savePending();
            reward(player, amount);
        }
    }

    private void reward(Player player, double amount) {
        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            // Previously logged to console only - the voting player saw nothing at all and had
            // no way to know their vote had been received but not rewarded.
            plugin.getLogger().warning("Could not reward " + player.getName() + " for voting: no economy available yet.");
            player.sendMessage(plugin.msg("general.no-economy"));
            return;
        }
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
        pendingAmounts.clear();
        YamlConfiguration pending = YamlIo.load(file);
        // 以前の形式: ユーザー名の一覧 (Setだったため、オフライン中に複数サイトで投票しても
        // 1回分しか保留されなかった)。読み込み時は1件=基本報酬1回分として引き継ぐ。
        for (String legacy : pending.getStringList("pending")) {
            pendingAmounts.merge(legacy.toLowerCase(), rewardAmount(), Double::sum);
        }
        ConfigurationSection amounts = pending.getConfigurationSection("pending-amounts");
        if (amounts != null) {
            for (String key : amounts.getKeys(false)) {
                pendingAmounts.merge(key.toLowerCase(), amounts.getDouble(key), Double::sum);
            }
        }

        voteCounts.clear();
        displayNames.clear();
        YamlConfiguration counts = YamlIo.load(countsFile);
        ConfigurationSection section = counts.getConfigurationSection("votes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                voteCounts.put(key, section.getInt(key + ".count"));
                displayNames.put(key, section.getString(key + ".name", key));
            }
        }
    }

    private void savePending() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<String, Double> entry : pendingAmounts.entrySet()) {
            data.set("pending-amounts." + entry.getKey(), entry.getValue());
        }
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save pending-votes.yml", e);
        }
    }

    private void saveCounts() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            data.set("votes." + entry.getKey() + ".name", displayNames.getOrDefault(entry.getKey(), entry.getKey()));
            data.set("votes." + entry.getKey() + ".count", entry.getValue());
        }
        try {
            YamlIo.save(data, countsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save vote-counts.yml", e);
        }
    }
}
