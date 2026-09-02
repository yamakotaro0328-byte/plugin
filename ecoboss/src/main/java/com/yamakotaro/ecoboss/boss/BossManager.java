package com.yamakotaro.ecoboss.boss;

import com.yamakotaro.ecoboss.Messages;
import com.yamakotaro.ecoboss.location.BossLocationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Owns every boss's static definition (loaded from config.yml) and the live state of whichever
 * instances are currently active. Where each boss spawns is a separate concern (BossLocationManager).
 */
public class BossManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final BossLocationManager locationManager;
    private final Map<String, BossDefinition> definitionsById = new LinkedHashMap<>();
    private final Map<String, ActiveBoss> activeByBossId = new HashMap<>();
    private final Map<String, Long> cooldownUntilMillisByBossId = new HashMap<>();
    private final Map<String, Long> nextAbilityAtMillisByBossId = new HashMap<>();
    private final Random random = new Random();

    public BossManager(JavaPlugin plugin, Messages messages, BossLocationManager locationManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.locationManager = locationManager;
        load();
    }

    public void load() {
        definitionsById.clear();
        ConfigurationSection bossesSection = plugin.getConfig().getConfigurationSection("bosses");
        if (bossesSection == null) {
            return;
        }
        for (String id : bossesSection.getKeys(false)) {
            ConfigurationSection section = bossesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                definitionsById.put(id.toLowerCase(Locale.ROOT), parseBoss(id, section));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Failed to load boss '" + id + "': " + e.getMessage());
            }
        }
    }

    private BossDefinition parseBoss(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        EntityType entityType = EntityType.valueOf(section.getString("entity-type", "ZOMBIE").toUpperCase(Locale.ROOT));
        BossType type = BossType.valueOf(section.getString("type", "WORLD").toUpperCase(Locale.ROOT));
        int healthBoostAmplifier = section.getInt("health-boost-amplifier", 5);
        int strengthAmplifier = section.getInt("strength-amplifier", 1);
        int cooldownMinutes = section.getInt("cooldown-minutes", 30);
        int worldIntervalMinutes = section.getInt("world-interval-minutes", 180);
        DayOfWeek eventDayOfWeek = section.isString("event-day-of-week")
                ? DayOfWeek.valueOf(section.getString("event-day-of-week").toUpperCase(Locale.ROOT)) : null;
        int eventHour = section.getInt("event-hour", 20);
        double scale = section.getDouble("scale", 1.0);
        int abilityIntervalSeconds = Math.max(1, section.getInt("ability-interval-seconds", 8));

        List<Ability> abilities = new ArrayList<>();
        for (String rawAbility : section.getStringList("abilities")) {
            try {
                abilities.add(Ability.valueOf(rawAbility.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Boss '" + id + "': unknown ability '" + rawAbility + "', skipping.");
            }
        }
        EntityType summonWaveType = EntityType.valueOf(section.getString("summon-wave-type", "ZOMBIE").toUpperCase(Locale.ROOT));

        List<Phase> phases = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("phases")) {
            int healthPercent = map.get("health-percent") instanceof Number number ? number.intValue() : 50;
            String message = map.get("message") != null ? String.valueOf(map.get("message")) : "";
            boolean enrage = Boolean.TRUE.equals(map.get("enrage"));
            EntityType summonType = map.get("summon-type") != null
                    ? EntityType.valueOf(String.valueOf(map.get("summon-type")).toUpperCase(Locale.ROOT)) : null;
            int summonCount = map.get("summon-count") instanceof Number number ? number.intValue() : 0;
            phases.add(new Phase(healthPercent, message, enrage, summonType, summonCount));
        }
        // Sorted so index 0 is the first threshold crossed as the boss's health drops from 100%.
        phases.sort((a, b) -> Integer.compare(b.healthPercent(), a.healthPercent()));

        List<Reward> loot = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("loot")) {
            Material material = Material.matchMaterial(String.valueOf(map.get("material")));
            if (material == null) {
                plugin.getLogger().warning("Boss '" + id + "': unknown material '" + map.get("material") + "', skipping reward.");
                continue;
            }
            int amount = map.get("amount") instanceof Number number ? number.intValue() : 1;
            String name = map.get("name") != null ? String.valueOf(map.get("name")) : material.name();
            List<String> lore = map.get("lore") instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
            int weight = map.get("weight") instanceof Number number ? Math.max(1, number.intValue()) : 1;
            boolean guaranteed = Boolean.TRUE.equals(map.get("guaranteed"));
            boolean broadcast = Boolean.TRUE.equals(map.get("broadcast"));
            loot.add(new Reward(material, amount, name, lore, weight, guaranteed, broadcast));
        }

        return new BossDefinition(id, displayName, entityType, type, healthBoostAmplifier, strengthAmplifier,
                cooldownMinutes, worldIntervalMinutes, eventDayOfWeek, eventHour, scale, abilities,
                abilityIntervalSeconds, summonWaveType, phases, loot);
    }

    public Optional<BossDefinition> find(String id) {
        return Optional.ofNullable(definitionsById.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<BossDefinition> all() {
        return definitionsById.values();
    }

    public BossLocationManager locations() {
        return locationManager;
    }

    public boolean isActive(String bossId) {
        return activeByBossId.containsKey(bossId);
    }

    public long cooldownRemainingMinutes(String bossId) {
        Long until = cooldownUntilMillisByBossId.get(bossId);
        if (until == null) {
            return 0;
        }
        long remainingMillis = until - System.currentTimeMillis();
        return remainingMillis <= 0 ? 0 : (remainingMillis / 60_000L) + 1;
    }

    public Optional<ActiveBoss> findActive(UUID entityId) {
        return activeByBossId.values().stream().filter(active -> active.entity().getUniqueId().equals(entityId)).findFirst();
    }

    /** @return null on success, or an error message key. */
    public String spawn(BossDefinition definition, Location location) {
        if (isActive(definition.id())) {
            return "boss.already-active";
        }
        if (cooldownRemainingMinutes(definition.id()) > 0) {
            return "boss.on-cooldown";
        }
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, definition.entityType());
        String legacyName = legacyColor(definition.displayName());
        entity.setCustomName(legacyName);
        entity.setCustomNameVisible(true);
        if (definition.healthBoostAmplifier() > 0) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, Integer.MAX_VALUE, definition.healthBoostAmplifier(), false, false));
        }
        if (definition.strengthAmplifier() > 0) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, definition.strengthAmplifier(), false, false));
        }
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        // Wolves/polar bears etc. default to neutral - force hostility so they actually fight like a boss.
        if (entity instanceof Wolf wolf) {
            wolf.setAngry(true);
        }
        if (definition.scale() != 1.0) {
            AttributeInstance scaleAttribute = entity.getAttribute(Attribute.SCALE);
            if (scaleAttribute != null) {
                scaleAttribute.setBaseValue(definition.scale());
            }
        }

        BarColor barColor = parseBarColor(plugin.getConfig().getString("bossbar-color", "RED"));
        BarStyle barStyle = parseBarStyle(plugin.getConfig().getString("bossbar-style", "SEGMENTED_10"));
        BossBar bossBar = Bukkit.createBossBar(legacyName, barColor, barStyle);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        ActiveBoss active = new ActiveBoss(definition, entity, bossBar, entity.getHealth());
        activeByBossId.put(definition.id(), active);
        return null;
    }

    /** @return null on success, or an error message key. */
    public String stop(String bossId) {
        ActiveBoss active = activeByBossId.remove(bossId);
        if (active == null) {
            return "boss.not-active";
        }
        nextAbilityAtMillisByBossId.remove(bossId);
        active.bossBar().removeAll();
        active.entity().remove();
        return null;
    }

    /** Adds any online player not yet viewing an active boss's health bar - covers late joiners. */
    public void syncBossBars() {
        for (ActiveBoss active : activeByBossId.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                active.bossBar().addPlayer(player);
            }
        }
    }

    /** Spawns a small rotating particle ring around every active boss - a persistent "this is special" visual. */
    public void tickAuras() {
        long now = System.currentTimeMillis();
        for (ActiveBoss active : activeByBossId.values()) {
            LivingEntity entity = active.entity();
            Location center = entity.getLocation().add(0, 1, 0);
            double radius = 1.2 * Math.max(1.0, active.definition().scale());
            for (int i = 0; i < 8; i++) {
                double angle = (now / 200.0) + i * (Math.PI * 2 / 8);
                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
                center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, x, center.getY(), z, 0, 0, 0, 0, 0);
            }
        }
    }

    /** For every active boss with abilities configured, fires one at random once its cooldown is up. */
    public void tickAbilities() {
        long now = System.currentTimeMillis();
        for (ActiveBoss active : activeByBossId.values()) {
            BossDefinition definition = active.definition();
            if (definition.abilities().isEmpty()) {
                continue;
            }
            long nextAt = nextAbilityAtMillisByBossId.getOrDefault(definition.id(), 0L);
            if (now < nextAt) {
                continue;
            }
            Player target = nearestPlayer(active.entity());
            if (target != null) {
                useAbility(active, definition.abilities().get(random.nextInt(definition.abilities().size())), target);
            }
            nextAbilityAtMillisByBossId.put(definition.id(), now + definition.abilityIntervalSeconds() * 1000L);
        }
    }

    private Player nearestPlayer(LivingEntity entity) {
        Player nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Player player : entity.getWorld().getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(entity.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = player;
            }
        }
        return nearest;
    }

    private void useAbility(ActiveBoss active, Ability ability, Player target) {
        switch (ability) {
            case FIREBALL_BARRAGE -> fireballBarrage(active, target);
            case GROUND_SLAM -> groundSlam(active);
            case LIGHTNING_STRIKE -> lightningStrike(active, target);
            case SUMMON_WAVE -> summonWave(active);
            case CHARGE_DASH -> chargeDash(active, target);
        }
    }

    private void fireballBarrage(ActiveBoss active, Player target) {
        LivingEntity entity = active.entity();
        Location eye = entity.getEyeLocation();
        Vector baseDirection = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        for (int i = -1; i <= 1; i++) {
            Vector direction = baseDirection.clone().add(new Vector(0, i * 0.15, 0)).normalize();
            if (entity.getWorld().spawnEntity(eye, EntityType.SMALL_FIREBALL) instanceof Fireball fireball) {
                fireball.setShooter(entity);
                fireball.setDirection(direction);
            }
        }
    }

    private void groundSlam(ActiveBoss active) {
        LivingEntity entity = active.entity();
        Location center = entity.getLocation();
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
        double radius = 5.0 * Math.max(1.0, active.definition().scale());
        for (Player player : center.getWorld().getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(center);
            if (distanceSquared > radius * radius) {
                continue;
            }
            Vector knockback = player.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.4);
            knockback.setY(0.6);
            player.setVelocity(knockback);
            player.damage(6.0, entity);
        }
    }

    private void lightningStrike(ActiveBoss active, Player target) {
        target.getWorld().strikeLightningEffect(target.getLocation());
        target.damage(5.0, active.entity());
    }

    private void summonWave(ActiveBoss active) {
        LivingEntity entity = active.entity();
        for (int i = 0; i < 3; i++) {
            entity.getWorld().spawnEntity(entity.getLocation(), active.definition().summonWaveType());
        }
    }

    private void chargeDash(ActiveBoss active, Player target) {
        LivingEntity entity = active.entity();
        Vector direction = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        Vector velocity = direction.multiply(2.2);
        velocity.setY(0.3);
        entity.setVelocity(velocity);
        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation(), 20, 0.3, 0.1, 0.3, 0.02);
    }

    /** Call after any damage to the boss: advances/triggers phases and refreshes the boss bar. */
    public void onDamaged(ActiveBoss active) {
        double percent = active.healthPercent() * 100;
        List<Phase> phases = active.definition().phases();
        while (active.phaseIndex() < phases.size() && percent <= phases.get(active.phaseIndex()).healthPercent()) {
            triggerPhase(active, phases.get(active.phaseIndex()));
            active.advancePhase();
        }
        active.bossBar().setProgress(active.healthPercent());
    }

    private void triggerPhase(ActiveBoss active, Phase phase) {
        LivingEntity entity = active.entity();
        if (phase.enrage()) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, active.definition().strengthAmplifier() + 2, false, false));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
        }
        if (phase.summonType() != null && phase.summonCount() > 0) {
            for (int i = 0; i < phase.summonCount(); i++) {
                entity.getWorld().spawnEntity(entity.getLocation(), phase.summonType());
            }
        }
        if (!phase.message().isEmpty()) {
            Component component = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(phase.message().replace("{boss}", active.definition().displayName()));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
        }
    }

    /** Call when the boss's entity dies: ends the encounter, starts its cooldown, and hands out loot. */
    public void onDeath(ActiveBoss active) {
        activeByBossId.remove(active.definition().id());
        nextAbilityAtMillisByBossId.remove(active.definition().id());
        active.bossBar().removeAll();
        cooldownUntilMillisByBossId.put(active.definition().id(),
                System.currentTimeMillis() + active.definition().cooldownMinutes() * 60_000L);

        UUID topPlayerId = topDamager(active);
        String topName = topPlayerId != null ? playerName(topPlayerId) : "?";
        Component defeated = messages.get("boss.defeated", Map.of("boss", active.definition().displayName(), "player", topName));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(defeated);
        }

        distributeLoot(active, topPlayerId);
    }

    private UUID topDamager(ActiveBoss active) {
        return active.damageByPlayer().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String playerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        String offlineName = Bukkit.getOfflinePlayer(playerId).getName();
        return offlineName != null ? offlineName : "?";
    }

    private void distributeLoot(ActiveBoss active, UUID topPlayerId) {
        List<Reward> loot = active.definition().loot();
        if (loot.isEmpty()) {
            return;
        }
        for (UUID playerId : active.damageByPlayer().keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            giveReward(player, active.definition(), pickWeighted(loot));
            if (playerId.equals(topPlayerId)) {
                for (Reward reward : loot) {
                    if (reward.guaranteed()) {
                        giveReward(player, active.definition(), reward);
                    }
                }
            }
        }
    }

    private Reward pickWeighted(List<Reward> rewards) {
        int totalWeight = rewards.stream().mapToInt(Reward::weight).sum();
        int roll = random.nextInt(Math.max(totalWeight, 1));
        int cursor = 0;
        for (Reward reward : rewards) {
            cursor += reward.weight();
            if (roll < cursor) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    private void giveReward(Player player, BossDefinition definition, Reward reward) {
        for (var extra : player.getInventory().addItem(reward.toItemStack()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        player.sendMessage(messages.get("boss.loot-received", Map.of("boss", definition.displayName(), "reward", reward.name())));
        if (reward.broadcast()) {
            Component broadcastMessage = messages.get("boss.loot-broadcast",
                    Map.of("player", player.getName(), "boss", definition.displayName(), "reward", reward.name()));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(broadcastMessage);
            }
        }
    }

    private static String legacyColor(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static BarColor parseBarColor(String name) {
        try {
            return BarColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarColor.RED;
        }
    }

    private static BarStyle parseBarStyle(String name) {
        try {
            return BarStyle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarStyle.SOLID;
        }
    }
}
