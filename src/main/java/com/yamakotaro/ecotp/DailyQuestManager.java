package com.yamakotaro.ecotp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * デイリークエスト: 毎日 (timezone の日付が変わるたび) プレイヤーごとに config の pool から
 * per-day 個のお題をランダムに出す。達成するとその場で報酬、全部達成でボーナス。
 * お題の種類: break (ブロックを掘る)・kill (Mob を倒す)・fish (魚を釣る)・harvest (育った作物を収穫)。
 * 自分で置いたブロックや、スポナー/スポーンエッグで出した Mob は数えない (ずる防止)。
 */
public class DailyQuestManager implements Listener {

    public enum Type { BREAK, KILL, FISH, HARVEST }

    /** config の pool の1つ。targets が空なら「何でも」(kill なら敵対Mobなら何でも)。 */
    public record QuestDef(String id, Type type, Set<String> targets, int amount, double reward, String name, Material icon) {
    }

    /** あるプレイヤーの今日のお題1つの進み具合。 */
    public record QuestProgress(QuestDef def, int progress) {
        public boolean done() {
            return progress >= def.amount();
        }
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int PLACED_MEMORY = 20_000;

    private final EcoTpPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey unnaturalKey;
    /** 最近プレイヤーが置いたブロックの位置 (掘っても数えない)。古い順に忘れる。 */
    private final Set<String> placed = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > PLACED_MEMORY;
        }
    });
    private boolean dirty;

    public DailyQuestManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily-quests.yml");
        this.data = YamlIo.load(file);
        this.unnaturalKey = new NamespacedKey(plugin, "quest_unnatural");
        // 進み具合はこまめに変わるので、変わった時だけ1分ごとにまとめて保存する。
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, 1200L, 1200L);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("daily-quests.enabled", true);
    }

    private String today() {
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("daily-quests.timezone", "Asia/Tokyo"));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        return LocalDate.now(zone).toString();
    }

    private Map<String, QuestDef> pool() {
        Map<String, QuestDef> pool = new LinkedHashMap<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("daily-quests.pool")) {
            try {
                String id = String.valueOf(entry.get("id"));
                Type type = Type.valueOf(String.valueOf(entry.get("type")).toUpperCase(Locale.ROOT));
                Set<String> targets = new LinkedHashSet<>();
                if (entry.get("targets") instanceof List<?> list) {
                    for (Object o : list) {
                        targets.add(String.valueOf(o).toUpperCase(Locale.ROOT));
                    }
                }
                int amount = entry.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 10;
                double reward = entry.get("reward") instanceof Number n ? n.doubleValue() : 300.0;
                Material icon = Material.matchMaterial(String.valueOf(entry.get("icon")));
                String name = entry.get("name") == null ? id : String.valueOf(entry.get("name"));
                pool.put(id, new QuestDef(id, type, targets, amount, reward,
                        ChatUtil.color(name.replace("{amount}", String.valueOf(amount))),
                        icon == null ? Material.PAPER : icon));
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping an invalid daily-quests.pool entry: " + entry);
            }
        }
        return pool;
    }

    /** 今日のお題 (まだ無ければ今ここで選ぶ)。 */
    public List<QuestProgress> getQuests(UUID uuid) {
        String base = "players." + uuid;
        String today = today();
        Map<String, QuestDef> pool = pool();
        if (!today.equals(data.getString(base + ".date"))) {
            List<String> ids = new ArrayList<>(pool.keySet());
            Collections.shuffle(ids);
            int perDay = Math.max(1, plugin.getConfig().getInt("daily-quests.per-day", 3));
            data.set(base, null);
            data.set(base + ".date", today);
            data.set(base + ".quests", ids.subList(0, Math.min(perDay, ids.size())));
            dirty = true;
        }
        List<QuestProgress> result = new ArrayList<>();
        for (String id : data.getStringList(base + ".quests")) {
            QuestDef def = pool.get(id);
            if (def != null) {
                result.add(new QuestProgress(def, data.getInt(base + ".progress." + id)));
            }
        }
        return result;
    }

    public boolean isBonusClaimed(UUID uuid) {
        return data.getBoolean("players." + uuid + ".bonus");
    }

    private void addProgress(Player player, Type type, String target, int count) {
        addProgress(player, type, def -> def.targets().isEmpty() || def.targets().contains(target), count);
    }

    private void addProgress(Player player, Type type, java.util.function.Predicate<QuestDef> matcher, int count) {
        if (!isEnabled() || count <= 0) {
            return;
        }
        UUID uuid = player.getUniqueId();
        List<QuestProgress> quests = getQuests(uuid);
        boolean changed = false;
        for (QuestProgress quest : quests) {
            QuestDef def = quest.def();
            if (def.type() != type || quest.done() || !matcher.test(def)) {
                continue;
            }
            int next = Math.min(def.amount(), quest.progress() + count);
            data.set("players." + uuid + ".progress." + def.id(), next);
            dirty = true;
            changed = true;
            if (next >= def.amount()) {
                complete(player, def);
            } else {
                player.sendActionBar(LEGACY.deserialize(plugin.getMessages().get("quest.progress",
                        "quest", def.name(), "progress", String.valueOf(next), "amount", String.valueOf(def.amount()))));
            }
        }
        if (changed) {
            checkAllDone(player);
        }
    }

    private void complete(Player player, QuestDef def) {
        double reward = plugin.getFeverManager().apply(def.reward());
        Economy economy = plugin.getEconomyHolder().get();
        if (economy != null && reward > 0) {
            economy.depositPlayer(player, reward);
        }
        player.sendMessage(plugin.msg("quest.completed", "quest", def.name(), "reward", ChatUtil.formatMoney(reward)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    private void checkAllDone(Player player) {
        UUID uuid = player.getUniqueId();
        List<QuestProgress> quests = getQuests(uuid);
        if (quests.isEmpty() || isBonusClaimed(uuid) || !quests.stream().allMatch(QuestProgress::done)) {
            return;
        }
        data.set("players." + uuid + ".bonus", true);
        dirty = true;
        double bonus = plugin.getFeverManager().apply(plugin.getConfig().getDouble("daily-quests.all-complete-bonus", 1000.0));
        Economy economy = plugin.getEconomyHolder().get();
        if (economy != null && bonus > 0) {
            economy.depositPlayer(player, bonus);
        }
        Bukkit.broadcastMessage(plugin.getMessages().get("quest.all-done-broadcast",
                "player", player.getName(), "bonus", ChatUtil.formatMoney(bonus)));
        player.sendTitle(plugin.getMessages().get("quest.all-done-title"),
                plugin.getMessages().get("quest.all-done-subtitle", "bonus", ChatUtil.formatMoney(bonus)), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
                60, 0.5, 0.8, 0.5, 0.35);
    }

    // ---- ずる防止: 自分で置いたブロック・人工的に出した Mob ----

    private static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        placed.add(key(event.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            case SPAWNER, SPAWNER_EGG, DISPENSE_EGG, BUILD_IRONGOLEM, BUILD_SNOWMAN, BUILD_WITHER, CUSTOM, COMMAND, BREEDING ->
                    event.getEntity().getPersistentDataContainer().set(unnaturalKey, PersistentDataType.BYTE, (byte) 1);
            default -> {
                // 自然湧き等はそのまま数える
            }
        }
    }

    // ---- 数える ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (placed.remove(key(block.getLocation()))) {
            return; // 自分 (や誰か) が置いたブロック
        }
        if (block.getBlockData() instanceof Ageable crop && isCrop(block.getType())) {
            if (crop.getAge() >= crop.getMaximumAge()) {
                addProgress(event.getPlayer(), Type.HARVEST, block.getType().name(), 1);
            }
            return;
        }
        addProgress(event.getPlayer(), Type.BREAK, block.getType().name(), 1);
    }

    private static boolean isCrop(Material type) {
        return switch (type) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, COCOA, SWEET_BERRY_BUSH -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || entity instanceof Player
                || entity.getPersistentDataContainer().has(unnaturalKey, PersistentDataType.BYTE)) {
            return;
        }
        String type = entity.getType().name();
        boolean hostile = entity instanceof Monster;
        // targets が空の kill クエストは「敵対Mobなら何でも」、それ以外は名前で指定した Mob だけ。
        addProgress(killer, Type.KILL, def -> def.targets().isEmpty() ? hostile : def.targets().contains(type), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof org.bukkit.entity.Item item) {
            addProgress(event.getPlayer(), Type.FISH, item.getItemStack().getType().name(), 1);
        }
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            YamlIo.save(data, file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save daily-quests.yml", e);
        }
    }

    /** プラグイン停止時に保存する。 */
    public void shutdown() {
        saveIfDirty();
    }
}
