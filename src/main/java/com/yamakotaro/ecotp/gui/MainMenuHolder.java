package com.yamakotaro.ecotp.gui;

import com.yamakotaro.ecotp.ChatUtil;
import com.yamakotaro.ecotp.EcoTpPlugin;
import com.yamakotaro.ecotp.Messages;
import com.yamakotaro.ecotp.TpaManager;
import com.yamakotaro.ecotp.VoteGoalManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.UUID;

/**
 * /menu で開くメインメニュー。テレポート・お金・報酬・その他の4つの段に分けて並べ、
 * 開くとアイコンが左から順に現れ、枠は虹色に流れる (gui.animations で止められる)。
 * config.yml の features.* で無効化されている項目は表示されない。
 */
public class MainMenuHolder implements EcoTpMenu {

    private static final int SIZE = 54;

    public static final int SLOT_PROFILE = 4;
    public static final int SLOT_INCOMING_REQUEST = 8;
    // テレポートの段
    private static final int SLOT_CAT_TELEPORT = 10;
    public static final int SLOT_HOME = 11;
    public static final int SLOT_SETHOME = 12;
    public static final int SLOT_SPAWN = 13;
    public static final int SLOT_WARP = 14;
    public static final int SLOT_TPA = 15;
    public static final int SLOT_TPHERE = 16;
    // お金の段
    private static final int SLOT_CAT_MONEY = 19;
    public static final int SLOT_BALANCE = 20;
    public static final int SLOT_PAY = 21;
    public static final int SLOT_DONATE = 22;
    public static final int SLOT_BALTOP = 23;
    // 報酬の段
    private static final int SLOT_CAT_REWARDS = 28;
    public static final int SLOT_DAILY = 29;
    public static final int SLOT_VOTE = 30;
    public static final int SLOT_VOTE_GOAL = 31;
    public static final int SLOT_BOSS = 32;
    // その他の段
    private static final int SLOT_CAT_OTHER = 37;
    public static final int SLOT_SETTINGS = 38;
    public static final int SLOT_SERVER_INFO = 39;
    public static final int SLOT_HELP = 40;

    public static final int SLOT_CLOSE = 49;

    private final Inventory inventory;
    private final Map<Integer, ItemStack> tiles = new HashMap<>();

    public MainMenuHolder(EcoTpPlugin plugin, Player viewer) {
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.getMessages().get("menu.title"));
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, MenuItems.item(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }

        Messages messages = plugin.getMessages();
        double minFee = plugin.getConfig().getDouble("costs.distance-min-fee", 100.0);
        String minFeeFormatted = ChatUtil.formatMoney(minFee);
        UUID uuid = viewer.getUniqueId();

        tiles.put(SLOT_PROFILE, profileHead(plugin, viewer));
        plugin.getTpaManager().getIncomingRequest(uuid)
                .ifPresent(info -> tiles.put(SLOT_INCOMING_REQUEST, incomingRequestItem(plugin, info)));

        // テレポート
        tiles.put(SLOT_CAT_TELEPORT, category(plugin, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "teleport"));
        putIfEnabled(plugin, SLOT_HOME, "home", Material.RED_BED, homeLore(plugin, viewer, minFeeFormatted));
        putIfEnabled(plugin, SLOT_SETHOME, "sethome", Material.COMPASS, sethomeLore(plugin, viewer));
        putIfEnabled(plugin, SLOT_SPAWN, "spawn", Material.GRASS_BLOCK,
                messages.getList("menu.lore.spawn", "fee", minFeeFormatted));
        putIfEnabled(plugin, SLOT_WARP, "warp", Material.LODESTONE,
                messages.getList("menu.lore.warp", "count", String.valueOf(plugin.getWarpManager().getWarpNames().size())));
        putIfEnabled(plugin, SLOT_TPA, "tpa", Material.ENDER_PEARL,
                messages.getList("menu.lore.tpa", "fee", minFeeFormatted));
        putIfEnabled(plugin, SLOT_TPHERE, "tphere", Material.ENDER_EYE,
                messages.getList("menu.lore.tphere", "fee", minFeeFormatted));

        // お金
        tiles.put(SLOT_CAT_MONEY, category(plugin, Material.YELLOW_STAINED_GLASS_PANE, "money"));
        tiles.put(SLOT_BALANCE, MenuItems.item(Material.GOLD_INGOT, messages.get("menu.balance"), balanceLore(plugin, viewer)));
        putIfEnabled(plugin, SLOT_PAY, "pay", Material.EMERALD, messages.getList("menu.lore.pay"));
        putIfEnabled(plugin, SLOT_DONATE, "donate", Material.NETHER_STAR, messages.getList("menu.lore.donate"));
        putIfEnabled(plugin, SLOT_BALTOP, "baltop", Material.DIAMOND, messages.getList("menu.lore.baltop"));

        // 報酬
        tiles.put(SLOT_CAT_REWARDS, category(plugin, Material.LIME_STAINED_GLASS_PANE, "rewards"));
        boolean dailyClaimable = plugin.getDailyRewardManager().isClaimable(uuid);
        if (plugin.isFeatureEnabled("daily")) {
            tiles.put(SLOT_DAILY, MenuItems.item(dailyClaimable ? Material.CHEST_MINECART : Material.MINECART,
                    messages.get(dailyClaimable ? "menu.daily" : "menu.daily-claimed"),
                    dailyLore(plugin, viewer), dailyClaimable));
        }
        if (plugin.getVoteRewardManager().isEnabled()) {
            tiles.put(SLOT_VOTE, MenuItems.item(Material.PAPER, messages.get("menu.vote"),
                    messages.getList("menu.lore.vote-menu",
                            "amount", ChatUtil.formatMoney(plugin.getVoteRewardManager().rewardAmount()),
                            "votes", String.valueOf(plugin.getVoteRewardManager().getVoteCount(viewer.getName())))));
        }
        VoteGoalManager goal = plugin.getVoteGoalManager();
        if (goal.isEnabled()) {
            tiles.put(SLOT_VOTE_GOAL, voteGoalItem(plugin, goal));
        }
        if (plugin.getConfig().getBoolean("boss-rewards.enabled", true)) {
            tiles.put(SLOT_BOSS, bossItem(plugin));
        }

        // その他
        tiles.put(SLOT_CAT_OTHER, category(plugin, Material.MAGENTA_STAINED_GLASS_PANE, "other"));
        tiles.put(SLOT_SETTINGS, MenuItems.item(Material.COMPARATOR, messages.get("menu.settings"), messages.getList("menu.lore.settings")));
        tiles.put(SLOT_SERVER_INFO, serverInfoItem(plugin, viewer));
        tiles.put(SLOT_HELP, MenuItems.item(Material.KNOWLEDGE_BOOK, messages.get("menu.help"), messages.getList("menu.lore.help")));

        tiles.put(SLOT_CLOSE, MenuItems.item(Material.BARRIER, messages.get("menu.close"), null));

        MenuItems.playOpenSound(viewer);
        MenuFx.open(plugin, viewer, this, inventory, tiles, true);
    }

    private void putIfEnabled(EcoTpPlugin plugin, int slot, String featureKey, Material material, List<String> lore) {
        if (!plugin.isFeatureEnabled(featureKey)) {
            return; // 無効な機能はメニューに出さない
        }
        tiles.put(slot, MenuItems.item(material, plugin.getMessages().get("menu." + featureKey), lore));
    }

    private static ItemStack category(EcoTpPlugin plugin, Material pane, String key) {
        return MenuItems.item(pane, plugin.getMessages().get("menu.category." + key),
                plugin.getMessages().getList("menu.lore.category-" + key));
    }

    private static ItemStack voteGoalItem(EcoTpPlugin plugin, VoteGoalManager goal) {
        int target = goal.getTarget();
        int progress = goal.getProgress();
        return MenuItems.item(Material.BEACON, plugin.getMessages().get("menu.vote-goal"),
                plugin.getMessages().getList("menu.lore.vote-goal",
                        "bar", MenuFx.bar((double) progress / target, 10),
                        "progress", String.valueOf(progress), "target", String.valueOf(target),
                        "remaining", String.valueOf(target - progress),
                        "reward", ChatUtil.formatMoney(plugin.getConfig().getDouble("vote-goal.reward", 1000.0))),
                true);
    }

    private static ItemStack bossItem(EcoTpPlugin plugin) {
        List<String> lore = new ArrayList<>(plugin.getMessages().getList("menu.lore.boss-header"));
        ConfigurationSection mobs = plugin.getConfig().getConfigurationSection("boss-rewards.mobs");
        if (mobs != null) {
            for (String type : mobs.getKeys(false)) {
                String name = plugin.getMessages().get("boss-rewards.names." + type.toLowerCase());
                if (name.contains("boss-rewards.names")) {
                    name = type;
                }
                lore.add(plugin.getMessages().get("menu.lore.boss-entry",
                        "mob", name, "reward", ChatUtil.formatMoney(mobs.getDouble(type))));
            }
        }
        return MenuItems.item(Material.WITHER_SKELETON_SKULL, plugin.getMessages().get("menu.boss"), lore);
    }

    private static ItemStack serverInfoItem(EcoTpPlugin plugin, Player viewer) {
        double tps = Math.min(20.0, Bukkit.getServer().getTPS()[0]);
        long time = viewer.getWorld().getTime();
        String timeKey = time < 12300 || time > 23850 ? "menu.lore.time-day" : "menu.lore.time-night";
        return MenuItems.item(Material.CLOCK, plugin.getMessages().get("menu.server-info"),
                plugin.getMessages().getList("menu.lore.server-info",
                        "online", String.valueOf(Bukkit.getOnlinePlayers().size()),
                        "max", String.valueOf(Bukkit.getMaxPlayers()),
                        "ping", String.valueOf(viewer.getPing()),
                        "tps", String.format("%.1f", tps),
                        "time", plugin.getMessages().get(timeKey)));
    }

    private static ItemStack profileHead(EcoTpPlugin plugin, Player viewer) {
        UUID uuid = viewer.getUniqueId();
        Economy economy = plugin.getEconomyHolder().get();
        String balance = economy == null ? "?" : ChatUtil.formatMoney(economy.getBalance(viewer));
        int used = plugin.getHomeManager().getHomeNames(uuid).size();
        int max = plugin.getConfig().getInt("homes.max-per-player", 3);
        int streak = plugin.getDailyRewardManager().getEffectiveStreak(uuid);
        int votes = plugin.getVoteRewardManager().getVoteCount(viewer.getName());
        List<String> lore = plugin.getMessages().getList("menu.lore.profile",
                "balance", balance, "used", String.valueOf(used), "max", String.valueOf(max),
                "streak", String.valueOf(streak), "votes", String.valueOf(votes));

        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(viewer);
            skullMeta.setDisplayName(plugin.getMessages().get("menu.profile-title", "player", viewer.getName()));
            skullMeta.setLore(lore);
            stack.setItemMeta(skullMeta);
        }
        return stack;
    }

    private static ItemStack incomingRequestItem(EcoTpPlugin plugin, TpaManager.IncomingRequestInfo info) {
        String typeLabel = plugin.getMessages().get(IncomingRequestHolder.typeLabelKey(info.type()));
        String name = plugin.getMessages().get("menu.incoming-request", "player", info.requesterName());
        List<String> lore = plugin.getMessages().getList("menu.lore.incoming-request", "type", typeLabel);
        return MenuItems.item(Material.BELL, name, lore, true);
    }

    private static List<String> homeLore(EcoTpPlugin plugin, Player viewer, String feeFormatted) {
        int count = plugin.getHomeManager().getHomeNames(viewer.getUniqueId()).size();
        if (count > 1) {
            return plugin.getMessages().getList("menu.lore.home-multi", "fee", feeFormatted, "count", String.valueOf(count));
        }
        return plugin.getMessages().getList("menu.lore.home", "fee", feeFormatted);
    }

    private static List<String> sethomeLore(EcoTpPlugin plugin, Player viewer) {
        UUID uuid = viewer.getUniqueId();
        double nextCost = plugin.getHomeManager().getNextSetHomeCost(uuid);
        int used = plugin.getHomeManager().getHomeNames(uuid).size();
        int max = plugin.getConfig().getInt("homes.max-per-player", 3);
        return plugin.getMessages().getList("menu.lore.sethome",
                "cost", ChatUtil.formatMoney(nextCost), "used", String.valueOf(used), "max", String.valueOf(max));
    }

    private static List<String> balanceLore(EcoTpPlugin plugin, Player viewer) {
        Economy economy = plugin.getEconomyHolder().get();
        String balance = economy == null ? "?" : ChatUtil.formatMoney(economy.getBalance(viewer));
        return plugin.getMessages().getList("menu.lore.balance", "balance", balance);
    }

    private static List<String> dailyLore(EcoTpPlugin plugin, Player viewer) {
        UUID uuid = viewer.getUniqueId();
        int streak = plugin.getDailyRewardManager().getEffectiveStreak(uuid);
        String status = plugin.getDailyRewardManager().isClaimable(uuid)
                ? plugin.getMessages().get("menu.lore.daily-status-ready")
                : plugin.getMessages().get("menu.lore.daily-status-waiting",
                        "time", plugin.getDailyRewardManager().formatRemainingCooldown(uuid));
        return plugin.getMessages().getList("menu.lore.daily-calendar", "streak", String.valueOf(streak), "status", status);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
