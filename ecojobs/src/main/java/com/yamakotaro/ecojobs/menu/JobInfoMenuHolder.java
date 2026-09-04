package com.yamakotaro.ecojobs.menu;

import com.yamakotaro.ecojobs.ActionReward;
import com.yamakotaro.ecojobs.JobDefinition;
import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.JobOverrides;
import com.yamakotaro.ecojobs.Messages;
import com.yamakotaro.ecojobs.PerkDefinition;
import com.yamakotaro.ecojobs.PerkManager;
import com.yamakotaro.ecojobs.PlayerJobManager;
import com.yamakotaro.ecojobs.PlayerJobProgress;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A per-job payout screen, reached by shift-right-clicking a job in {@link JobsMenuHolder} - the
 * one thing the GUI never showed before: what a job actually pays. Mirrors /jobs info's data
 * (same {@link JobDefinition#getActionsByType()}, same alphabetical TreeMap order) but as an icon
 * grid, paginated the same way as the jobs menu itself so a job with an unusually long reward
 * table can never be silently truncated. Also lists every {@link PerkDefinition} the job has
 * (unlocked or not) after its reward entries, so a job's detail screen doubles as the answer to
 * "what does leveling this up actually get me" - see PerkManager.
 */
public class JobInfoMenuHolder implements InventoryHolder {

    public static final int HEADER_SLOT = 4;
    public static final int BACK_SLOT = 45;
    public static final int PREV_PAGE_SLOT = 46;
    public static final int NEXT_PAGE_SLOT = 48;
    public static final int CLOSE_SLOT = 49;

    /** Exactly one of reward/perk is set - see {@link #ofReward}/{@link #ofPerk}. */
    private record Entry(String key, Material icon, ActionReward reward, PerkDefinition perk, boolean perkUnlocked, int perkLevelsAway) {
        static Entry ofReward(String key, Material icon, ActionReward reward) {
            return new Entry(key, icon, reward, null, false, 0);
        }

        static Entry ofPerk(PerkDefinition perk, Material icon, boolean unlocked, int levelsAway) {
            return new Entry(perk.type() + ":" + perk.level(), icon, null, perk, unlocked, levelsAway);
        }
    }

    private final Messages messages;
    private final String jobId;
    private final Inventory inventory;
    private int page;
    private double explorerDistancePerMilestone;

    public JobInfoMenuHolder(Messages messages, String jobId) {
        this.messages = messages;
        this.jobId = jobId;
        this.inventory = Bukkit.createInventory(this, 54, messages.get("menu.job-info-title", Map.of("job", messages.jobName(jobId))));
    }

    public String getJobId() {
        return jobId;
    }

    public int getPage() {
        return page;
    }

    public void render(JobManager jobManager, PlayerJobManager playerJobManager, JobOverrides jobOverrides,
                        PerkManager perkManager, Player viewer, int requestedPage) {
        inventory.clear();
        MenuUtil.fillBorder(inventory);
        JobDefinition job = jobManager.get(jobId);
        if (job == null) {
            return;
        }
        inventory.setItem(HEADER_SLOT, headerItem(job, jobManager, playerJobManager, jobOverrides, viewer));

        this.explorerDistancePerMilestone = jobManager.explorerDistancePerMilestone();
        PlayerJobProgress progress = playerJobManager.allProgress(viewer.getUniqueId()).get(jobId);
        int effectiveLevel = progress != null ? perkManager.effectiveLevel(progress) : 0;
        List<Entry> entries = buildEntries(job, jobManager, perkManager, effectiveLevel);
        List<Integer> slots = MenuUtil.interiorSlots();
        int pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) slots.size()));
        this.page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int start = page * slots.size();
        for (int i = 0; i < slots.size() && start + i < entries.size(); i++) {
            inventory.setItem(slots.get(i), entryItem(entries.get(start + i)));
        }

        inventory.setItem(BACK_SLOT, MenuUtil.backItem(messages));
        if (page > 0) {
            inventory.setItem(PREV_PAGE_SLOT, MenuUtil.prevPageItem(messages));
        }
        if (page < pageCount - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, MenuUtil.nextPageItem(messages));
        }
        inventory.setItem(CLOSE_SLOT, MenuUtil.closeItem(messages));
    }

    private List<Entry> buildEntries(JobDefinition job, JobManager jobManager, PerkManager perkManager, int effectiveLevel) {
        List<Entry> entries = new ArrayList<>();
        if ("explorer".equals(jobId)) {
            // Explorer has no action-reward table (see JobManager#load) - it pays purely on
            // distance milestones, so build one synthetic entry describing that instead.
            entries.add(Entry.ofReward("milestone", Material.COMPASS,
                    new ActionReward(jobManager.explorerMoneyPerMilestone(), jobManager.explorerXpPerMilestone(), 0, 0)));
        } else {
            Map<String, Map<String, ActionReward>> actions = new TreeMap<>(job.getActionsByType());
            for (Map.Entry<String, Map<String, ActionReward>> actionType : actions.entrySet()) {
                for (Map.Entry<String, ActionReward> rewardEntry : new TreeMap<>(actionType.getValue()).entrySet()) {
                    entries.add(Entry.ofReward(rewardEntry.getKey(), iconFor(rewardEntry.getKey()), rewardEntry.getValue()));
                }
            }
        }
        for (PerkDefinition perk : perkManager.allPerks(job)) {
            boolean unlocked = effectiveLevel >= perk.level();
            int levelsAway = Math.max(0, perk.level() - effectiveLevel);
            entries.add(Entry.ofPerk(perk, perkIcon(perk.type()), unlocked, levelsAway));
        }
        return entries;
    }

    /** Best-effort icon for a reward key: a block/item name, then an entity's spawn egg, then paper. */
    private Material iconFor(String key) {
        Material direct = Material.matchMaterial(key);
        if (direct != null) {
            return direct;
        }
        Material spawnEgg = Material.matchMaterial(key + "_SPAWN_EGG");
        return spawnEgg != null ? spawnEgg : Material.PAPER;
    }

    private Material perkIcon(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case PerkDefinition.PAY_BONUS -> Material.GOLD_NUGGET;
            case PerkDefinition.POTION -> Material.POTION;
            case PerkDefinition.DOUBLE_DROP -> Material.HOPPER;
            case PerkDefinition.AUTO_SMELT -> Material.FURNACE;
            case PerkDefinition.XP_ORB_BONUS -> Material.EXPERIENCE_BOTTLE;
            default -> Material.NETHER_STAR;
        };
    }

    private ItemStack headerItem(JobDefinition job, JobManager jobManager, PlayerJobManager playerJobManager, JobOverrides jobOverrides, Player viewer) {
        Material material = JobsMenuHolder.ICONS.getOrDefault(jobId, Material.PAPER);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(messages.get("menu.job-title", Map.of("job", messages.jobName(jobId))));
        PlayerJobProgress progress = playerJobManager.allProgress(viewer.getUniqueId()).get(jobId);
        boolean active = playerJobManager.isJoined(viewer.getUniqueId(), jobId);
        boolean maxed = progress != null && progress.getLevel() >= jobManager.maxLevel();
        List<Component> lore = new ArrayList<>();
        if (progress != null) {
            lore.add(messages.get("menu.lore-level", Map.of(
                    "level", String.valueOf(progress.getLevel()),
                    "prestige", String.valueOf(progress.getPrestige()),
                    "xp", String.format("%.0f", progress.getXp()),
                    "next_xp", String.format("%.0f", playerJobManager.xpToNextLevel(progress.getLevel())))));
        } else {
            lore.add(messages.get("menu.lore-not-joined", Map.of()));
        }
        boolean enabled = jobOverrides.isEnabled(jobId);
        if (!enabled) {
            lore.add(messages.get("menu.lore-disabled", Map.of()));
        } else if (progress != null) {
            lore.add(messages.get(active ? "menu.lore-click-leave" : "menu.lore-click-rejoin", Map.of()));
        } else {
            lore.add(messages.get("menu.lore-click-join", Map.of()));
        }
        if (viewer.hasPermission("ecojobs.top")) {
            lore.add(messages.get("menu.lore-click-leaderboard", Map.of()));
        }
        if (active && maxed) {
            lore.add(messages.get("menu.lore-click-prestige", Map.of()));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(active || maxed);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack entryItem(Entry entry) {
        return entry.perk() != null ? perkItem(entry) : rewardItem(entry);
    }

    private ItemStack rewardItem(Entry entry) {
        ItemStack stack = new ItemStack(entry.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(prettify(entry.key())));
            List<Component> lore = new ArrayList<>();
            if ("explorer".equals(jobId)) {
                lore.add(messages.get("menu.job-info-explorer-lore",
                        Map.of("distance", String.valueOf((int) explorerDistancePerMilestone))));
            }
            lore.add(messages.get("menu.job-info-entry-money", Map.of(
                    "money", MenuUtil.formatReward(entry.reward().money(), entry.reward().moneyPerLevel()))));
            lore.add(messages.get("menu.job-info-entry-xp", Map.of(
                    "xp", MenuUtil.formatReward(entry.reward().xp(), entry.reward().xpPerLevel()))));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack perkItem(Entry entry) {
        PerkDefinition perk = entry.perk();
        ItemStack stack = new ItemStack(entry.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get(
                    entry.perkUnlocked() ? "menu.job-info-perk-unlocked-title" : "menu.job-info-perk-locked-title",
                    Map.of("level", String.valueOf(perk.level()))));
            List<Component> lore = new ArrayList<>();
            lore.add(messages.get(perkDescriptionKey(perk.type()), perkDescriptionArgs(perk)));
            if (!entry.perkUnlocked()) {
                lore.add(messages.get("menu.job-info-perk-levels-away", Map.of("levels", String.valueOf(entry.perkLevelsAway()))));
            }
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(entry.perkUnlocked());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String perkDescriptionKey(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case PerkDefinition.PAY_BONUS -> "menu.perk-desc-pay-bonus";
            case PerkDefinition.POTION -> "menu.perk-desc-potion";
            case PerkDefinition.DOUBLE_DROP -> "menu.perk-desc-double-drop";
            case PerkDefinition.AUTO_SMELT -> "menu.perk-desc-auto-smelt";
            case PerkDefinition.XP_ORB_BONUS -> "menu.perk-desc-xp-orb-bonus";
            default -> "menu.perk-desc-unknown";
        };
    }

    private Map<String, String> perkDescriptionArgs(PerkDefinition perk) {
        return switch (perk.type().toLowerCase(Locale.ROOT)) {
            case PerkDefinition.POTION -> Map.of("effect", perk.effect() != null ? perk.effect() : "?");
            case PerkDefinition.AUTO_SMELT -> Map.of();
            default -> Map.of("value", String.format("%.0f", perk.value()));
        };
    }

    /** "COAL_ORE" -> "Coal Ore". Reward keys are raw Material/EntityType names with no translation
     * table of their own (there are 100+ across every job), so this is the same raw-key display
     * /jobs info has always used, just made readable instead of shouting in all caps. */
    private static String prettify(String key) {
        StringBuilder result = new StringBuilder();
        for (String part : key.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
