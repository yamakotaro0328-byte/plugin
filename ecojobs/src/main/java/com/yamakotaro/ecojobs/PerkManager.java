package com.yamakotaro.ecojobs;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Resolves and applies job perks (config.yml's jobs.&lt;job&gt;.perks) - the level-gated bonuses
 * beyond raw money/xp: a permanent extra pay-%, a potion effect kept up while the job is joined,
 * a chance to double a mined/chopped/harvested block's drop, ores dropping already smelted, and
 * extra vanilla xp.
 *
 * <p>Unlocking uses an "effective level" (level + prestige * max-level, see
 * {@link #effectiveLevel}) rather than the raw level, so prestiging a job - which resets its
 * level to 1 - never takes a perk away; it only ever adds headroom towards the next one. This is
 * what makes prestige (see PlayerJobManager#prestige) a pure net gain instead of just a reset.
 */
public class PerkManager {

    private final EcoJobsPlugin plugin;
    private final JobManager jobManager;
    private final Map<Material, Material> smeltMap = new EnumMap<>(Material.class);
    private boolean badEffectWarned;

    public PerkManager(EcoJobsPlugin plugin, JobManager jobManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        load();
    }

    public void load() {
        smeltMap.clear();
        ConfigurationSection section = plugin.config().getConfigurationSection("auto-smelt-map");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material ore = Material.matchMaterial(key);
            Material result = Material.matchMaterial(section.getString(key, ""));
            if (ore != null && result != null) {
                smeltMap.put(ore, result);
            }
        }
    }

    /** Level + prestige * max-level - see the class doc for why prestige never loses a perk. */
    public int effectiveLevel(PlayerJobProgress progress) {
        return progress.getLevel() + progress.getPrestige() * jobManager.maxLevel();
    }

    private List<PerkDefinition> unlockedOfType(JobDefinition job, int effectiveLevel, String type) {
        List<PerkDefinition> result = new ArrayList<>();
        for (PerkDefinition perk : job.getPerks()) {
            if (perk.type().equalsIgnoreCase(type) && effectiveLevel >= perk.level()) {
                result.add(perk);
            }
        }
        return result;
    }

    /** @return the extra pay multiplier (e.g. 0.05 for +5%) from every pay-bonus perk unlocked so far. */
    public double payBonusMultiplier(JobDefinition job, int effectiveLevel) {
        double total = 0;
        for (PerkDefinition perk : unlockedOfType(job, effectiveLevel, PerkDefinition.PAY_BONUS)) {
            total += perk.value() / 100.0;
        }
        return total;
    }

    /** @return extra vanilla xp (player.giveExp, not job xp) from every unlocked xp-orb-bonus perk. */
    public int xpOrbBonus(JobDefinition job, int effectiveLevel) {
        int total = 0;
        for (PerkDefinition perk : unlockedOfType(job, effectiveLevel, PerkDefinition.XP_ORB_BONUS)) {
            total += (int) perk.value();
        }
        return total;
    }

    /**
     * Re-applies every unlocked potion perk's effect to the player. Called periodically (see
     * PerkHeartbeatTask) rather than once on join, so leaving the job or logging off needs no
     * special cleanup - the refreshes simply stop and the last application's own duration expires.
     */
    public void applyPotionPerks(Player player, JobDefinition job, int effectiveLevel, int durationTicks) {
        for (PerkDefinition perk : unlockedOfType(job, effectiveLevel, PerkDefinition.POTION)) {
            PotionEffectType type = perk.effect() != null ? PotionEffectType.getByName(perk.effect()) : null;
            if (type == null) {
                if (!badEffectWarned) {
                    badEffectWarned = true;
                    plugin.getLogger().log(Level.WARNING,
                            "Unknown potion effect ''{0}'' in a perk for job ''{1}'' - skipping (further warnings suppressed).",
                            new Object[] {perk.effect(), job.getId()});
                }
                continue;
            }
            int amplifier = Math.max(0, (int) perk.value() - 1);
            player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false));
        }
    }

    public boolean rollDoubleDrop(JobDefinition job, int effectiveLevel) {
        for (PerkDefinition perk : unlockedOfType(job, effectiveLevel, PerkDefinition.DOUBLE_DROP)) {
            if (ThreadLocalRandom.current().nextDouble(100) < perk.value()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAutoSmelt(JobDefinition job, int effectiveLevel) {
        return !unlockedOfType(job, effectiveLevel, PerkDefinition.AUTO_SMELT).isEmpty();
    }

    /** @return the smelted result for an ore block type, or null if it has none configured. */
    public Material smeltedResult(Material ore) {
        return smeltMap.get(ore);
    }

    /** Every perk this job has, ascending by level - used by JobInfoMenuHolder to list them all
     * (unlocked or not) without duplicating the unlock check. */
    public List<PerkDefinition> allPerks(JobDefinition job) {
        List<PerkDefinition> sorted = new ArrayList<>(job.getPerks());
        sorted.sort(Comparator.comparingInt(PerkDefinition::level));
        return sorted;
    }
}
