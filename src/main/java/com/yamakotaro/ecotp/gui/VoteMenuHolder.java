package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.Messages;
import com.yamakotaro.ecotp.VoteGoalManager;
import com.yamakotaro.ecotp.VoteRewardManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 投票メニュー: 自分の投票数・投票サイト (クリックでリンク)・サーバー目標の進み具合・
 * 累計ボーナス・投票ランキング上位をまとめて表示する。
 */
public class VoteMenuHolder implements EcoTpMenu {

    private static final int SIZE = 54;
    private static final int SLOT_STATS = 4;
    private static final int SLOT_GOAL = 13;
    private static final int[] SITE_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int SLOT_MILESTONES = 31;
    private static final int[] TOP_SLOTS = {38, 39, 40, 41, 42};
    public static final int SLOT_BACK = 45;
    public static final int SLOT_CLOSE = 53;

    private final Inventory inventory;
    private final List<VoteRewardManager.VoteSite> sites;

    public VoteMenuHolder(EcoTpPlugin plugin, Player viewer) {
        Messages messages = plugin.getMessages();
        VoteRewardManager votes = plugin.getVoteRewardManager();
        this.sites = votes.getSites();
        this.inventory = Bukkit.createInventory(this, SIZE, messages.get("menu.vote-title"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.item(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }

        Map<Integer, ItemStack> tiles = new HashMap<>();
        int myVotes = votes.getVoteCount(viewer.getName());
        tiles.put(SLOT_STATS, MenuItems.item(Material.NAME_TAG, messages.get("menu.vote-stats"),
                messages.getList("menu.lore.vote-stats", "votes", String.valueOf(myVotes),
                        "amount", ChatUtil.formatMoney(votes.rewardAmount()))));

        VoteGoalManager goal = plugin.getVoteGoalManager();
        if (goal.isEnabled()) {
            int target = goal.getTarget();
            tiles.put(SLOT_GOAL, MenuItems.item(Material.BEACON, messages.get("menu.vote-goal"),
                    messages.getList("menu.lore.vote-goal",
                            "bar", MenuFx.bar((double) goal.getProgress() / target, 10),
                            "progress", String.valueOf(goal.getProgress()), "target", String.valueOf(target),
                            "remaining", String.valueOf(target - goal.getProgress()),
                            "reward", ChatUtil.formatMoney(plugin.getConfig().getDouble("vote-goal.reward", 1000.0))),
                    true));
        }

        if (sites.isEmpty()) {
            tiles.put(SITE_SLOTS[3], MenuItems.item(Material.BARRIER, messages.get("menu.vote-no-sites"), null));
        } else {
            for (int i = 0; i < sites.size() && i < SITE_SLOTS.length; i++) {
                VoteRewardManager.VoteSite site = sites.get(i);
                tiles.put(SITE_SLOTS[i], MenuItems.item(Material.FILLED_MAP,
                        messages.get("menu.vote-site", "name", site.name()),
                        messages.getList("menu.lore.vote-site", "url", site.url()), true));
            }
        }

        tiles.put(SLOT_MILESTONES, milestonesItem(plugin, myVotes));

        List<VoteRewardManager.VoteEntry> top = votes.getTopVoters(TOP_SLOTS.length);
        for (int i = 0; i < top.size(); i++) {
            tiles.put(TOP_SLOTS[i], topHead(messages, i + 1, top.get(i)));
        }

        tiles.put(SLOT_BACK, MenuItems.item(Material.ARROW, messages.get("menu.back"), null));
        tiles.put(SLOT_CLOSE, MenuItems.item(Material.BARRIER, messages.get("menu.close"), null));
        MenuItems.playOpenSound(viewer);
        MenuFx.open(plugin, viewer, this, inventory, tiles, true);
    }

    private static ItemStack milestonesItem(EcoTpPlugin plugin, int myVotes) {
        Messages messages = plugin.getMessages();
        List<String> lore = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("vote-reward.milestones");
        TreeSet<Integer> counts = new TreeSet<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    counts.add(Integer.parseInt(key));
                } catch (NumberFormatException ignored) {
                    // 数字でないキーは無視
                }
            }
        }
        for (int count : counts) {
            lore.add(messages.get(myVotes >= count ? "menu.lore.milestone-done" : "menu.lore.milestone-todo",
                    "count", String.valueOf(count),
                    "bonus", ChatUtil.formatMoney(section.getDouble(String.valueOf(count)))));
        }
        if (lore.isEmpty()) {
            lore.add(messages.get("menu.lore.milestone-none"));
        }
        return MenuItems.item(Material.EXPERIENCE_BOTTLE, messages.get("menu.vote-milestones"), lore);
    }

    private static ItemStack topHead(Messages messages, int rank, VoteRewardManager.VoteEntry entry) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            // getOfflinePlayer(名前) は未知の名前だと Mojang に問い合わせてサーバーが止まるので、
            // キャッシュにある人だけ頭の見た目を付ける。
            OfflinePlayer owner = Bukkit.getOfflinePlayerIfCached(entry.name());
            if (owner != null) {
                skull.setOwningPlayer(owner);
            }
            String color = switch (rank) {
                case 1 -> "&6&l";
                case 2 -> "&7&l";
                case 3 -> "&c&l";
                default -> "&f";
            };
            skull.setDisplayName(ChatUtil.color(color + "#" + rank + " " + entry.name()));
            skull.setLore(messages.getList("menu.lore.vote-top", "votes", String.valueOf(entry.votes())));
            stack.setItemMeta(skull);
        }
        return stack;
    }

    /** @return このスロットの投票サイト。サイトでなければ null。 */
    public VoteRewardManager.VoteSite siteAt(int slot) {
        for (int i = 0; i < SITE_SLOTS.length && i < sites.size(); i++) {
            if (SITE_SLOTS[i] == slot) {
                return sites.get(i);
            }
        }
        return null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
