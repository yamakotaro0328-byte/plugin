package com.yamakotaro.ecolobby.gui;

import com.yamakotaro.ecolobby.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sub-menu opened from the lobby-menu's particle-trail icon: one entry per particle configured in
 * config.yml's particle-trails list, plus a fixed "off" entry at slot 0. Read fresh on each open
 * (like LobbyMenuConfig) so /ecolobby reload picks up edits without a restart.
 */
public class ParticleMenuHolder implements InventoryHolder {

    public record Entry(Particle particle, String displayName, Material icon) {
    }

    private final Inventory inventory;
    private final Map<Integer, Particle> slotToParticle = new HashMap<>();

    public ParticleMenuHolder(JavaPlugin plugin, Messages messages) {
        List<Entry> entries = new ArrayList<>();
        for (Map<?, ?> map : plugin.getConfig().getMapList("particle-trails")) {
            Object nameObj = map.get("particle");
            if (nameObj == null) {
                continue;
            }
            Particle particle;
            try {
                particle = Particle.valueOf(String.valueOf(nameObj).toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown particle '" + nameObj + "' in config.yml particle-trails list.");
                continue;
            }
            String displayName = map.get("display-name") != null ? String.valueOf(map.get("display-name")) : String.valueOf(nameObj);
            Material icon = map.get("icon") != null ? Material.matchMaterial(String.valueOf(map.get("icon"))) : null;
            entries.add(new Entry(particle, displayName, icon != null ? icon : Material.FIREWORK_STAR));
        }

        int size = Math.max(9, (int) (Math.ceil((entries.size() + 1) / 9.0) * 9));
        this.inventory = Bukkit.createInventory(this, size, messages.get("menu.particle-title", Map.of()));

        ItemStack off = new ItemStack(Material.BARRIER);
        ItemMeta offMeta = off.getItemMeta();
        offMeta.displayName(messages.get("particle.off", Map.of()));
        off.setItemMeta(offMeta);
        inventory.setItem(0, off);
        slotToParticle.put(0, null);

        int slot = 1;
        for (Entry entry : entries) {
            ItemStack item = new ItemStack(entry.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(messages.color(entry.displayName()));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            slotToParticle.put(slot, entry.particle());
            slot++;
        }
    }

    /** @return the particle for a clicked slot, or null both for the "off" slot and for an unused one -
     * callers must check {@link #isKnownSlot(int)} to tell the two apart. */
    public Particle particleAt(int slot) {
        return slotToParticle.get(slot);
    }

    public boolean isKnownSlot(int slot) {
        return slotToParticle.containsKey(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
