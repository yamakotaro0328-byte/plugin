package com.yamakotaro.ecojobs.listeners;

import com.yamakotaro.ecojobs.PlayerJobManager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

/**
 * Handles hunter/archer/slayer/warrior/breeder/tamer/shearer/fisherman/treasurehunter - all
 * triggered by entity-related events (kills, breeding, taming, shearing, fishing).
 */
public class EntityJobListener implements Listener {

    private final PlayerJobManager jobs;

    public EntityJobListener(PlayerJobManager jobs) {
        this.jobs = jobs;
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
        if (wasRangedKill(event.getEntity().getLastDamageCause())) {
            jobs.reward(killer, "archer", "kill-mob-ranged", type, 1);
        }
    }

    private boolean wasRangedKill(EntityDamageEvent lastDamage) {
        return lastDamage instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getPlayer().getKiller();
        if (killer != null) {
            jobs.reward(killer, "warrior", "kill-player", "default", 1);
        }
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

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(event.getCaught() instanceof Item item)) {
            return;
        }
        String material = item.getItemStack().getType().name();
        switch (material) {
            case "COD", "SALMON", "PUFFERFISH", "TROPICAL_FISH" ->
                    jobs.reward(event.getPlayer(), "fisherman", "catch-fish", material, 1);
            // Anything else pulled up (enchanted books, bows, junk items, ...) counts as
            // "treasure" for simplicity - config only has a flat default reward for it.
            default -> jobs.reward(event.getPlayer(), "treasurehunter", "catch-treasure", "default", 1);
        }
    }
}
