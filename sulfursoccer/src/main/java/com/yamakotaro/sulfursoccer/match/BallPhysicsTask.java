package com.yamakotaro.sulfursoccer.match;

import com.yamakotaro.sulfursoccer.arena.Arena;
import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.arena.Box;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives the ball's actual movement, at a much tighter tick rate than SoccerTickTask's
 * goal/win-condition checks. Nothing else in this plugin ever calls setVelocity on the ball, so
 * without this class the ball just sits there under vanilla gravity - "standing near it" alone
 * never launches it, real knockback only ever comes from an attack or an explosion, neither of
 * which a soccer match should require:
 *
 * - kick: every match participant standing within match.kick-radius of the ball nudges it away
 *   from themselves, horizontally, by match.kick-force.
 * - friction: every tick, the ball's horizontal velocity is scaled down by match.friction so it
 *   coasts to a stop instead of sliding forever.
 * - max speed: after kicks are applied, horizontal speed is clamped to match.max-speed.
 * - field boundary bounce: reflects the ball's horizontal velocity back inward the moment it
 *   crosses the field's horizontal edge - this only does anything useful now that this class is
 *   the one place giving the ball real velocity to reflect.
 *
 * Vertical velocity (gravity) is left entirely to vanilla - only the horizontal (x/z) components
 * are ever touched here.
 */
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
        double kickRadius = plugin.getConfig().getDouble("match.kick-radius", 1.2);
        double kickForce = plugin.getConfig().getDouble("match.kick-force", 0.55);
        double maxSpeed = plugin.getConfig().getDouble("match.max-speed", 1.3);
        double friction = plugin.getConfig().getDouble("match.friction", 0.9);

        for (Match match : matchManager.allRunningMatches()) {
            Optional<Arena> arenaOpt = arenaManager.find(match.getArenaId());
            if (arenaOpt.isEmpty()) {
                continue;
            }
            Arena arena = arenaOpt.get();
            Entity ball = match.getBallEntityId() != null ? Bukkit.getEntity(match.getBallEntityId()) : null;
            if (ball == null || ball.isDead()) {
                continue;
            }

            Vector velocity = ball.getVelocity();
            double velocityX = velocity.getX() * friction;
            double velocityZ = velocity.getZ() * friction;

            Location ballLoc = ball.getLocation();
            for (Player player : onlinePlayers(match)) {
                if (!player.getWorld().equals(ball.getWorld())) {
                    continue;
                }
                Location playerLoc = player.getLocation();
                double dx = ballLoc.getX() - playerLoc.getX();
                double dz = ballLoc.getZ() - playerLoc.getZ();
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > kickRadius * kickRadius || distanceSquared < 1.0E-6) {
                    continue;
                }
                double distance = Math.sqrt(distanceSquared);
                velocityX += (dx / distance) * kickForce;
                velocityZ += (dz / distance) * kickForce;
            }

            double speed = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
            if (speed > maxSpeed) {
                double scale = maxSpeed / speed;
                velocityX *= scale;
                velocityZ *= scale;
            }

            ball.setVelocity(new Vector(velocityX, velocity.getY(), velocityZ));
            bounceOffFieldBoundary(ball, arena.field());
        }
    }

    private List<Player> onlinePlayers(Match match) {
        List<Player> players = new ArrayList<>();
        for (UUID id : match.getTeamA()) {
            addIfOnline(players, id);
        }
        for (UUID id : match.getTeamB()) {
            addIfOnline(players, id);
        }
        return players;
    }

    private void addIfOnline(List<Player> players, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            players.add(player);
        }
    }

    /** Reflects the ball's horizontal velocity back inward the moment it crosses the field's
     * horizontal edge. Ceiling/floor are left untouched, same as ArenaWallBuilder's walls - a
     * soccer field, not a sealed box. */
    private void bounceOffFieldBoundary(Entity ball, Box field) {
        Location loc = ball.getLocation();
        Vector velocity = ball.getVelocity();
        double velocityX = velocity.getX();
        double velocityZ = velocity.getZ();
        boolean bounced = false;

        if (loc.getX() < field.minX() && velocityX < 0) {
            velocityX = -velocityX;
            bounced = true;
        } else if (loc.getX() > field.maxX() + 1 && velocityX > 0) {
            velocityX = -velocityX;
            bounced = true;
        }
        if (loc.getZ() < field.minZ() && velocityZ < 0) {
            velocityZ = -velocityZ;
            bounced = true;
        } else if (loc.getZ() > field.maxZ() + 1 && velocityZ > 0) {
            velocityZ = -velocityZ;
            bounced = true;
        }
        if (bounced) {
            ball.setVelocity(new Vector(velocityX, velocity.getY(), velocityZ));
        }
    }
}
