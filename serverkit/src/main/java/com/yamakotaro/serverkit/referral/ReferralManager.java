package com.yamakotaro.serverkit.referral;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A one-time invite-reward system, not a friend list: a genuinely new player claims who invited
 * them, the named inviter confirms it, and the inviter gets a one-time Vault reward per pair.
 * The "claimed within N minutes of first join" check exists specifically so two long-established
 * accounts can't declare each other "invited" after the fact just to farm the reward.
 */
public class ReferralManager {

    private enum Status { PENDING, CONFIRMED }

    private record Claim(UUID inviter, Status status) {
    }

    private final Plugin plugin;
    private final EconomyHolder economyHolder;
    private final Map<UUID, Claim> claims = new HashMap<>();
    private final Set<String> rewardedPairs = new HashSet<>();
    private final File file;

    public ReferralManager(Plugin plugin, EconomyHolder economyHolder) {
        this.plugin = plugin;
        this.economyHolder = economyHolder;
        this.file = new File(plugin.getDataFolder(), "referrals.yml");
    }

    public enum ClaimResult { SUCCESS, CANNOT_TARGET_SELF, TARGET_NEVER_PLAYED, ALREADY_CLAIMED, NOT_NEW_ENOUGH }

    public enum ConfirmResult { SUCCESS, NO_PENDING_CLAIM, ALREADY_REWARDED, NO_ECONOMY }

    public ClaimResult claim(Player claimant, OfflinePlayer inviter) {
        if (claimant.getUniqueId().equals(inviter.getUniqueId())) {
            return ClaimResult.CANNOT_TARGET_SELF;
        }
        if (!inviter.hasPlayedBefore() && !inviter.isOnline()) {
            return ClaimResult.TARGET_NEVER_PLAYED;
        }
        if (claims.containsKey(claimant.getUniqueId())) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        long windowMinutes = windowMinutes();
        long firstPlayed = claimant.getFirstPlayed();
        long ageMillis = System.currentTimeMillis() - firstPlayed;
        if (ageMillis > windowMinutes * 60_000L) {
            return ClaimResult.NOT_NEW_ENOUGH;
        }
        claims.put(claimant.getUniqueId(), new Claim(inviter.getUniqueId(), Status.PENDING));
        save();
        return ClaimResult.SUCCESS;
    }

    public ConfirmResult confirm(Player inviter, OfflinePlayer claimant) {
        Claim claim = claims.get(claimant.getUniqueId());
        if (claim == null || !claim.inviter().equals(inviter.getUniqueId())) {
            return ConfirmResult.NO_PENDING_CLAIM;
        }
        if (claim.status() == Status.CONFIRMED) {
            return ConfirmResult.ALREADY_REWARDED;
        }
        String pairKey = pairKey(inviter.getUniqueId(), claimant.getUniqueId());
        if (rewardedPairs.contains(pairKey)) {
            return ConfirmResult.ALREADY_REWARDED;
        }
        Economy economy = economyHolder.get();
        if (economy == null) {
            return ConfirmResult.NO_ECONOMY;
        }
        double amount = plugin.getConfig().getDouble("referral.reward-amount", 500.0);
        economy.depositPlayer(inviter, amount);
        claims.put(claimant.getUniqueId(), new Claim(claim.inviter(), Status.CONFIRMED));
        rewardedPairs.add(pairKey);
        save();
        return ConfirmResult.SUCCESS;
    }

    public double rewardAmount() {
        return plugin.getConfig().getDouble("referral.reward-amount", 500.0);
    }

    public long windowMinutes() {
        return plugin.getConfig().getLong("referral.new-player-window-minutes", 60);
    }

    private String pairKey(UUID inviter, UUID claimant) {
        return inviter + ":" + claimant;
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.isConfigurationSection("claims")) {
            for (String key : config.getConfigurationSection("claims").getKeys(false)) {
                try {
                    UUID claimantId = UUID.fromString(key);
                    UUID inviterId = UUID.fromString(config.getString("claims." + key + ".inviter"));
                    Status status = Status.valueOf(config.getString("claims." + key + ".status", "PENDING"));
                    claims.put(claimantId, new Claim(inviterId, status));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "Skipping malformed referral claim entry: " + key, e);
                }
            }
        }
        rewardedPairs.addAll(config.getStringList("rewarded-pairs"));
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Claim> entry : claims.entrySet()) {
            String path = "claims." + entry.getKey();
            config.set(path + ".inviter", entry.getValue().inviter().toString());
            config.set(path + ".status", entry.getValue().status().name());
        }
        config.set("rewarded-pairs", new java.util.ArrayList<>(rewardedPairs));
        try {
            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save referrals.yml", e);
        }
    }
}
