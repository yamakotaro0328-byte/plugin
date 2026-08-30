package com.yamakotaro.ecotpquickactions;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * アドミンショップの設定(スロットごとのアイテム・価格)の読み書きと、購入/売却の処理。
 * 在庫は持たない(サーバー側が無限に売買する、いわゆる"admin shop")。
 * スロットに置かれたアイテムはMaterialだけでなく完全なItemStack(ItemMeta込み)として
 * 保存・比較するため、NovaやItemsAdderなど見た目や中身をItemMetaで変えるプラグインの
 * アイテムでも、素材(例: シュルカーボックス)に化けずに正しく売買できる。
 */
public class AdminShopManager {

    private final EcoTpQuickActionsPlugin plugin;
    private final EconomyHolder economyHolder;
    private final Messages messages;
    private final Map<Integer, ShopItem> items = new TreeMap<>();
    private final File file;

    public AdminShopManager(EcoTpQuickActionsPlugin plugin, EconomyHolder economyHolder, Messages messages) {
        this.plugin = plugin;
        this.economyHolder = economyHolder;
        this.messages = messages;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("admin-shop.enabled", true);
    }

    public int rows() {
        int rows = plugin.getConfig().getInt("admin-shop.rows", 3);
        return Math.max(1, Math.min(6, rows));
    }

    public int size() {
        return rows() * 9;
    }

    public Map<Integer, ShopItem> items() {
        return items;
    }

    public ShopItem get(int slot) {
        return items.get(slot);
    }

    public void setItem(int slot, ItemStack item) {
        items.put(slot, new ShopItem(item, null, null));
        save();
    }

    public void remove(int slot) {
        items.remove(slot);
        save();
    }

    /**
     * @return スロットにアイテムが設定されていれば true。
     */
    public boolean setBuyPrice(int slot, Double price) {
        ShopItem item = items.get(slot);
        if (item == null) {
            return false;
        }
        item.setBuyPrice(price);
        save();
        return true;
    }

    /**
     * @return スロットにアイテムが設定されていれば true。
     */
    public boolean setSellPrice(int slot, Double price) {
        ShopItem item = items.get(slot);
        if (item == null) {
            return false;
        }
        item.setSellPrice(price);
        save();
        return true;
    }

    public void buy(Player player, int slot, boolean fullStack) {
        if (!isEnabled()) {
            player.sendMessage(messages.get("adminshop.feature-disabled", Map.of()));
            return;
        }
        ShopItem item = items.get(slot);
        if (item == null) {
            player.sendMessage(messages.get("adminshop.empty-slot", Map.of()));
            return;
        }
        if (item.getBuyPrice() == null) {
            player.sendMessage(messages.get("adminshop.buy-disabled", Map.of()));
            return;
        }
        Economy economy = economyHolder.get();
        if (economy == null) {
            player.sendMessage(messages.get("adminshop.no-economy", Map.of()));
            return;
        }
        ItemStack template = item.getTemplate();
        int amount = fullStack ? template.getMaxStackSize() : 1;
        double cost = item.getBuyPrice() * amount;
        if (!economy.has(player, cost)) {
            player.sendMessage(messages.get("adminshop.insufficient-funds", Map.of("price", formatMoney(cost))));
            return;
        }
        ItemStack toGive = template.clone();
        toGive.setAmount(amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
        if (!leftover.isEmpty()) {
            player.sendMessage(messages.get("adminshop.inventory-full", Map.of()));
            return;
        }
        economy.withdrawPlayer(player, cost);
        player.sendMessage(messages.get("adminshop.bought", Map.of(
                "amount", String.valueOf(amount),
                "material", template.getType().name(),
                "price", formatMoney(cost))));
    }

    public void sell(Player player, int slot, boolean all) {
        if (!isEnabled()) {
            player.sendMessage(messages.get("adminshop.feature-disabled", Map.of()));
            return;
        }
        ShopItem item = items.get(slot);
        if (item == null) {
            player.sendMessage(messages.get("adminshop.empty-slot", Map.of()));
            return;
        }
        if (item.getSellPrice() == null) {
            player.sendMessage(messages.get("adminshop.sell-disabled", Map.of()));
            return;
        }
        Economy economy = economyHolder.get();
        if (economy == null) {
            player.sendMessage(messages.get("adminshop.no-economy", Map.of()));
            return;
        }
        ItemStack template = item.getTemplate();
        PlayerInventory inventory = player.getInventory();
        int have = countMatching(inventory, template);
        int amount = all ? have : Math.min(1, have);
        if (amount <= 0) {
            player.sendMessage(messages.get("adminshop.insufficient-items", Map.of("material", template.getType().name())));
            return;
        }
        removeMatching(inventory, template, amount);
        double total = item.getSellPrice() * amount;
        economy.depositPlayer(player, total);
        player.sendMessage(messages.get("adminshop.sold", Map.of(
                "amount", String.valueOf(amount),
                "material", template.getType().name(),
                "price", formatMoney(total))));
    }

    private static String formatMoney(double amount) {
        return String.format("%.2f", amount);
    }

    /**
     * isSimilar()はMaterialに加えてItemMeta(表示名・カスタムモデルデータ・PersistentDataContainer
     * など)も比較するため、NovaやItemsAdderのような見た目や識別をItemMetaで行うプラグインの
     * アイテムも、数量違いだけを無視して正しく同一アイテムとして数えられる。
     */
    private static int countMatching(PlayerInventory inventory, ItemStack template) {
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(template)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static void removeMatching(PlayerInventory inventory, ItemStack template, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !stack.isSimilar(template)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            remaining -= take;
        }
        inventory.setStorageContents(contents);
    }

    private void load() {
        items.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        var itemsSection = data.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }
        for (String key : itemsSection.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            ItemStack template = itemsSection.getItemStack(key + ".item");
            if (template == null) {
                // shop.yml written before Nova/ItemMeta support only stored the material name;
                // fall back to a plain vanilla item so upgrading doesn't wipe existing shops.
                String materialName = itemsSection.getString(key + ".material");
                Material material = materialName != null ? Material.matchMaterial(materialName) : null;
                if (material == null) {
                    continue;
                }
                template = new ItemStack(material);
            }
            Double buyPrice = itemsSection.contains(key + ".buy-price") ? itemsSection.getDouble(key + ".buy-price") : null;
            Double sellPrice = itemsSection.contains(key + ".sell-price") ? itemsSection.getDouble(key + ".sell-price") : null;
            items.put(slot, new ShopItem(template, buyPrice, sellPrice));
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<Integer, ShopItem> entry : items.entrySet()) {
            ShopItem item = entry.getValue();
            String base = "items." + entry.getKey();
            data.set(base + ".item", item.getTemplate());
            if (item.getBuyPrice() != null) {
                data.set(base + ".buy-price", item.getBuyPrice());
            }
            if (item.getSellPrice() != null) {
                data.set(base + ".sell-price", item.getSellPrice());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save shop.yml: " + e.getMessage());
        }
    }
}
