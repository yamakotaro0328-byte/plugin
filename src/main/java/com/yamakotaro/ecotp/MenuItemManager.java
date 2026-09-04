package com.yamakotaro.ecotp;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 右クリックで /menu と同じGUIを開ける専用アイテム。config.yml の features.menu-item が
 * true で、かつそのプレイヤー自身がオフにしていなければ、参加時にまだ持っていない
 * (インベントリのどこにも無い) プレイヤーへ自動的に1個渡す (MenuItemListener 参照)。
 * 見た目や素材が同じでも、PersistentDataContainer のこのタグを持たないアイテムは
 * 対象にならない (EcoItemManager と同じ考え方)。
 *
 * 名前/lore/素材が config.yml や言語設定の変更後に古いまま残ってしまわないよう、
 * 既に配布済みのアイテムは refreshExisting() で現在の内容と比較し、ずれていれば
 * その場で新しい内容に差し替える (プレイヤーは再受け取り不要)。
 */
public class MenuItemManager {

    private final EcoTpPlugin plugin;
    private final NamespacedKey markerKey;
    private final File file;
    private final Set<UUID> disabled = new HashSet<>();

    public MenuItemManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "menu_item");
        this.file = new File(plugin.getDataFolder(), "menu-item-prefs.yml");
        load();
    }

    private Material material() {
        Material configured = Material.matchMaterial(plugin.getConfig().getString("menu-item.material", "CLOCK"));
        return configured != null ? configured : Material.CLOCK;
    }

    public ItemStack createItem() {
        ItemStack stack = new ItemStack(material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getMessages().get("menu-item.name"));
            meta.setLore(plugin.getMessages().getList("menu-item.lore"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isMenuItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    public boolean hasMenuItem(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isMenuItem(stack)) {
                return true;
            }
        }
        return isMenuItem(player.getInventory().getItemInOffHand());
    }

    public boolean isEnabled(UUID uuid) {
        return !disabled.contains(uuid);
    }

    /** プレイヤー自身によるオン/オフ切り替え。オフにした場合は既に持っている分も全て回収する。 */
    public void setEnabled(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        if (enabled) {
            disabled.remove(uuid);
        } else {
            disabled.add(uuid);
        }
        save();
        if (enabled) {
            giveOnJoinIfMissing(player);
        } else {
            removeAll(player);
        }
    }

    private void removeAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (isMenuItem(contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
        if (isMenuItem(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    /** 既に持っているアイテムの素材/名前/loreが最新の設定・言語と食い違っていれば、その場で差し替える。 */
    public void refreshExisting(Player player) {
        if (!hasMenuItem(player)) {
            return;
        }
        Material expectedMaterial = material();
        String expectedName = plugin.getMessages().get("menu-item.name");
        List<String> expectedLore = plugin.getMessages().getList("menu-item.lore");

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack refreshed = refreshed(contents[i], expectedMaterial, expectedName, expectedLore);
            if (refreshed != contents[i]) {
                contents[i] = refreshed;
                changed = true;
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
        ItemStack offhand = inventory.getItemInOffHand();
        ItemStack refreshedOffhand = refreshed(offhand, expectedMaterial, expectedName, expectedLore);
        if (refreshedOffhand != offhand) {
            inventory.setItemInOffHand(refreshedOffhand);
        }
    }

    private ItemStack refreshed(ItemStack stack, Material expectedMaterial, String expectedName, List<String> expectedLore) {
        if (!isMenuItem(stack)) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        boolean upToDate = stack.getType() == expectedMaterial
                && meta != null
                && expectedName.equals(meta.getDisplayName())
                && expectedLore.equals(meta.getLore());
        if (upToDate) {
            return stack;
        }
        ItemStack fresh = createItem();
        fresh.setAmount(stack.getAmount());
        return fresh;
    }

    /** 参加時に呼ぶ: 機能が有効かつ、プレイヤー自身がオフにしておらず、まだ持っていなければ1個渡す。 */
    public void giveOnJoinIfMissing(Player player) {
        if (!plugin.isFeatureEnabled("menu-item")) {
            return;
        }
        refreshExisting(player);
        if (!isEnabled(player.getUniqueId()) || hasMenuItem(player)) {
            return;
        }
        player.getInventory().addItem(createItem());
    }

    private void load() {
        disabled.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlIo.load(file);
        for (String value : data.getStringList("disabled")) {
            try {
                disabled.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Skip a malformed entry rather than failing the whole load.
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("disabled", disabled.stream().map(UUID::toString).collect(Collectors.toCollection(ArrayList::new)));
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save menu-item-prefs.yml", e);
        }
    }
}
