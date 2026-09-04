package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;

/**
 * Read-only top-45 leaderboard for one job, opened from three places (the player's own jobs menu,
 * the leaderboard picker, or /jobs admin) - {@link Origin} remembers which one, so the back button
 * (see AdminMenuListener, which owns every LeaderboardMenuHolder click regardless of where it was
 * opened from) returns to the right parent screen. Player heads are looked up by name via
 * Bukkit.getOfflinePlayer - the same pattern used elsewhere in this codebase - since
 * PlayerJobManager.TopEntry only carries names, not UUIDs.
 */
public class LeaderboardMenuHolder implements InventoryHolder {

    /** Where this leaderboard was opened from, so its back button returns to the right screen. */
    public enum Origin { JOBS_MENU, PICKER, ADMIN }

    public static final int BACK_SLOT = 45;
    public static final int CLOSE_SLOT = 49;

    private final Messages messages;
    private final String jobId;
    private final Origin origin;
    private final Inventory inventory;

    public LeaderboardMenuHolder(Messages messages, String jobId, Origin origin) {
        this.messages = messages;
        this.jobId = jobId;
        this.origin = origin;
        this.inventory = Bukkit.createInventory(this, 54, messages.get("admin.leaderboard-title", Map.of("job", messages.jobName(jobId))));
    }

    public Origin getOrigin() {
        return origin;
    }

    public void render(PlayerJobManager playerJobManager) {
        inventory.clear();
        // Capped at exactly BACK_SLOT entries so a full board can never collide with the back
        // button below it.
        List<PlayerJobManager.TopEntry> top = playerJobManager.top(jobId, BACK_SLOT);
        int slot = 0;
        for (PlayerJobManager.TopEntry entry : top) {
            inventory.setItem(slot, headOf(entry, slot + 1));
            slot++;
        }
        inventory.setItem(BACK_SLOT, MenuUtil.backItem(messages));
        inventory.setItem(CLOSE_SLOT, MenuUtil.closeItem(messages));
    }

    private ItemStack headOf(PlayerJobManager.TopEntry entry, int rank) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.name()));
            skullMeta.displayName(messages.get("admin.leaderboard-entry", Map.of(
                    "rank", String.valueOf(rank), "player", entry.name())));
            skullMeta.lore(List.of(messages.get("admin.leaderboard-lore", Map.of(
                    "level", String.valueOf(entry.level()),
                    "prestige", String.valueOf(entry.prestige()),
                    "xp", String.format("%.0f", entry.xp())))));
            stack.setItemMeta(skullMeta);
        }
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
