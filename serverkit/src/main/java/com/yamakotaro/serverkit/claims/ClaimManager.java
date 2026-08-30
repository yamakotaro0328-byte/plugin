package com.yamakotaro.serverkit.claims;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 自由矩形選択のクレーム(GriefPreventionの"クレームブロック"と同じ考え方)。
 * プレイヤーは所持しているクレームブロック数(オンライン中に一定間隔で自動加算)の
 * 範囲内でしか土地をクレームできない。削除すればブロックはそのまま再利用できる
 * (面積の合計を都度計算しているだけで、個別に「消費」を記録しているわけではない)。
 */
public class ClaimManager {

    public enum CreateResult {
        SUCCESS, DISABLED, NAME_TAKEN, DIFFERENT_WORLDS, INSUFFICIENT_BLOCKS, OVERLAPS_CLAIM, OVERLAPS_EXTERNAL
    }

    public enum RemoveResult { SUCCESS, NOT_FOUND }

    public enum TrustResult { SUCCESS, NOT_FOUND, ALREADY_TRUSTED, NOT_TRUSTED }

    private final Plugin plugin;
    private final TerritoryGuard territoryGuard;
    private final Map<UUID, Long> blockBalances = new HashMap<>();
    private final Map<String, List<Claim>> claimsByWorld = new HashMap<>();
    private final File file;

    public ClaimManager(Plugin plugin, TerritoryGuard territoryGuard) {
        this.plugin = plugin;
        this.territoryGuard = territoryGuard;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("claims.enabled", true);
    }

    public long startingBlocks() {
        return plugin.getConfig().getLong("claims.starting-blocks", 100);
    }

    public long accrualAmount() {
        return plugin.getConfig().getLong("claims.accrual-amount", 100);
    }

    public long accrualIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getLong("claims.accrual-interval-minutes", 60));
    }

    /** 0 = 上限なし。 */
    public long maxBlocks() {
        return plugin.getConfig().getLong("claims.max-blocks", 0);
    }

    public long getBalance(UUID uuid) {
        return blockBalances.computeIfAbsent(uuid, k -> startingBlocks());
    }

    public void addBlocks(UUID uuid, long amount) {
        long balance = getBalance(uuid) + amount;
        long max = maxBlocks();
        if (max > 0) {
            balance = Math.min(balance, max);
        }
        blockBalances.put(uuid, balance);
        save();
    }

    public List<Claim> claimsForOwner(UUID owner) {
        List<Claim> result = new ArrayList<>();
        for (List<Claim> claims : claimsByWorld.values()) {
            for (Claim claim : claims) {
                if (claim.getOwner().equals(owner)) {
                    result.add(claim);
                }
            }
        }
        return result;
    }

    public long usedBlocks(UUID owner) {
        long used = 0;
        for (Claim claim : claimsForOwner(owner)) {
            used += claim.area();
        }
        return used;
    }

    public Claim findClaimAt(String world, int x, int z) {
        for (Claim claim : claimsByWorld.getOrDefault(world, List.of())) {
            if (claim.contains(world, x, z)) {
                return claim;
            }
        }
        return null;
    }

    public Claim findClaimAt(Location location) {
        return findClaimAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    public Claim findByName(UUID owner, String name) {
        for (Claim claim : claimsForOwner(owner)) {
            if (claim.getName().equalsIgnoreCase(name)) {
                return claim;
            }
        }
        return null;
    }

    public CreateResult createClaim(Player owner, String name, Location pointA, Location pointB) {
        if (!isEnabled()) {
            return CreateResult.DISABLED;
        }
        if (findByName(owner.getUniqueId(), name) != null) {
            return CreateResult.NAME_TAKEN;
        }
        if (!pointA.getWorld().equals(pointB.getWorld())) {
            return CreateResult.DIFFERENT_WORLDS;
        }
        String world = pointA.getWorld().getName();
        int minX = Math.min(pointA.getBlockX(), pointB.getBlockX());
        int maxX = Math.max(pointA.getBlockX(), pointB.getBlockX());
        int minZ = Math.min(pointA.getBlockZ(), pointB.getBlockZ());
        int maxZ = Math.max(pointA.getBlockZ(), pointB.getBlockZ());
        Claim candidate = new Claim(name, owner.getUniqueId(), world, minX, minZ, maxX, maxZ);

        long available = getBalance(owner.getUniqueId()) - usedBlocks(owner.getUniqueId());
        if (candidate.area() > available) {
            return CreateResult.INSUFFICIENT_BLOCKS;
        }
        for (Claim existing : claimsByWorld.getOrDefault(world, List.of())) {
            if (candidate.overlaps(existing)) {
                return CreateResult.OVERLAPS_CLAIM;
            }
        }
        boolean checkTowny = plugin.getConfig().getBoolean("claims.respect-towny", true);
        boolean checkLands = plugin.getConfig().getBoolean("claims.respect-lands", true);
        if ((checkTowny || checkLands) && overlapsExternalTerritory(pointA.getWorld(), minX, minZ, maxX, maxZ, checkTowny, checkLands)) {
            return CreateResult.OVERLAPS_EXTERNAL;
        }

        claimsByWorld.computeIfAbsent(world, k -> new ArrayList<>()).add(candidate);
        save();
        return CreateResult.SUCCESS;
    }

    private boolean overlapsExternalTerritory(World world, int minX, int minZ, int maxX, int maxZ,
                                               boolean checkTowny, boolean checkLands) {
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (territoryGuard.isExternallyClaimed(world, (cx << 4) + 8, (cz << 4) + 8, checkTowny, checkLands)) {
                    return true;
                }
            }
        }
        return false;
    }

    public RemoveResult removeClaim(UUID owner, String name) {
        Claim claim = findByName(owner, name);
        if (claim == null) {
            return RemoveResult.NOT_FOUND;
        }
        claimsByWorld.getOrDefault(claim.getWorld(), List.of()).remove(claim);
        save();
        return RemoveResult.SUCCESS;
    }

    public TrustResult trust(UUID owner, String claimName, UUID target) {
        Claim claim = findByName(owner, claimName);
        if (claim == null) {
            return TrustResult.NOT_FOUND;
        }
        if (!claim.getTrusted().add(target)) {
            return TrustResult.ALREADY_TRUSTED;
        }
        save();
        return TrustResult.SUCCESS;
    }

    public TrustResult untrust(UUID owner, String claimName, UUID target) {
        Claim claim = findByName(owner, claimName);
        if (claim == null) {
            return TrustResult.NOT_FOUND;
        }
        if (!claim.getTrusted().remove(target)) {
            return TrustResult.NOT_TRUSTED;
        }
        save();
        return TrustResult.SUCCESS;
    }

    private void load() {
        blockBalances.clear();
        claimsByWorld.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection balancesSection = data.getConfigurationSection("balances");
        if (balancesSection != null) {
            for (String key : balancesSection.getKeys(false)) {
                try {
                    blockBalances.put(UUID.fromString(key), balancesSection.getLong(key));
                } catch (IllegalArgumentException ignored) {
                    // Skip a malformed entry rather than failing the whole load.
                }
            }
        }
        ConfigurationSection claimsSection = data.getConfigurationSection("claims");
        if (claimsSection != null) {
            for (String ownerKey : claimsSection.getKeys(false)) {
                UUID owner;
                try {
                    owner = UUID.fromString(ownerKey);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ConfigurationSection ownerSection = claimsSection.getConfigurationSection(ownerKey);
                if (ownerSection == null) {
                    continue;
                }
                for (String name : ownerSection.getKeys(false)) {
                    String base = name + ".";
                    String world = ownerSection.getString(base + "world");
                    if (world == null) {
                        continue;
                    }
                    Claim claim = new Claim(name, owner, world,
                            ownerSection.getInt(base + "minX"), ownerSection.getInt(base + "minZ"),
                            ownerSection.getInt(base + "maxX"), ownerSection.getInt(base + "maxZ"));
                    for (String trustedUuid : ownerSection.getStringList(base + "trusted")) {
                        try {
                            claim.getTrusted().add(UUID.fromString(trustedUuid));
                        } catch (IllegalArgumentException ignored) {
                            // Skip a malformed entry rather than failing the whole load.
                        }
                    }
                    claimsByWorld.computeIfAbsent(world, k -> new ArrayList<>()).add(claim);
                }
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : blockBalances.entrySet()) {
            data.set("balances." + entry.getKey(), entry.getValue());
        }
        for (List<Claim> claims : claimsByWorld.values()) {
            for (Claim claim : claims) {
                String base = "claims." + claim.getOwner() + "." + claim.getName() + ".";
                data.set(base + "world", claim.getWorld());
                data.set(base + "minX", claim.getMinX());
                data.set(base + "minZ", claim.getMinZ());
                data.set(base + "maxX", claim.getMaxX());
                data.set(base + "maxZ", claim.getMaxZ());
                List<String> trusted = new ArrayList<>();
                for (UUID uuid : claim.getTrusted()) {
                    trusted.add(uuid.toString());
                }
                data.set(base + "trusted", trusted);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save claims.yml", e);
        }
    }
}
