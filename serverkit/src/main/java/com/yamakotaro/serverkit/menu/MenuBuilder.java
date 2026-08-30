package com.yamakotaro.serverkit.menu;

import com.yamakotaro.serverkit.Messages;
import com.yamakotaro.serverkit.ServerKitPlugin;
import com.yamakotaro.serverkit.claims.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ServerKitの各モジュールをまたいだメニューGUI。モジュールが無効、または権限が無いボタンは
 * 単純にそのプレイヤー向けの表示から省く(エラーメッセージは出さない)。
 */
public final class MenuBuilder {

    private MenuBuilder() {
    }

    public static MenuHolder buildMain(ServerKitPlugin plugin, Messages messages, Player viewer) {
        MenuHolder holder = new MenuHolder(MenuHolder.Mode.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.get("menu.main-title", Map.of()));
        holder.setInventory(inventory);

        if (plugin.getConfig().getBoolean("modules.staff", true)) {
            if (viewer.hasPermission("serverkit.staff.vanish")) {
                inventory.setItem(10, item(Material.POTION, messages.get("menu.vanish-name", Map.of()),
                        List.of(messages.get("menu.vanish-lore", Map.of()))));
            }
            if (viewer.hasPermission("serverkit.staff.staffchat")) {
                inventory.setItem(11, item(Material.WRITABLE_BOOK, messages.get("menu.staffchat-name", Map.of()),
                        List.of(messages.get("menu.staffchat-lore", Map.of()))));
            }
            if (viewer.hasPermission("serverkit.staff.freeze")) {
                inventory.setItem(12, item(Material.PACKED_ICE, messages.get("menu.freeze-menu-name", Map.of()),
                        List.of(messages.get("menu.freeze-menu-lore", Map.of()))));
            }
        }
        if (plugin.getConfig().getBoolean("modules.dragonarena", true) && viewer.hasPermission("serverkit.dragonfight")) {
            inventory.setItem(14, item(Material.DRAGON_HEAD, messages.get("menu.dragonfight-start-name", Map.of()),
                    List.of(messages.get("menu.dragonfight-start-lore", Map.of()))));
            inventory.setItem(15, item(Material.MINECART, messages.get("menu.dragonfight-leave-name", Map.of()),
                    List.of(messages.get("menu.dragonfight-leave-lore", Map.of()))));
        }
        if (plugin.getConfig().getBoolean("modules.claims", true) && viewer.hasPermission("serverkit.claims")) {
            inventory.setItem(16, item(Material.GRASS_BLOCK, messages.get("menu.claims-menu-name", Map.of()),
                    List.of(messages.get("menu.claims-menu-lore", Map.of()))));
        }
        inventory.setItem(22, item(Material.BARRIER, messages.get("menu.close-name", Map.of()), List.of()));
        return holder;
    }

    public static MenuHolder buildFreeze(ServerKitPlugin plugin, Messages messages) {
        MenuHolder holder = new MenuHolder(MenuHolder.Mode.FREEZE);
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("menu.freeze-title", Map.of()));
        holder.setInventory(inventory);

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 53) {
                break;
            }
            boolean frozen = plugin.getFreezeManager().isFrozen(online.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(online);
                skullMeta.displayName(Component.text(online.getName()));
                skullMeta.lore(List.of(messages.get(frozen ? "menu.freeze-target-frozen" : "menu.freeze-target-not-frozen", Map.of())));
                head.setItemMeta(skullMeta);
            }
            inventory.setItem(slot, head);
            holder.putFreezeTarget(slot, online.getUniqueId());
            slot++;
        }
        inventory.setItem(53, item(Material.ARROW, messages.get("menu.back-name", Map.of()), List.of()));
        return holder;
    }

    public static MenuHolder buildClaims(ServerKitPlugin plugin, Messages messages, Player viewer) {
        MenuHolder holder = new MenuHolder(MenuHolder.Mode.CLAIMS);
        Inventory inventory = Bukkit.createInventory(holder, 54, messages.get("menu.claims-title", Map.of()));
        holder.setInventory(inventory);

        var claimManager = plugin.getClaimManager();
        long balance = claimManager.getBalance(viewer.getUniqueId());
        long used = claimManager.usedBlocks(viewer.getUniqueId());
        inventory.setItem(0, item(Material.BOOK, messages.get("menu.claims-info-name", Map.of()),
                List.of(messages.get("menu.claims-info-lore", Map.of(
                        "balance", String.valueOf(balance),
                        "used", String.valueOf(used),
                        "available", String.valueOf(balance - used))))));
        inventory.setItem(1, item(Material.STICK, messages.get("menu.claims-wand-name", Map.of()),
                List.of(messages.get("menu.claims-wand-lore", Map.of()))));

        int slot = 9;
        for (Claim claim : claimManager.claimsForOwner(viewer.getUniqueId())) {
            if (slot >= 53) {
                break;
            }
            inventory.setItem(slot, item(Material.MAP, Component.text(claim.getName()),
                    List.of(messages.get("menu.claims-entry-lore", Map.of(
                            "world", claim.getWorld(), "area", String.valueOf(claim.area()))))));
            holder.putClaimName(slot, claim.getName());
            slot++;
        }
        inventory.setItem(53, item(Material.ARROW, messages.get("menu.back-name", Map.of()), List.of()));
        return holder;
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(new ArrayList<>(lore));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
