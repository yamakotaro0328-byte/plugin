package com.yamakotaro.ecorail.items;

import com.yamakotaro.ecorail.Messages;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public class TicketItemFactory {

    private final NamespacedKey fromKey;
    private final NamespacedKey toKey;
    private final Messages messages;

    public TicketItemFactory(Plugin plugin, Messages messages) {
        this.fromKey = new NamespacedKey(plugin, "ticket-from");
        this.toKey = new NamespacedKey(plugin, "ticket-to");
        this.messages = messages;
    }

    public ItemStack create(String fromStationId, String toStationId) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("ticket.item-name", Map.of("from", fromStationId, "to", toStationId)));
        meta.getPersistentDataContainer().set(fromKey, PersistentDataType.STRING, fromStationId);
        meta.getPersistentDataContainer().set(toKey, PersistentDataType.STRING, toStationId);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether this item is a ticket matching this exact route - a ticket for a different route doesn't work here. */
    public boolean matches(ItemStack item, String fromStationId, String toStationId) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        String from = data.get(fromKey, PersistentDataType.STRING);
        String to = data.get(toKey, PersistentDataType.STRING);
        return fromStationId.equals(from) && toStationId.equals(to);
    }
}
