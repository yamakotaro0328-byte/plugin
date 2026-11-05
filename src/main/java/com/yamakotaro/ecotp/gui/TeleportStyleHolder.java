package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.Messages;
import com.yamakotaro.ecotp.TeleportStyle;
import com.yamakotaro.ecotp.TeleportStyleManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * テレポート演出の選択/購入。持っている演出はクリックで選択、持っていない演出は
 * 1回目のクリックで「もう一度クリックで購入」になり、2回目で購入する (誤購入防止)。
 */
public class TeleportStyleHolder implements EcoTpMenu {

    private static final int SIZE = 36;
    private static final int[] STYLE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    public static final int SLOT_BACK = 31;

    private final EcoTpPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;
    private TeleportStyle pendingPurchase;

    public TeleportStyleHolder(EcoTpPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.style-title"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.borderPane(slot));
        }
        Map<Integer, ItemStack> tiles = new HashMap<>(render());
        tiles.put(SLOT_BACK, MenuItems.item(Material.ARROW, plugin.getMessages().get("menu.back"), null));
        MenuItems.playOpenSound(viewer);
        MenuFx.open(plugin, viewer, this, inventory, tiles, false);
    }

    private Map<Integer, ItemStack> render() {
        Map<Integer, ItemStack> items = new HashMap<>();
        TeleportStyleManager styles = plugin.getTeleportStyleManager();
        Messages messages = plugin.getMessages();
        UUID uuid = viewer.getUniqueId();
        TeleportStyle selected = styles.getSelected(uuid);
        TeleportStyle[] all = TeleportStyle.values();
        for (int i = 0; i < all.length && i < STYLE_SLOTS.length; i++) {
            TeleportStyle style = all[i];
            String name = messages.get("teleport-styles.names." + style.key());
            boolean owned = styles.owns(uuid, style);
            List<String> lore;
            if (style == selected) {
                lore = messages.getList("menu.lore.style-selected");
            } else if (owned) {
                lore = messages.getList("menu.lore.style-owned");
            } else if (style == pendingPurchase) {
                lore = messages.getList("menu.lore.style-confirm", "price", ChatUtil.formatMoney(styles.price(style)));
            } else {
                lore = messages.getList("menu.lore.style-buy", "price", ChatUtil.formatMoney(styles.price(style)));
            }
            Material icon = owned || style == pendingPurchase ? style.getIcon() : Material.GRAY_DYE;
            items.put(STYLE_SLOTS[i], MenuItems.item(icon, name, lore, style == selected || style == pendingPurchase));
        }
        return items;
    }

    /** @return クリックしたスロットの演出。演出でなければ null。 */
    public TeleportStyle styleAt(int slot) {
        TeleportStyle[] all = TeleportStyle.values();
        for (int i = 0; i < STYLE_SLOTS.length && i < all.length; i++) {
            if (STYLE_SLOTS[i] == slot) {
                return all[i];
            }
        }
        return null;
    }

    /** 演出がクリックされた: 選択・購入確認・購入のどれかをして、表示を更新する。 */
    public void click(TeleportStyle style) {
        TeleportStyleManager styles = plugin.getTeleportStyleManager();
        UUID uuid = viewer.getUniqueId();
        if (styles.owns(uuid, style)) {
            pendingPurchase = null;
            styles.select(uuid, style);
            viewer.sendMessage(plugin.msg("teleport-styles.selected",
                    "style", plugin.getMessages().get("teleport-styles.names." + style.key())));
        } else if (style != pendingPurchase) {
            pendingPurchase = style; // 1回目: 確認待ちにするだけ
        } else {
            pendingPurchase = null;
            switch (styles.buy(viewer, style)) {
                case BOUGHT -> {
                    viewer.sendMessage(plugin.msg("teleport-styles.bought",
                            "style", plugin.getMessages().get("teleport-styles.names." + style.key()),
                            "price", ChatUtil.formatMoney(styles.price(style))));
                    viewer.playSound(viewer.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
                }
                case NOT_ENOUGH_MONEY -> viewer.sendMessage(plugin.msg("general.insufficient-funds",
                        "cost", ChatUtil.formatMoney(styles.price(style))));
                case NO_ECONOMY -> viewer.sendMessage(plugin.msg("general.no-economy"));
                default -> {
                    // 既に持っている / 無効: 何もしない
                }
            }
        }
        render().forEach(inventory::setItem);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
