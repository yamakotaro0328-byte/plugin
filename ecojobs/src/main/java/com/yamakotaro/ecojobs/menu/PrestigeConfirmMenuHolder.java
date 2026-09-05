package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * A confirmation step before actually prestiging a job (see PlayerJobManager#prestige) - resetting
 * to level 1 is permanent, so it's never triggered by a single accidental click. Only ever opened
 * from a job's detail screen (see JobInfoMenuHolder) - prestige isn't offered directly from the
 * main jobs menu - so confirming or cancelling always returns there. JobsMenuListener owns every
 * click on this holder.
 */
public class PrestigeConfirmMenuHolder implements InventoryHolder {

    public static final int CONFIRM_SLOT = 11;
    public static final int CANCEL_SLOT = 15;

    private final Messages messages;
    private final String jobId;
    private final Inventory inventory;

    public PrestigeConfirmMenuHolder(Messages messages, String jobId) {
        this.messages = messages;
        this.jobId = jobId;
        this.inventory = Bukkit.createInventory(this, 27, messages.get("menu.prestige-confirm-title", Map.of("job", messages.jobName(jobId))));
    }

    public String getJobId() {
        return jobId;
    }

    public void render() {
        inventory.clear();
        inventory.setItem(CONFIRM_SLOT, item(Material.LIME_CONCRETE, "menu.prestige-confirm-yes"));
        inventory.setItem(CANCEL_SLOT, item(Material.RED_CONCRETE, "menu.prestige-confirm-no"));
    }

    private ItemStack item(Material material, String titleKey) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get(titleKey, Map.of()));
            meta.lore(List.of(messages.get("menu.prestige-confirm-lore", Map.of())));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
