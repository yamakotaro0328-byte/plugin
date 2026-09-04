package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.JobDefinition;
import com.yamakotaro.ecojobs.JobManager;
import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles hunter/archer/slayer/warrior/breeder/tamer/shearer/fisherman/treasurehunter - all
 * triggered by entity-related events (kills, breeding, taming, shearing, fishing).
 */
public class EntityJobListener implements Listener {

    private final PlayerJobManager jobs;
    private final JobManager jobManager;
    private final EvenMoreFishBridge evenMoreFish;
    /** 直近に報酬を出した「倒した側 -> 倒された側」の組。2人でのキル交換による無限稼ぎを防ぐ。 */
    private final Map<UUID, Map<UUID, Long>> recentPlayerKills = new HashMap<>();

    public EntityJobListener(PlayerJobManager jobs, JobManager jobManager, EvenMoreFishBridge evenMoreFish) {
        this.jobs = jobs;
        this.jobManager = jobManager;
        this.evenMoreFish = evenMoreFish;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        String type = event.getEntityType().name();
        // Disjoint by design (see config.yml): a mob is either a "boss" for slayer or an
        // ordinary hostile for hunter/archer, never both, so trying both is safe either way.
        jobs.reward(killer, "slayer", "kill-boss", type, 1);
        jobs.reward(killer, "hunter", "kill-mob", type, 1);
        // 弓術師は config.yml の説明どおり「hunterの表に載っている敵mob」を遠距離で倒した時だけ。
        // この確認が無いと archer の default 報酬が牛・鶏・村人にも出てしまい、繁殖させた動物を
        // 撃つだけで無限に稼げてしまう。
        if (isListedHostile(type) && wasRangedKill(event.getEntity().getLastDamageCause())) {
            jobs.reward(killer, "archer", "kill-mob-ranged", type, 1);
        }
    }

    /** hunter の kill-mob 表に載っている(=このサーバーが「敵mob」と定義した)種類かどうか。 */
    private boolean isListedHostile(String entityType) {
        JobDefinition hunter = jobManager.get("hunter");
        return hunter != null && hunter.getReward("kill-mob", entityType) != null;
    }

    private boolean wasRangedKill(EntityDamageEvent lastDamage) {
        return lastDamage instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!shouldPayForPlayerKill(killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        jobs.reward(killer, "warrior", "kill-player", "default", 1);
    }

    /**
     * @return 今回のキルに報酬を出してよければ true。同じ相手を短時間に何度も倒した場合
     * (2人組でのキル交換)は2回目以降 false になる。クールダウンは config.yml の
     * anti-farm.player-kill-cooldown-minutes で調整でき、0にすると無制限。
     */
    private boolean shouldPayForPlayerKill(UUID killer, UUID victim) {
        long cooldownMillis = jobManager.playerKillCooldownMillis();
        if (cooldownMillis <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Map<UUID, Long> victims = recentPlayerKills.computeIfAbsent(killer, k -> new HashMap<>());
        // 期限切れの記録はここで捨てる。別途タイマーを持たなくてもメモリが際限なく増えない。
        victims.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownMillis);
        if (victims.containsKey(victim)) {
            return false;
        }
        victims.put(victim, now);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            jobs.reward(player, "breeder", "breed-entity", event.getEntityType().name(), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) {
            jobs.reward(player, "tamer", "tame-entity", event.getEntityType().name(), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        jobs.reward(event.getPlayer(), "shearer", "shear-entity", event.getEntity().getType().name(), 1);
    }

    // EMFは自分のPlayerFishEventハンドラから独自イベントを発火するため、こちらを後ろ
    // (HIGHEST)に回して、EMF側で支払い済みかどうかを見てから判断できるようにする。
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(event.getCaught() instanceof Item item)) {
            return;
        }
        // EvenMoreFishのカスタム魚はレア度別にEMF側で支払い済みなので、ここでは何もしない
        // (1回の釣りで二重に支払われないように)。
        if (evenMoreFish.handledRecently(event.getPlayer()) || evenMoreFish.isEvenMoreFishItem(item.getItemStack())) {
            return;
        }
        String material = item.getItemStack().getType().name();
        switch (material) {
            case "COD", "SALMON", "PUFFERFISH", "TROPICAL_FISH" ->
                    jobs.reward(event.getPlayer(), "fisherman", "catch-fish", material, 1);
            // 魚以外はお宝側で引く。本物のお宝(エンチャント本・名札等)は config.yml に個別に
            // 載っており、載っていない物(棒・革・ボウル等のゴミ)は default の少額になる。
            default -> jobs.reward(event.getPlayer(), "treasurehunter", "catch-treasure", material, 1);
        }
    }
}
