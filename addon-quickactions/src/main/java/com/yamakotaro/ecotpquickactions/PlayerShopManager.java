package com.yamakotaro.ecotpquickactions;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * プレイヤー間ショップ: 自分のアイテムをGUIで出品(出品と同時に在庫として預かる)し、
 * 他のプレイヤーがその場で購入できる。アドミンショップと違い在庫は有限で、
 * 支払いは購入者から出品者へ直接渡る。取引履歴は各プレイヤー自身の分だけ記録する。
 * 出品するアイテムはMaterialだけでなく完全なItemStack(ItemMeta込み)として保存・比較
 * するため、NovaやItemsAdderなど見た目や中身をItemMetaで変えるプラグインのアイテムでも、
 * 素材(例: シュルカーボックス)に化けずに正しく売買できる。
 */
public class PlayerShopManager {

    public record Listing(int id, UUID sellerId, String sellerName, ItemStack item, int amount, double pricePerUnit) {
        Listing withAmount(int newAmount) {
            return new Listing(id, sellerId, sellerName, item, newAmount, pricePerUnit);
        }
    }

    public record HistoryEntry(long timestamp, boolean bought, ItemStack item, int amount, double total, String counterpartyName) {
    }

    public enum ListResult { SUCCESS, INVALID_AMOUNT, INVALID_PRICE, INSUFFICIENT_ITEMS }

    public enum BuyResult { SUCCESS, NOT_FOUND, CANNOT_BUY_OWN, INSUFFICIENT_STOCK, NO_ECONOMY, INSUFFICIENT_FUNDS, INVENTORY_FULL }

    public enum RemoveResult { SUCCESS, NOT_FOUND, NOT_OWNER }

    private final EcoTpQuickActionsPlugin plugin;
    private final EconomyHolder economyHolder;
    private final Messages messages;
    private final Map<Integer, Listing> listings = new TreeMap<>();
    private final Map<UUID, List<HistoryEntry>> history = new LinkedHashMap<>();
    private int nextId = 1;
    private final File file;

    public PlayerShopManager(EcoTpQuickActionsPlugin plugin, EconomyHolder economyHolder, Messages messages) {
        this.plugin = plugin;
        this.economyHolder = economyHolder;
        this.messages = messages;
        this.file = new File(plugin.getDataFolder(), "playershop.yml");
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("player-shop.enabled", true);
    }

    public int rows() {
        int rows = plugin.getConfig().getInt("player-shop.browse-rows", 6);
        return Math.max(1, Math.min(6, rows));
    }

    public int size() {
        return rows() * 9;
    }

    private int historyLimit() {
        return Math.max(1, plugin.getConfig().getInt("player-shop.history-limit", 20));
    }

    public List<Listing> activeListingsForBrowse() {
        List<Listing> sorted = new ArrayList<>(listings.values());
        sorted.sort(Comparator.comparingInt(Listing::id).reversed());
        if (sorted.size() > size()) {
            return sorted.subList(0, size());
        }
        return sorted;
    }

    public List<Listing> listingsForSeller(UUID seller) {
        List<Listing> result = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.sellerId().equals(seller)) {
                result.add(listing);
            }
        }
        result.sort(Comparator.comparingInt(Listing::id).reversed());
        return result;
    }

    public Listing get(int id) {
        return listings.get(id);
    }

    public List<HistoryEntry> historyFor(UUID uuid) {
        return history.getOrDefault(uuid, List.of());
    }

    public ListResult createListing(Player seller, ItemStack itemTemplate, int amount, double price) {
        if (amount <= 0) {
            return ListResult.INVALID_AMOUNT;
        }
        if (price <= 0) {
            return ListResult.INVALID_PRICE;
        }
        PlayerInventory inventory = seller.getInventory();
        if (countMatching(inventory, itemTemplate) < amount) {
            return ListResult.INSUFFICIENT_ITEMS;
        }
        removeMatching(inventory, itemTemplate, amount);
        int id = nextId++;
        ItemStack template = itemTemplate.clone();
        template.setAmount(1);
        listings.put(id, new Listing(id, seller.getUniqueId(), seller.getName(), template, amount, price));
        save();
        return ListResult.SUCCESS;
    }

    public RemoveResult removeListing(Player seller, int listingId) {
        Listing listing = listings.get(listingId);
        if (listing == null) {
            return RemoveResult.NOT_FOUND;
        }
        if (!listing.sellerId().equals(seller.getUniqueId())) {
            return RemoveResult.NOT_OWNER;
        }
        listings.remove(listingId);
        ItemStack give = listing.item().clone();
        give.setAmount(listing.amount());
        Map<Integer, ItemStack> leftover = seller.getInventory().addItem(give);
        for (ItemStack stack : leftover.values()) {
            seller.getWorld().dropItemNaturally(seller.getLocation(), stack);
        }
        save();
        return RemoveResult.SUCCESS;
    }

    public BuyResult buy(Player buyer, int listingId, int requestedAmount) {
        Listing listing = listings.get(listingId);
        if (listing == null) {
            return BuyResult.NOT_FOUND;
        }
        if (listing.sellerId().equals(buyer.getUniqueId())) {
            return BuyResult.CANNOT_BUY_OWN;
        }
        int amount = Math.min(requestedAmount, listing.amount());
        if (amount <= 0) {
            return BuyResult.INSUFFICIENT_STOCK;
        }
        Economy economy = economyHolder.get();
        if (economy == null) {
            return BuyResult.NO_ECONOMY;
        }
        double total = listing.pricePerUnit() * amount;
        if (!economy.has(buyer, total)) {
            return BuyResult.INSUFFICIENT_FUNDS;
        }
        ItemStack give = listing.item().clone();
        give.setAmount(amount);
        Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(give);
        if (!leftover.isEmpty()) {
            int notAdded = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            int added = amount - notAdded;
            if (added > 0) {
                removeMatching(buyer.getInventory(), listing.item(), added);
            }
            return BuyResult.INVENTORY_FULL;
        }

        economy.withdrawPlayer(buyer, total);
        OfflinePlayer sellerOffline = Bukkit.getOfflinePlayer(listing.sellerId());
        economy.depositPlayer(sellerOffline, total);

        int remaining = listing.amount() - amount;
        if (remaining > 0) {
            listings.put(listingId, listing.withAmount(remaining));
        } else {
            listings.remove(listingId);
        }
        addHistory(buyer.getUniqueId(), new HistoryEntry(System.currentTimeMillis(), true, listing.item(), amount, total, listing.sellerName()));
        addHistory(listing.sellerId(), new HistoryEntry(System.currentTimeMillis(), false, listing.item(), amount, total, buyer.getName()));
        save();

        Player onlineSeller = Bukkit.getPlayer(listing.sellerId());
        if (onlineSeller != null) {
            onlineSeller.sendMessage(messages.get("playershop.item-sold-notify", Map.of(
                    "amount", String.valueOf(amount),
                    "material", listing.item().getType().name(),
                    "price", formatMoney(total),
                    "player", buyer.getName())));
        }
        return BuyResult.SUCCESS;
    }

    private void addHistory(UUID uuid, HistoryEntry entry) {
        List<HistoryEntry> list = history.computeIfAbsent(uuid, k -> new ArrayList<>());
        list.add(0, entry);
        while (list.size() > historyLimit()) {
            list.remove(list.size() - 1);
        }
    }

    static String formatMoney(double amount) {
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
        listings.clear();
        history.clear();
        nextId = 1;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        nextId = data.getInt("next-id", 1);

        ConfigurationSection listingsSection = data.getConfigurationSection("listings");
        if (listingsSection != null) {
            for (String key : listingsSection.getKeys(false)) {
                try {
                    int id = Integer.parseInt(key);
                    UUID sellerId = UUID.fromString(listingsSection.getString(key + ".seller"));
                    String sellerName = listingsSection.getString(key + ".seller-name", "?");
                    ItemStack item = readItem(listingsSection, key);
                    int amount = listingsSection.getInt(key + ".amount");
                    double price = listingsSection.getDouble(key + ".price");
                    if (item != null && amount > 0) {
                        listings.put(id, new Listing(id, sellerId, sellerName, item, amount, price));
                    }
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    // Skip a malformed entry rather than failing the whole load.
                }
            }
        }

        ConfigurationSection historySection = data.getConfigurationSection("history");
        if (historySection != null) {
            for (String key : historySection.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                List<HistoryEntry> entries = new ArrayList<>();
                for (Map<?, ?> raw : historySection.getMapList(key)) {
                    try {
                        long timestamp = ((Number) raw.get("timestamp")).longValue();
                        boolean bought = Boolean.TRUE.equals(raw.get("bought"));
                        ItemStack item = readHistoryItem(raw);
                        int amount = ((Number) raw.get("amount")).intValue();
                        double total = ((Number) raw.get("total")).doubleValue();
                        String counterparty = String.valueOf(raw.get("counterparty"));
                        if (item != null) {
                            entries.add(new HistoryEntry(timestamp, bought, item, amount, total, counterparty));
                        }
                    } catch (RuntimeException ignored) {
                        // Skip a malformed entry rather than failing the whole load.
                    }
                }
                history.put(uuid, entries);
            }
        }
    }

    // playershop.yml written before Nova/ItemMeta support only stored the material name; fall
    // back to a plain vanilla item for those so upgrading doesn't wipe existing listings/history.
    private static ItemStack readItem(ConfigurationSection section, String key) {
        ItemStack item = section.getItemStack(key + ".item");
        if (item != null) {
            return item;
        }
        Material material = Material.matchMaterial(section.getString(key + ".material", ""));
        return material != null ? new ItemStack(material) : null;
    }

    private static ItemStack readHistoryItem(Map<?, ?> raw) {
        Object stored = raw.get("item");
        if (stored instanceof ItemStack item) {
            return item;
        }
        Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
        return material != null ? new ItemStack(material) : null;
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("next-id", nextId);
        for (Map.Entry<Integer, Listing> entry : listings.entrySet()) {
            Listing listing = entry.getValue();
            String base = "listings." + entry.getKey();
            data.set(base + ".seller", listing.sellerId().toString());
            data.set(base + ".seller-name", listing.sellerName());
            data.set(base + ".item", listing.item());
            data.set(base + ".amount", listing.amount());
            data.set(base + ".price", listing.pricePerUnit());
        }
        for (Map.Entry<UUID, List<HistoryEntry>> entry : history.entrySet()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (HistoryEntry historyEntry : entry.getValue()) {
                Map<String, Object> row = new HashMap<>();
                row.put("timestamp", historyEntry.timestamp());
                row.put("bought", historyEntry.bought());
                row.put("item", historyEntry.item());
                row.put("amount", historyEntry.amount());
                row.put("total", historyEntry.total());
                row.put("counterparty", historyEntry.counterpartyName());
                rows.add(row);
            }
            data.set("history." + entry.getKey(), rows);
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save playershop.yml: " + e.getMessage());
        }
    }
}
