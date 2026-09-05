package com.yamakotaro.sulfursoccer.match;

import com.yamakotaro.sulfursoccer.arena.Arena;
import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.arena.Box;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Optional;

public class BallPhysicsTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final ArenaManager arenaManager;
    private final MatchManager matchManager;

    public BallPhysicsTask(JavaPlugin plugin, ArenaManager arenaManager, MatchManager matchManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
    }

    @Override
    public void run() {
        for (Match match : matchManager.allRunningMatches()) {
            if (match.getBallEntityId() == null) {
                continue;
            }

            Optional<Arena> arenaOpt = arenaManager.find(match.getArenaId());
            if (arenaOpt.isEmpty()) continue;
            Arena arena = arenaOpt.get();

            org.bukkit.World world = plugin.getServer().getWorld(arena.world());
            if (world == null) continue;

            Entity ball = null;
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(match.getBallEntityId())) {
                    ball = entity;
                    break;
                }
            }

            if (ball == null || !ball.isValid()) {
                continue;
            }

            Vector velocity = ball.getVelocity().clone();
            double kickRadius = plugin.getConfig().getDouble("match.kick-radius", 1.2);
            double kickForce = plugin.getConfig().getDouble("match.kick-force", 0.55);

            for (Player player : ball.getWorld().getPlayers()) {
                if (!match.isPlaying(player.getUniqueId())) continue;
                if (!player.isOnline()) continue;

                double distance = player.getLocation().distance(ball.getLocation());
                if (distance < kickRadius) {
                    Vector direction = ball.getLocation().subtract(player.getLocation()).toVector();
                    direction.setY(0);
                    if (direction.length() > 0) {
                        direction.normalize();
                        velocity.add(direction.multiply(kickForce));
                    }
                }
            }

            double friction = plugin.getConfig().getDouble("match.friction", 0.9);
            velocity.setX(velocity.getX() * friction);
            velocity.setZ(velocity.getZ() * friction);

            double maxSpeed = plugin.getConfig().getDouble("match.max-speed", 1.3);
            double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
            if (horizontalSpeed > maxSpeed) {
                double scale = maxSpeed / horizontalSpeed;
                velocity.setX(velocity.getX() * scale);
                velocity.setZ(velocity.getZ() * scale);
            }

            ball.setVelocity(velocity);

            bounceOffFieldBoundary(ball, arena.field());
        }
    }

    private void bounceOffFieldBoundary(Entity ball, Box field) {
        double x = ball.getLocation().getX();
        double z = ball.getLocation().getZ();
        int minX = Math.min(field.corner1().x(), field.corner2().x());
        int maxX = Math.max(field.corner1().x(), field.corner2().x());
        int minZ = Math.min(field.corner1().z(), field.corner2().z());
        int maxZ = Math.max(field.corner1().z(), field.corner2().z());

        Vector vel = ball.getVelocity();

        if (x < minX || x > maxX + 1) {
            vel.setX(-vel.getX());
            if (x < minX) {
                ball.teleport(ball.getLocation().setX(minX + 0.5));
            } else {
                ball.teleport(ball.getLocation().setX(maxX + 0.5));
            }
        }

        if (z < minZ || z > maxZ + 1) {
            vel.setZ(-vel.getZ());
            if (z < minZ) {
                ball.teleport(ball.getLocation().setZ(minZ + 0.5));
            } else {
                ball.teleport(ball.getLocation().setZ(maxZ + 0.5));
            }
        }

        ball.setVelocity(vel);
    }
}
