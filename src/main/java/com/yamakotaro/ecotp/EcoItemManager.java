package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 「物理通貨」アイテム (/ecoitem give で生成)。1個あたりの価値を PersistentDataContainer に
 * 記録しており、ドロップ・トレード・チェスト保管など普通のアイテムと同じように扱える。
 * 右クリックで換金 (スタック全体の合計額を入金し、アイテムは消費) できる (EcoItemListener 参照)。
 * このタグを持たないアイテム (見た目や素材が同じでも) は換金対象にならない。
 */
public class EcoItemManager {

    private final EcoTpPlugin plugin;
    private final NamespacedKey valueKey;

    public EcoItemManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.valueKey = new NamespacedKey(plugin, "eco_item_value");
    }

    private Material material() {
        Material configured = Material.matchMaterial(plugin.getConfig().getString("eco-item.material", "GOLD_NUGGET"));
        return configured != null ? configured : Material.GOLD_NUGGET;
    }

    public ItemStack createItem(double valuePerItem, int quantity) {
        ItemStack stack = new ItemStack(material(), quantity);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatUtil.color(plugin.getMessages().get("ecoitem.item-name",
                    "amount", ChatUtil.formatMoney(valuePerItem))));
            meta.setLore(List.of(ChatUtil.color(plugin.getMessages().get("ecoitem.item-lore"))));
            meta.getPersistentDataContainer().set(valueKey, PersistentDataType.DOUBLE, valuePerItem);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * @return このアイテム1個あたりの価値。/ecoitem give で生成されたものでなければ null。
     */
    public Double getValuePerItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(valueKey, PersistentDataType.DOUBLE);
    }

    /**
     * 手に持っているスタック全体を換金する。
     *
     * @return 換金できた合計額。有効な通貨アイテムを持っていなかった、または経済が
     * 利用できなかった場合は 0。
     */
    public double redeem(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        Double perItem = getValuePerItem(stack);
        if (perItem == null) {
            return 0;
        }
        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            return 0;
        }
        double total = perItem * stack.getAmount();
        economy.depositPlayer(player, total);
        player.getInventory().setItemInMainHand(null);
        return total;
    }
}
