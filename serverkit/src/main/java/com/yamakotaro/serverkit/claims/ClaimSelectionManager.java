package com.yamakotaro.serverkit.claims;

import com.yamakotaro.serverkit.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * クレーム範囲を選ぶための「杖」(スティック)と、プレイヤーごとの2点選択(pos1/pos2)を管理する。
 * 杖はPersistentDataContainerのフラグで識別するので、リネームしても判定できる。
 */
public class ClaimSelectionManager {

    private final Messages messages;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public ClaimSelectionManager(Plugin plugin, Messages messages) {
        this.messages = messages;
        this.wandKey = new NamespacedKey(plugin, "claim_wand");
    }

    public ItemStack createWand() {
        ItemStack stack = new ItemStack(Material.STICK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get("claims.wand-name", Map.of()));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isWand(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public void setPos1(UUID uuid, Location location) {
        pos1.put(uuid, location);
    }

    public void setPos2(UUID uuid, Location location) {
        pos2.put(uuid, location);
    }

    public Location getPos1(UUID uuid) {
        return pos1.get(uuid);
    }

    public Location getPos2(UUID uuid) {
        return pos2.get(uuid);
    }

    public void clear(UUID uuid) {
        pos1.remove(uuid);
        pos2.remove(uuid);
    }

    public boolean hasBothPoints(UUID uuid) {
        return pos1.containsKey(uuid) && pos2.containsKey(uuid);
    }

    public Entry<Location, Location> getBothPoints(UUID uuid) {
        Location a = pos1.get(uuid);
        Location b = pos2.get(uuid);
        if (a == null || b == null) {
            return null;
        }
        return Map.entry(a, b);
    }
}
