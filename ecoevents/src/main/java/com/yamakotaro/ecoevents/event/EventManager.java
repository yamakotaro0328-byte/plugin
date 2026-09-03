package com.yamakotaro.ecoevents.event;

import com.yamakotaro.ecoevents.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Loads every event's static definition from config.yml and executes their (much smaller set of
 * shared) mechanics. Many differently-named/flavored events reuse the same underlying mechanic
 * with different radius/duration/magnitude/effects/loot.
 */
public class EventManager {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Map<String, EventDefinition> definitionsById = new LinkedHashMap<>();
    private final Set<String> disabledEventIds = new HashSet<>();
    private final Random random = new Random();

    public EventManager(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        load();
    }

    public void load() {
        definitionsById.clear();
        ConfigurationSection eventsSection = plugin.getConfig().getConfigurationSection("events");
        if (eventsSection == null) {
            return;
        }
        for (String id : eventsSection.getKeys(false)) {
            ConfigurationSection section = eventsSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                definitionsById.put(id.toLowerCase(Locale.ROOT), parseEvent(id, section));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Failed to load event '" + id + "': " + e.getMessage());
            }
        }
    }

    private EventDefinition parseEvent(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        Mechanic mechanic = Mechanic.valueOf(section.getString("mechanic", "METEOR_STRIKE").toUpperCase(Locale.ROOT));
        int weight = Math.max(1, section.getInt("weight", 5));
        double radius = section.getDouble("radius", 8.0);
        int durationSeconds = Math.max(1, section.getInt("duration-seconds", 10));
        double magnitude = section.getDouble("magnitude", 1.0);

        List<PotionEffectSpec> effects = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("effects")) {
            String rawType = String.valueOf(map.get("type"));
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(rawType.toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Event '" + id + "': unknown potion effect '" + rawType + "', skipping.");
                continue;
            }
            int amplifier = map.get("amplifier") instanceof Number number ? number.intValue() : 0;
            effects.add(new PotionEffectSpec(type, amplifier));
        }

        List<LootDrop> loot = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("loot")) {
            Material material = Material.matchMaterial(String.valueOf(map.get("material")));
            if (material == null) {
                plugin.getLogger().warning("Event '" + id + "': unknown material '" + map.get("material") + "', skipping loot entry.");
                continue;
            }
            int amount = map.get("amount") instanceof Number number ? number.intValue() : 1;
            String name = map.get("name") != null ? String.valueOf(map.get("name")) : material.name();
            int lootWeight = map.get("weight") instanceof Number number ? Math.max(1, number.intValue()) : 1;
            loot.add(new LootDrop(material, amount, name, lootWeight));
        }

        return new EventDefinition(id, displayName, mechanic, weight, radius, durationSeconds, magnitude, effects, loot);
    }

    public Optional<EventDefinition> find(String id) {
        return Optional.ofNullable(definitionsById.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<EventDefinition> all() {
        return definitionsById.values();
    }

    public boolean isEnabled(String id) {
        return !disabledEventIds.contains(id.toLowerCase(Locale.ROOT));
    }

    /** @return the new enabled state after toggling. */
    public boolean toggle(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (disabledEventIds.remove(key)) {
            return true;
        }
        disabledEventIds.add(key);
        return false;
    }

    /** Picks one enabled event (weighted) and fires it - a no-op if nobody's online or nothing's enabled. */
    public void fireRandomEvent() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        List<EventDefinition> enabled = definitionsById.values().stream().filter(def -> isEnabled(def.id())).toList();
        if (enabled.isEmpty()) {
            return;
        }
        fireEvent(pickWeighted(enabled));
    }

    public void fireEvent(EventDefinition def) {
        announce(def);
        executeMechanic(def);
    }

    private EventDefinition pickWeighted(List<EventDefinition> events) {
        int totalWeight = events.stream().mapToInt(EventDefinition::weight).sum();
        int roll = random.nextInt(Math.max(totalWeight, 1));
        int cursor = 0;
        for (EventDefinition def : events) {
            cursor += def.weight();
            if (roll < cursor) {
                return def;
            }
        }
        return events.get(events.size() - 1);
    }

    private void announce(EventDefinition def) {
        Component message = messages.get("event.occurring", Map.of("event", def.displayName()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private void executeMechanic(EventDefinition def) {
        switch (def.mechanic()) {
            case METEOR_STRIKE, METEOR_SHOWER -> meteorRain(def);
            case THUNDERSTORM -> thunderstorm(def);
            case LIGHTNING_BARRAGE -> lightningBarrage(def);
            case BLIZZARD -> blizzard(def);
            case SANDSTORM -> globalEffectBurst(def, Particle.CLOUD);
            case EARTHQUAKE -> earthquake(def);
            case PLAGUE, BLESSING -> applyEffectsToAll(def);
            case SKYFALL_LOOT -> skyfallLoot(def);
            case VOID_RIFT -> voidRift(def);
            case WILDFIRE -> wildfire(def);
            case FROST_WAVE -> globalEffectBurst(def, Particle.SNOWFLAKE);
            case AURORA -> aurora();
            case ECLIPSE -> eclipse(def);
        }
    }

    private void meteorRain(EventDefinition def) {
        Player anchor = randomOnlinePlayer();
        if (anchor == null) {
            return;
        }
        Location center = anchor.getLocation();
        int count = Math.max(1, (int) Math.round(def.magnitude() * 5));
        for (int i = 0; i < count; i++) {
            double dx = (random.nextDouble() * 2 - 1) * def.radius();
            double dz = (random.nextDouble() * 2 - 1) * def.radius();
            Location impact = center.clone().add(dx, 0, dz);
            impact.setY(impact.getWorld().getHighestBlockYAt(impact) + 1);
            impact.getWorld().spawnParticle(Particle.FLAME, impact, 40, 0.5, 0.5, 0.5, 0.05);
            impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 1);
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.8f);
            for (Entity nearby : impact.getWorld().getNearbyEntities(impact, 2.5, 2.5, 2.5)) {
                if (nearby instanceof Player player) {
                    player.damage(4.0 * Math.max(1.0, def.magnitude()));
                }
            }
        }
    }

    private void thunderstorm(EventDefinition def) {
        World world = randomOnlineWorld();
        if (world != null) {
            int ticks = def.durationSeconds() * 20;
            world.setStorm(true);
            world.setThundering(true);
            world.setWeatherDuration(ticks);
            world.setThunderDuration(ticks);
            for (Player player : world.getPlayers()) {
                if (random.nextDouble() < 0.4 * Math.max(1.0, def.magnitude())) {
                    world.strikeLightningEffect(player.getLocation());
                }
            }
        }
    }

    private void lightningBarrage(EventDefinition def) {
        int strikesPerPlayer = Math.max(1, (int) Math.round(def.magnitude()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < strikesPerPlayer; i++) {
                Location strikeLoc = player.getLocation().clone().add(
                        (random.nextDouble() * 2 - 1) * def.radius(), 0, (random.nextDouble() * 2 - 1) * def.radius());
                player.getWorld().strikeLightningEffect(strikeLoc);
                if (strikeLoc.distanceSquared(player.getLocation()) < 9) {
                    player.damage(5.0);
                }
            }
        }
    }

    private void blizzard(EventDefinition def) {
        World world = randomOnlineWorld();
        if (world != null) {
            int ticks = def.durationSeconds() * 20;
            world.setStorm(true);
            world.setWeatherDuration(ticks);
        }
        globalEffectBurst(def, Particle.SNOWFLAKE);
    }

    private void earthquake(EventDefinition def) {
        Player anchor = randomOnlinePlayer();
        if (anchor == null) {
            return;
        }
        Location center = anchor.getLocation();
        for (Entity entity : center.getWorld().getNearbyEntities(center, def.radius(), def.radius(), def.radius())) {
            if (entity instanceof Player player) {
                Vector velocity = player.getVelocity();
                velocity.setY(Math.max(velocity.getY(), 0.5 * Math.max(1.0, def.magnitude())));
                player.setVelocity(velocity);
            }
        }
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, (int) def.radius(), def.radius(), 0.2, def.radius(), 0);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
    }

    private void applyEffectsToAll(EventDefinition def) {
        int ticks = def.durationSeconds() * 20;
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (PotionEffectSpec spec : def.effects()) {
                player.addPotionEffect(new PotionEffect(spec.type(), ticks, spec.amplifier()));
            }
        }
    }

    private void globalEffectBurst(EventDefinition def, Particle particle) {
        applyEffectsToAll(def);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 30, 0.8, 0.8, 0.8, 0.02);
        }
    }

    private void skyfallLoot(EventDefinition def) {
        Player anchor = randomOnlinePlayer();
        if (anchor == null || def.loot().isEmpty()) {
            return;
        }
        Location center = anchor.getLocation();
        int count = Math.max(1, (int) Math.round(def.magnitude() * 4));
        for (int i = 0; i < count; i++) {
            double dx = (random.nextDouble() * 2 - 1) * def.radius();
            double dz = (random.nextDouble() * 2 - 1) * def.radius();
            Location dropLoc = center.clone().add(dx, 6, dz);
            LootDrop drop = pickWeightedLoot(def.loot());
            ItemStack item = new ItemStack(drop.material(), Math.max(1, drop.amount()));
            ItemMeta meta = item.getItemMeta();
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(drop.name()));
            item.setItemMeta(meta);
            center.getWorld().dropItem(dropLoc, item);
            center.getWorld().spawnParticle(Particle.FIREWORK, dropLoc, 15, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private LootDrop pickWeightedLoot(List<LootDrop> drops) {
        int totalWeight = drops.stream().mapToInt(LootDrop::weight).sum();
        int roll = random.nextInt(Math.max(totalWeight, 1));
        int cursor = 0;
        for (LootDrop drop : drops) {
            cursor += drop.weight();
            if (roll < cursor) {
                return drop;
            }
        }
        return drops.get(drops.size() - 1);
    }

    private void voidRift(EventDefinition def) {
        Player anchor = randomOnlinePlayer();
        if (anchor == null) {
            return;
        }
        Location center = anchor.getLocation();
        center.getWorld().spawnParticle(Particle.PORTAL, center, 100, def.radius() / 2, 1, def.radius() / 2, 0.5);
        center.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.6f);
        for (Entity entity : center.getWorld().getNearbyEntities(center, def.radius(), def.radius(), def.radius())) {
            if (entity instanceof Player player) {
                Location jitter = player.getLocation().clone().add(
                        (random.nextDouble() * 2 - 1) * 2, 0, (random.nextDouble() * 2 - 1) * 2);
                player.teleport(jitter);
            }
        }
    }

    private void wildfire(EventDefinition def) {
        Player anchor = randomOnlinePlayer();
        if (anchor == null) {
            return;
        }
        Location center = anchor.getLocation();
        center.getWorld().spawnParticle(Particle.FLAME, center, 80, def.radius(), 1, def.radius(), 0.02);
        center.getWorld().playSound(center, Sound.ITEM_FIRECHARGE_USE, 2f, 0.8f);
        for (Entity entity : center.getWorld().getNearbyEntities(center, def.radius(), def.radius(), def.radius())) {
            if (entity instanceof Player player) {
                player.setFireTicks(def.durationSeconds() * 20);
            }
        }
    }

    private void aurora() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location sky = player.getLocation().add(0, 20, 0);
            player.getWorld().spawnParticle(Particle.END_ROD, sky, 60, 6, 2, 6, 0.02);
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, sky, 30, 6, 2, 6, 0.02);
        }
    }

    private void eclipse(EventDefinition def) {
        World world = randomOnlineWorld();
        if (world != null) {
            world.setTime(18000);
        }
        int ticks = def.durationSeconds() * 20;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0));
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.5f);
        }
    }

    private Player randomOnlinePlayer() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
    }

    private World randomOnlineWorld() {
        Player player = randomOnlinePlayer();
        return player != null ? player.getWorld() : null;
    }
}
