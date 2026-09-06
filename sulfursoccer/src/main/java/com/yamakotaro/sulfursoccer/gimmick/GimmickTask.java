package com.yamakotaro.sulfursoccer.gimmick;

import com.yamakotaro.sulfursoccer.arena.Arena;
import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.arena.Box;
import com.yamakotaro.sulfursoccer.arena.Point;
import com.yamakotaro.sulfursoccer.match.Match;
import com.yamakotaro.sulfursoccer.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Drives the three gimmick types that need per-tick logic - see GimmickBuilder for the other two
 * (ICE/BOUNCE), which are one-time block placements instead and need nothing here:
 *
 * - BUMP: the region's floor periodically rises one block and comes back down.
 * - WIND: continuously pushes the ball along the arena's spawnA-to-spawnB axis while it's over the
 *   region (an arbitrary but always-available "downfield" direction, without needing a direction
 *   parameter on the gimmick itself).
 * - WARP: teleports the ball back to kickoff the moment it's over the region.
 *
 * WIND/WARP only check the region's footprint (x/z), not height - "standing over the marked
 * ground", not a strict 3D volume the ball has to be exactly inside. In practice that footprint is
 * a single column now (see SoccerCommand#randomPositionWithin - the wand selection is a spawn
 * range, one column inside it is chosen at random when the gimmick is added), but nothing here
 * assumes that.
 *
 * Runs independently of SoccerTickTask (see SulfurSoccerPlugin) at its own tick rate.
 */
public class GimmickTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final ArenaManager arenaManager;
    private final GimmickManager gimmickManager;
    private final MatchManager matchManager;
    private final Map<String, Boolean> bumpUp = new HashMap<>();
    private final Map<String, Long> bumpLastToggle = new HashMap<>();

    public GimmickTask(JavaPlugin plugin, ArenaManager arenaManager, GimmickManager gimmickManager, MatchManager matchManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.gimmickManager = gimmickManager;
        this.matchManager = matchManager;
    }

    @Override
    public void run() {
        long bumpPeriodMillis = plugin.getConfig().getLong("gimmick.bump-period-seconds", 3) * 1000L;
        double windForce = plugin.getConfig().getDouble("gimmick.wind-force", 0.15);
        long now = System.currentTimeMillis();

        for (Match match : matchManager.allRunningMatches()) {
            Optional<Arena> arenaOpt = arenaManager.find(match.getArenaId());
            if (arenaOpt.isEmpty()) {
                continue;
            }
            Arena arena = arenaOpt.get();
            World world = Bukkit.getWorld(arena.world());
            if (world == null) {
                continue;
            }
            Entity ball = match.getBallEntityId() != null ? Bukkit.getEntity(match.getBallEntityId()) : null;

            for (Gimmick gimmick : gimmickManager.forArena(arena.id())) {
                switch (gimmick.type()) {
                    case BUMP -> tickBump(world, arena.id(), gimmick, now, bumpPeriodMillis);
                    case WIND -> {
                        if (ball != null) {
                            tickWind(ball, arena, gimmick, windForce);
                        }
                    }
                    case WARP -> {
                        if (ball != null) {
                            tickWarp(ball, arena, gimmick);
                        }
                    }
                    default -> {
                        // ICE/BOUNCE are one-time block placements - see GimmickBuilder.
                    }
                }
            }
        }
    }

    private void tickBump(World world, String arenaId, Gimmick gimmick, long now, long periodMillis) {
        String key = arenaId + ":" + gimmick.id();
        Long lastToggle = bumpLastToggle.get(key);
        if (lastToggle != null && now - lastToggle < periodMillis) {
            return;
        }
        bumpLastToggle.put(key, now);
        boolean up = !bumpUp.getOrDefault(key, false);
        bumpUp.put(key, up);

        Box region = gimmick.region();
        int y = region.minY() + 1;
        Material material = up ? Material.STONE : Material.AIR;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                world.getBlockAt(x, y, z).setType(material);
            }
        }
    }

    private void tickWind(Entity ball, Arena arena, Gimmick gimmick, double force) {
        Location loc = ball.getLocation();
        if (!gimmick.region().containsXZ(loc.getBlockX(), loc.getBlockZ())) {
            return;
        }
        Point from = arena.spawnA();
        Point to = arena.spawnB();
        Vector direction = new Vector(to.x() - from.x(), 0, to.z() - from.z());
        if (direction.lengthSquared() == 0) {
            return;
        }
        direction.normalize().multiply(force);
        ball.setVelocity(ball.getVelocity().add(direction));
    }

    private void tickWarp(Entity ball, Arena arena, Gimmick gimmick) {
        Location loc = ball.getLocation();
        if (!gimmick.region().containsXZ(loc.getBlockX(), loc.getBlockZ())) {
            return;
        }
        Point kickoff = arena.kickoff();
        ball.teleport(new Location(loc.getWorld(), kickoff.centerX(), kickoff.y(), kickoff.centerZ()));
        ball.setVelocity(new Vector(0, 0, 0));
    }
}
