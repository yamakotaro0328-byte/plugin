package com.yamakotaro.serverkit.claims;

import com.yamakotaro.serverkit.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * クレーム範囲選択用の杖の処理と、クレーム内の保護(ブロック破壊・設置、コンテナ使用、
 * PvP、モンスタースポーン)をまとめて扱う。オーナー・信頼済みメンバー・
 * serverkit.claims.admin権限を持つ者は保護の対象外。
 */
public class ClaimListener implements Listener {

    private final Plugin plugin;
    private final ClaimManager manager;
    private final ClaimSelectionManager selectionManager;
    private final Messages messages;

    public ClaimListener(Plugin plugin, ClaimManager manager, ClaimSelectionManager selectionManager, Messages messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.selectionManager = selectionManager;
        this.messages = messages;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectionManager.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (selectionManager.isWand(event.getItem())) {
            handleWandUse(event, player);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Container)) {
            return;
        }
        if (!isProtectionActive("containers") || bypasses(player)) {
            return;
        }
        Claim claim = manager.findClaimAt(event.getClickedBlock().getLocation());
        if (claim != null && !claim.isMember(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(messages.get("claims.protected", Map.of("owner", ownerName(claim))));
        }
    }

    private void handleWandUse(PlayerInteractEvent event, Player player) {
        if (event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        Location location = event.getClickedBlock().getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selectionManager.setPos1(player.getUniqueId(), location);
            player.sendMessage(messages.get("claims.pos1-set",
                    Map.of("x", String.valueOf(location.getBlockX()), "z", String.valueOf(location.getBlockZ()))));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selectionManager.setPos2(player.getUniqueId(), location);
            player.sendMessage(messages.get("claims.pos2-set",
                    Map.of("x", String.valueOf(location.getBlockX()), "z", String.valueOf(location.getBlockZ()))));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!isProtectionActive("blocks") || bypasses(event.getPlayer())) {
            return;
        }
        Claim claim = manager.findClaimAt(event.getBlock().getLocation());
        if (claim != null && !claim.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("claims.protected", Map.of("owner", ownerName(claim))));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!isProtectionActive("blocks") || bypasses(event.getPlayer())) {
            return;
        }
        Claim claim = manager.findClaimAt(event.getBlock().getLocation());
        if (claim != null && !claim.isMember(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("claims.protected", Map.of("owner", ownerName(claim))));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isProtectionActive("pvp")) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || bypasses(attacker)) {
            return;
        }
        Claim claim = manager.findClaimAt(victim.getLocation());
        if (claim != null && !claim.isMember(attacker.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage(messages.get("claims.protected", Map.of("owner", ownerName(claim))));
        }
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!isProtectionActive("mob-spawning") || !(event.getEntity() instanceof Monster)) {
            return;
        }
        if (manager.findClaimAt(event.getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private boolean bypasses(Player player) {
        return player.hasPermission("serverkit.claims.admin");
    }

    private String ownerName(Claim claim) {
        String name = Bukkit.getOfflinePlayer(claim.getOwner()).getName();
        return name != null ? name : "?";
    }

    private boolean isProtectionActive(String key) {
        return manager.isEnabled() && plugin.getConfig().getBoolean("claims.protect." + key, true);
    }
}
