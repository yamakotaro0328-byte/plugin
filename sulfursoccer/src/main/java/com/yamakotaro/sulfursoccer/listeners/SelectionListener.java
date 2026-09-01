package com.yamakotaro.sulfursoccer.listeners;

import com.yamakotaro.sulfursoccer.Messages;
import com.yamakotaro.sulfursoccer.selection.SelectionManager;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/** Left-click with the wand sets corner 1, right-click sets corner 2 (see /soccer wand). */
public class SelectionListener implements Listener {

    private final NamespacedKey wandKey;
    private final SelectionManager selectionManager;
    private final Messages messages;

    public SelectionListener(Plugin plugin, SelectionManager selectionManager, Messages messages) {
        this.wandKey = new NamespacedKey(plugin, "wand");
        this.selectionManager = selectionManager;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem())) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            selectionManager.setCorner1(event.getPlayer().getUniqueId(), clicked.getLocation());
            event.getPlayer().sendMessage(messages.get("wand.corner1-set", Map.of()));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            selectionManager.setCorner2(event.getPlayer().getUniqueId(), clicked.getLocation());
            event.getPlayer().sendMessage(messages.get("wand.corner2-set", Map.of()));
        }
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BOOLEAN);
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }
}
