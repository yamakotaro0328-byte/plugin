package com.yamakotaro.ecotp;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 右クリックで /menu と同じGUIを開ける専用アイテム。config.yml の features.menu-item が
 * true なら、参加時にまだ持っていない (インベントリのどこにも無い) プレイヤーへ自動的に
 * 1個渡す (MenuItemListener 参照)。見た目や素材が同じでも、PersistentDataContainer の
 * このタグを持たないアイテムは対象にならない (EcoItemManager と同じ考え方)。
 */
public class MenuItemManager {

    private final EcoTpPlugin plugin;
    private final NamespacedKey markerKey;

    public MenuItemManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "menu_item");
    }

    private Material material() {
        Material configured = Material.matchMaterial(plugin.getConfig().getString("menu-item.material", "COMPASS"));
        return configured != null ? configured : Material.COMPASS;
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

    private boolean hasMenuItem(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isMenuItem(stack)) {
                return true;
            }
        }
        return false;
    }

    /** 参加時に呼ぶ: 機能が有効かつ、まだ持っていなければ1個渡す。 */
    public void giveOnJoinIfMissing(Player player) {
        if (!plugin.isFeatureEnabled("menu-item") || hasMenuItem(player)) {
            return;
        }
        player.getInventory().addItem(createItem());
    }
}
