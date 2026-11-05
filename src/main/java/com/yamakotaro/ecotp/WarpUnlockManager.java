package com.yamakotaro.ecotp;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ワープの解放: ワールドのスポーンから遠いワープ (または config で値段を決めたワープ) は、
 * 初回だけ解放料金を払うと使えるようになる。解放済みの一覧は warp-unlocks.yml に保存する。
 */
public class WarpUnlockManager {

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public WarpUnlockManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warp-unlocks.yml");
        this.data = YamlIo.load(file);
    }

    /** @return このワープの解放料金。0 なら解放不要。 */
    public double price(String name, Location warp) {
        if (!plugin.getConfig().getBoolean("warp-unlock.enabled", true)) {
            return 0;
        }
        String override = "warp-unlock.prices." + name.toLowerCase();
        if (plugin.getConfig().isSet(override)) {
            return Math.max(0, plugin.getConfig().getDouble(override));
        }
        World world = warp == null ? null : warp.getWorld();
        if (world == null) {
            return 0;
        }
        double distance = world.getSpawnLocation().distance(warp);
        double minDistance = plugin.getConfig().getDouble("warp-unlock.min-distance", 1000.0);
        if (distance < minDistance) {
            return 0;
        }
        double per1000 = plugin.getConfig().getDouble("warp-unlock.price-per-1000-blocks", 1000.0);
        return Math.max(0, Math.ceil(distance / 1000.0) * per1000);
    }

    public boolean isUnlocked(Player player, String name, Location warp) {
        return player.hasPermission("ecotp.warp.unlockall") || price(name, warp) <= 0
                || data.getStringList("players." + player.getUniqueId()).contains(name.toLowerCase());
    }

    public void unlock(UUID uuid, String name) {
        List<String> unlocked = new ArrayList<>(data.getStringList("players." + uuid));
        if (!unlocked.contains(name.toLowerCase())) {
            unlocked.add(name.toLowerCase());
        }
        data.set("players." + uuid, unlocked);
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save warp-unlocks.yml", e);
        }
    }
}
