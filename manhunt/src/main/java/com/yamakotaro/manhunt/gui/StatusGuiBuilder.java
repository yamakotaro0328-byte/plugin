package com.yamakotaro.manhunt.gui;

import com.yamakotaro.manhunt.Messages;
import com.yamakotaro.manhunt.game.ManhuntGame;
import com.yamakotaro.manhunt.game.Role;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the read-only /manhunt status GUI: current phase up top, runners and hunters below. */
public class StatusGuiBuilder {

    private static final int SIZE = 27;
    private static final int STATE_SLOT = 4;
    private static final int RUNNERS_ROW_START = 9;
    private static final int HUNTERS_ROW_START = 18;

    private final ManhuntGame game;
    private final Messages messages;

    public StatusGuiBuilder(ManhuntGame game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    public Inventory build() {
        StatusGuiHolder holder = new StatusGuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, messages.get("gui.title", Map.of()));
        holder.setInventory(inventory);

        inventory.setItem(STATE_SLOT, buildStateItem());

        int runnerSlot = RUNNERS_ROW_START;
        int hunterSlot = HUNTERS_ROW_START;
        for (Map.Entry<UUID, Role> entry : game.getRoles().entrySet()) {
            if (entry.getValue() == Role.RUNNER) {
                if (runnerSlot < HUNTERS_ROW_START) {
                    inventory.setItem(runnerSlot++, buildRunnerHead(entry.getKey()));
                }
            } else if (hunterSlot < SIZE) {
                inventory.setItem(hunterSlot++, buildHunterHead(entry.getKey()));
            }
        }
        return inventory;
    }

    private ItemStack buildStateItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (!game.isRunning()) {
            meta.displayName(messages.get("gui.state-waiting", Map.of()));
        } else if (game.isHeadStartActive()) {
            meta.displayName(messages.get("gui.state-headstart", Map.of()));
            long secondsLeft = game.headStartRemainingMillis() / 1000;
            meta.lore(List.of(messages.get("gui.state-headstart-lore", Map.of("seconds", String.valueOf(secondsLeft)))));
        } else {
            meta.displayName(messages.get("gui.state-running", Map.of()));
            long elapsedSeconds = game.huntElapsedMillis() / 1000;
            meta.lore(List.of(messages.get("gui.state-running-lore",
                    Map.of("minutes", String.valueOf(elapsedSeconds / 60), "seconds", String.valueOf(elapsedSeconds % 60)))));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRunnerHead(UUID playerId) {
        boolean caught = game.isEliminated(playerId);
        return buildHead(playerId, caught ? "gui.runner-caught-name" : "gui.runner-alive-name",
                caught ? "gui.runner-caught-lore" : "gui.runner-alive-lore");
    }

    private ItemStack buildHunterHead(UUID playerId) {
        return buildHead(playerId, "gui.hunter-name", "gui.hunter-lore");
    }

    private ItemStack buildHead(UUID playerId, String nameKey, String loreKey) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(offlinePlayer);
        String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : playerId.toString();
        meta.displayName(messages.get(nameKey, Map.of("player", name)));
        meta.lore(List.of(messages.get(loreKey, Map.of())));
        item.setItemMeta(meta);
        return item;
    }
}
