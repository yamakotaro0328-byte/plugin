package com.yamakotaro.ecopotions;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config.yml の potions からカタログを読み込み、飲むタイプのポーションアイテムを作る。
 * 所有・装備の概念は無い消耗品なので (EcoCosmeticsと違い) プレイヤーごとの永続データは持たない -
 * 購入したらその場でアイテムを渡すだけで、効果自体は実際に飲んだ時にバニラの飲用処理が発動する。
 */
public class PotionManager {

    public enum BuyResult {
        SUCCESS, INSUFFICIENT_FUNDS, NO_ECONOMY, UNKNOWN
    }

    private final EcoPotionsPlugin plugin;
    private final NamespacedKey markerKey;
    private final Map<String, PotionDefinition> catalog = new LinkedHashMap<>();

    public PotionManager(EcoPotionsPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "ecopotion");
    }

    public void load() {
        catalog.clear();
        ConfigurationSection section = plugin.config().getConfigurationSection("potions");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String displayName = ChatUtil.color(entry.getString("display-name", id));
            Color color = parseColor(entry.getString("color"));
            double price = entry.getDouble("price", 0.0);
            int durationSeconds = entry.getInt("duration-seconds", 60);
            List<EffectSpec> effects = new ArrayList<>();
            for (Map<?, ?> raw : entry.getMapList("effects")) {
                Object rawType = raw.get("type");
                if (rawType == null) {
                    continue;
                }
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(
                        NamespacedKey.minecraft(rawType.toString().toLowerCase(Locale.ROOT)));
                if (type == null) {
                    plugin.getLogger().warning("Potion '" + id + "': unknown effect type '" + rawType + "'.");
                    continue;
                }
                int amplifier = raw.get("amplifier") instanceof Number number ? number.intValue() : 0;
                effects.add(new EffectSpec(type, amplifier));
            }
            catalog.put(id, new PotionDefinition(id, displayName, color, price, durationSeconds, effects));
        }
    }

    private Color parseColor(String hex) {
        if (hex == null) {
            return null;
        }
        try {
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, PotionDefinition> getCatalog() {
        return catalog;
    }

    public PotionDefinition get(String id) {
        return catalog.get(id);
    }

    public boolean isEcoPotion(ItemStack stack) {
        if (stack == null || stack.getType() != Material.POTION) {
            return false;
        }
        var meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    public ItemStack createItem(PotionDefinition definition) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        PotionType base = Registry.POTION.get(NamespacedKey.minecraft("water"));
        if (base != null && meta != null) {
            meta.setBasePotionType(base);
        }
        if (meta != null) {
            meta.setDisplayName(definition.displayName());
            List<String> lore = new ArrayList<>();
            for (EffectSpec effect : definition.effects()) {
                String name = plugin.getMessages().effectName(effect.type().getKey().getKey());
                lore.add(ChatUtil.color("&7- " + name + " " + toRoman(effect.amplifier() + 1)));
            }
            lore.add(plugin.getMessages().get("shop.duration-line", "seconds", definition.durationSeconds()));
            meta.setLore(lore);
            if (definition.color() != null) {
                meta.setColor(definition.color());
            }
            for (EffectSpec effect : definition.effects()) {
                meta.addCustomEffect(new PotionEffect(effect.type(), definition.durationSeconds() * 20, effect.amplifier()), true);
            }
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(level);
        };
    }

    public BuyResult buy(Player player, String id, int quantity) {
        PotionDefinition definition = catalog.get(id);
        if (definition == null) {
            return BuyResult.UNKNOWN;
        }
        var economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            return BuyResult.NO_ECONOMY;
        }
        double totalPrice = definition.price() * quantity;
        if (!economy.has(player, totalPrice)) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }
        economy.withdrawPlayer(player, totalPrice);
        give(player, definition, quantity);
        return BuyResult.SUCCESS;
    }

    public void give(Player player, PotionDefinition definition, int quantity) {
        // ポーションはバニラでもスタック不可 (最大1個) なので、quantity分は自動的に複数スロットへ
        // 分配される。入りきらない分だけ足元にドロップする (EcoItemCommand と同じパターン)。
        for (int i = 0; i < quantity; i++) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(createItem(definition));
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }
}
