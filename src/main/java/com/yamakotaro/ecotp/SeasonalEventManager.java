package com.yamakotaro.ecotp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * 季節イベント (ハロウィン・クリスマス・お正月など、config.yml の seasonal-events.events)。
 * 期間中は:
 *  - 指定した Mob の一部が帽子をかぶって湧き、倒すとイベントコインを落とす
 *  - /event のショップでコインを景品 (アイテム・称号・お金) と交換できる
 *  - /event hat で、他の人から見た自分の頭に帽子を表示できる (見た目だけ)
 *  - gift-days の日に初めてログインすると、お年玉/プレゼント (ランダムな額) がもらえる
 */
public class SeasonalEventManager implements Listener {

    /** config から読んだイベント1つ分。 */
    public record SeasonalEvent(String key, String name, MonthDay start, MonthDay end, Set<EntityType> mobs,
                                double dressChance, ItemStack hat, ItemStack coinTemplate, double coinChance,
                                int coinsPerDrop, Set<MonthDay> giftDays, double giftMin, double giftMax,
                                List<Map<?, ?>> shop) {
    }

    private final EcoTpPlugin plugin;
    private final NamespacedKey mobKey;
    private final NamespacedKey coinKey;
    private final File dataFile;
    private final YamlConfiguration data;
    /** 今帽子を見せている人 (帽子を外した/終わった時に元の見た目へ戻すため)。 */
    private final Set<UUID> hatShown = new HashSet<>();
    private String lastActiveKey;
    private int tickCount;

    public SeasonalEventManager(EcoTpPlugin plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "event_mob");
        this.coinKey = new NamespacedKey(plugin, "event_coin");
        this.dataFile = new File(plugin.getDataFolder(), "seasonal-events.yml");
        this.data = YamlIo.load(dataFile);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 100L);
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("seasonal-events.timezone", "Asia/Tokyo"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private LocalDate today() {
        return LocalDate.now(zone());
    }

    /** @return 今開催中のイベント。無ければ null (複数重なっていたら config で先に書いた方)。 */
    public SeasonalEvent getActive() {
        if (!plugin.getConfig().getBoolean("seasonal-events.enabled", true)) {
            return null;
        }
        ConfigurationSection events = plugin.getConfig().getConfigurationSection("seasonal-events.events");
        if (events == null) {
            return null;
        }
        MonthDay now = MonthDay.from(today());
        for (String key : events.getKeys(false)) {
            SeasonalEvent event = parse(key, events.getConfigurationSection(key));
            if (event != null && inRange(now, event.start(), event.end())) {
                return event;
            }
        }
        return null;
    }

    private static boolean inRange(MonthDay day, MonthDay start, MonthDay end) {
        if (!start.isAfter(end)) {
            return !day.isBefore(start) && !day.isAfter(end);
        }
        return !day.isBefore(start) || !day.isAfter(end); // 年をまたぐ (12-31〜01-07 など)
    }

    private SeasonalEvent parse(String key, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        try {
            MonthDay start = MonthDay.parse("--" + section.getString("start"));
            MonthDay end = MonthDay.parse("--" + section.getString("end"));
            Set<EntityType> mobs = new HashSet<>();
            for (String name : section.getStringList("mobs")) {
                try {
                    mobs.add(EntityType.valueOf(name.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // 知らない Mob 名は無視
                }
            }
            Set<MonthDay> giftDays = new HashSet<>();
            for (String day : section.getStringList("gift-days")) {
                giftDays.add(MonthDay.parse("--" + day));
            }
            return new SeasonalEvent(key, ChatUtil.color(section.getString("name", key)), start, end, mobs,
                    section.getDouble("dress-chance", 25.0), hat(section), coin(key, section),
                    section.getDouble("coin-drop-chance", 100.0), Math.max(1, section.getInt("coins-per-drop", 1)),
                    giftDays, section.getDouble("gift-min", 500.0), section.getDouble("gift-max", 3000.0),
                    section.getMapList("shop"));
        } catch (Exception e) {
            plugin.getLogger().warning("seasonal-events.events." + key + " is invalid and was skipped: " + e.getMessage());
            return null;
        }
    }

    private static ItemStack hat(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("hat", ""));
        if (material == null || material.isAir()) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        String color = section.getString("hat-color");
        if (color != null && item.getItemMeta() instanceof LeatherArmorMeta leather) {
            try {
                leather.setColor(Color.fromRGB(Integer.parseInt(color.replace("#", ""), 16)));
                item.setItemMeta(leather);
            } catch (NumberFormatException ignored) {
                // 色の書式が違う
            }
        }
        return item;
    }

    private ItemStack coin(String key, ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("coin-item", "GOLD_NUGGET"));
        ItemStack item = new ItemStack(material == null ? Material.GOLD_NUGGET : material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatUtil.color(section.getString("coin-name", "&6Event Coin")));
        meta.setLore(List.of(ChatUtil.color(plugin.getMessages().get("event.coin-lore"))));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(coinKey, PersistentDataType.STRING, key);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCoin(ItemStack item, String eventKey) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String key = item.getItemMeta().getPersistentDataContainer().get(coinKey, PersistentDataType.STRING);
        return key != null && (eventKey == null || key.equals(eventKey));
    }

    public ItemStack createCoins(SeasonalEvent event, int amount) {
        ItemStack item = event.coinTemplate().clone();
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return item;
    }

    public int countCoins(Player player, SeasonalEvent event) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isCoin(item, event.key())) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** @return 足りていて取れたら true。 */
    public boolean takeCoins(Player player, SeasonalEvent event, int amount) {
        if (countCoins(player, event) < amount) {
            return false;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (isCoin(item, event.key())) {
                int take = Math.min(left, item.getAmount());
                item.setAmount(item.getAmount() - take);
                left -= take;
                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    /** 渡す。入りきらない分は足元に落とす。 */
    public void giveItem(Player player, ItemStack item) {
        for (ItemStack overflow : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    private void tick() {
        tickCount++;
        SeasonalEvent active = getActive();
        String activeKey = active == null ? null : active.key();
        // 起動直後の1回目は今の状態を覚えるだけ。その後、変わった時に全体へ知らせる。
        if (tickCount > 1 && !java.util.Objects.equals(activeKey, lastActiveKey)) {
            if (activeKey != null) {
                Bukkit.broadcastMessage(plugin.getMessages().get("event.started", "event", active.name()));
            } else {
                Bukkit.broadcastMessage(plugin.getMessages().get("event.ended"));
            }
        }
        lastActiveKey = activeKey;
        updateHats(active);
        // プレゼントは5分ごとにも配る (オンラインのまま日付が変わった人の分)。
        if (active != null && tickCount % 60 == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                tryGift(player, active);
            }
        }
    }

    // ---- 帽子 (見た目だけ) ----

    public boolean isWearingHat(UUID uuid) {
        return data.getStringList("hats").contains(uuid.toString());
    }

    /** @return 切り替え後にかぶっているなら true。 */
    public boolean toggleHat(Player player) {
        List<String> hats = new ArrayList<>(data.getStringList("hats"));
        String id = player.getUniqueId().toString();
        boolean wearing = !hats.remove(id);
        if (wearing) {
            hats.add(id);
        }
        data.set("hats", hats);
        saveData();
        updateHats(getActive());
        return wearing;
    }

    private void updateHats(SeasonalEvent active) {
        for (Player wearer : Bukkit.getOnlinePlayers()) {
            boolean show = active != null && active.hat() != null && isWearingHat(wearer.getUniqueId());
            if (!show && !hatShown.remove(wearer.getUniqueId())) {
                continue;
            }
            if (show) {
                hatShown.add(wearer.getUniqueId());
            }
            ItemStack real = wearer.getInventory().getHelmet();
            ItemStack shown = show ? active.hat() : (real == null ? new ItemStack(Material.AIR) : real);
            Location location = wearer.getLocation();
            for (Player viewer : wearer.getWorld().getPlayers()) {
                // 本人には送らない (かぶったカボチャの視界のぼやけを出さないため)。
                if (viewer != wearer && viewer.getLocation().distanceSquared(location) < 96 * 96) {
                    viewer.sendEquipmentChange(wearer, EquipmentSlot.HEAD, shown);
                }
            }
        }
    }

    // ---- お年玉 / プレゼント ----

    private void tryGift(Player player, SeasonalEvent event) {
        LocalDate date = today();
        if (!event.giftDays().contains(MonthDay.from(date)) || event.giftMax() <= 0) {
            return;
        }
        String path = "gifts." + event.key() + "." + date;
        List<String> received = new ArrayList<>(data.getStringList(path));
        String id = player.getUniqueId().toString();
        if (received.contains(id)) {
            return;
        }
        Economy economy = plugin.getEconomyHolder().get();
        if (economy == null) {
            return;
        }
        double min = Math.min(event.giftMin(), event.giftMax());
        double amount = Math.round(ThreadLocalRandom.current().nextDouble(min, event.giftMax() + 1) / 10.0) * 10.0;
        if (!economy.depositPlayer(player, amount).transactionSuccess()) {
            return;
        }
        received.add(id);
        data.set(path, received);
        saveData();
        player.sendTitle(plugin.getMessages().get("event.gift-title", "event", event.name()),
                plugin.getMessages().get("event.gift-subtitle", "amount", ChatUtil.formatMoney(amount)), 10, 60, 20);
        player.sendMessage(plugin.msg("event.gift", "event", event.name(), "amount", ChatUtil.formatMoney(amount)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.5, 0), 50, 0.5, 0.5, 0.5, 0.15);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            SeasonalEvent active = getActive();
            if (active == null || !player.isOnline()) {
                return;
            }
            player.sendMessage(plugin.msg("event.join-notice", "event", active.name()));
            tryGift(player, active);
            updateHats(active);
        }, 80L);
    }

    // ---- 帽子をかぶった Mob とコイン ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        // 自然湧きだけ (スポナーやスポーンエッグでコインを量産されないように)。
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        SeasonalEvent active = getActive();
        if (active == null || !active.mobs().contains(event.getEntityType())
                || ThreadLocalRandom.current().nextDouble(100) >= active.dressChance()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null && active.hat() != null) {
            equipment.setHelmet(active.hat().clone());
            equipment.setHelmetDropChance(0f);
        }
        entity.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, active.key());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        String key = event.getEntity().getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
        if (key == null || event.getEntity().getKiller() == null) {
            return;
        }
        SeasonalEvent active = getActive();
        if (active == null || !active.key().equals(key)
                || ThreadLocalRandom.current().nextDouble(100) >= active.coinChance()) {
            return;
        }
        event.getDrops().add(createCoins(active, active.coinsPerDrop()));
    }

    /** コインを作業台で別のアイテムに変えられないようにする (金塊→金インゴットなど)。 */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isCoin(item, null)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    private void saveData() {
        try {
            YamlIo.save(data, dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save seasonal-events.yml", e);
        }
    }
}
