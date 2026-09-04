package com.yamakotaro.ecocosmetics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * カタログ(config.yml の cosmetics)とプレイヤーごとの所持/装備状況(player-data.yml)を管理する。
 * どちらも YamlIo 経由でUTF-8として読み書きする。
 */
public class CosmeticManager {

    public enum BuyResult {
        SUCCESS, ALREADY_OWNED, INSUFFICIENT_FUNDS, NO_ECONOMY, UNKNOWN
    }

    private final EcoCosmeticsPlugin plugin;
    private final File dataFile;
    private final Map<String, CosmeticDefinition> catalog = new LinkedHashMap<>();
    private YamlConfiguration playerData = new YamlConfiguration();

    public CosmeticManager(EcoCosmeticsPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "player-data.yml");
    }

    public void load() {
        loadCatalog();
        this.playerData = YamlIo.load(dataFile);
    }

    public void save() {
        try {
            YamlIo.save(playerData, dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player-data.yml", e);
        }
    }

    private void loadCatalog() {
        catalog.clear();
        ConfigurationSection section = plugin.config().getConfigurationSection("cosmetics");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            Category category;
            try {
                category = Category.valueOf(entry.getString("category", "").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping cosmetic '" + id + "': unknown category.");
                continue;
            }
            Material icon;
            try {
                icon = Material.valueOf(entry.getString("icon", "").toUpperCase());
            } catch (IllegalArgumentException e) {
                icon = Material.BARRIER;
            }
            Particle particle = null;
            String particleName = entry.getString("particle");
            if (particleName != null) {
                try {
                    particle = Particle.valueOf(particleName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Cosmetic '" + id + "': unknown particle '" + particleName + "'.");
                }
            }
            String displayName = ChatUtil.color(entry.getString("display-name", id));
            double price = entry.getDouble("price", 0.0);
            String prefix = entry.getString("prefix");
            catalog.put(id, new CosmeticDefinition(id, category, displayName, icon, price, particle, prefix));
        }
    }

    public Map<String, CosmeticDefinition> getCatalog() {
        return catalog;
    }

    public CosmeticDefinition get(String id) {
        return catalog.get(id);
    }

    public List<CosmeticDefinition> getByCategory(Category category) {
        return catalog.values().stream()
                .filter(c -> c.category() == category)
                .collect(Collectors.toList());
    }

    private String ownedPath(UUID uuid) {
        return "players." + uuid + ".owned";
    }

    private String equippedPath(UUID uuid, Category category) {
        return "players." + uuid + ".equipped." + category.name();
    }

    public List<String> getOwned(UUID uuid) {
        return playerData.getStringList(ownedPath(uuid));
    }

    public boolean owns(UUID uuid, String id) {
        return getOwned(uuid).contains(id);
    }

    public String getEquipped(UUID uuid, Category category) {
        return playerData.getString(equippedPath(uuid, category));
    }

    public String getEquippedTitlePrefix(UUID uuid) {
        String id = getEquipped(uuid, Category.TITLE);
        if (id == null) {
            return null;
        }
        CosmeticDefinition definition = catalog.get(id);
        return definition == null ? null : definition.prefix();
    }

    public CosmeticDefinition getEquippedParticle(UUID uuid) {
        String id = getEquipped(uuid, Category.PARTICLE);
        return id == null ? null : catalog.get(id);
    }

    public CosmeticDefinition getEquippedJoinEffect(UUID uuid) {
        String id = getEquipped(uuid, Category.JOIN_EFFECT);
        return id == null ? null : catalog.get(id);
    }

    public boolean give(UUID uuid, String id) {
        if (!catalog.containsKey(id) || owns(uuid, id)) {
            return false;
        }
        List<String> owned = new ArrayList<>(getOwned(uuid));
        owned.add(id);
        playerData.set(ownedPath(uuid), owned);
        save();
        return true;
    }

    public BuyResult buy(Player player, String id) {
        CosmeticDefinition definition = catalog.get(id);
        if (definition == null) {
            return BuyResult.UNKNOWN;
        }
        if (owns(player.getUniqueId(), id)) {
            return BuyResult.ALREADY_OWNED;
        }
        var economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            return BuyResult.NO_ECONOMY;
        }
        if (!economy.has(player, definition.price())) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }
        economy.withdrawPlayer(player, definition.price());
        give(player.getUniqueId(), id);
        return BuyResult.SUCCESS;
    }

    public void setEquipped(UUID uuid, Category category, String id) {
        playerData.set(equippedPath(uuid, category), id);
        save();
    }

    public void toggleEquip(UUID uuid, String id) {
        CosmeticDefinition definition = catalog.get(id);
        if (definition == null) {
            return;
        }
        String current = getEquipped(uuid, definition.category());
        if (id.equals(current)) {
            setEquipped(uuid, definition.category(), null);
        } else {
            setEquipped(uuid, definition.category(), id);
        }
    }
}
