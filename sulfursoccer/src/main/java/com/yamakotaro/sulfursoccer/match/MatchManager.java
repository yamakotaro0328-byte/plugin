package com.yamakotaro.sulfursoccer.match;

import com.yamakotaro.sulfursoccer.Messages;
import com.yamakotaro.sulfursoccer.arena.Arena;
import com.yamakotaro.sulfursoccer.arena.ArenaManager;
import com.yamakotaro.sulfursoccer.arena.Point;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every arena's live match state and the ball entity that goes with it. Everything here is
 * in-memory - a match doesn't survive a restart, it just resets. See SoccerTickTask for the
 * per-tick goal/win-condition checks that drive this.
 */
public class MatchManager {

    private final JavaPlugin plugin;
    private final ArenaManager arenaManager;
    private final Messages messages;
    private final MatchScoreboard matchScoreboard;
    private final Map<String, Match> matchesByArena = new HashMap<>();

    public MatchManager(JavaPlugin plugin, ArenaManager arenaManager, Messages messages, MatchScoreboard matchScoreboard) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.messages = messages;
        this.matchScoreboard = matchScoreboard;
    }

    private Match getOrCreateMatch(String arenaId) {
        return matchesByArena.computeIfAbsent(arenaId, Match::new);
    }

    public Optional<Match> findMatchByBallId(UUID entityId) {
        return matchesByArena.values().stream().filter(m -> entityId.equals(m.getBallEntityId())).findFirst();
    }

    public Collection<Match> allRunningMatches() {
        return matchesByArena.values().stream().filter(Match::isRunning).toList();
    }

    /** Removes the player from whichever match they were in, on either team - a no-op if they weren't in one. */
    public void leave(UUID playerId) {
        for (Match match : matchesByArena.values()) {
            boolean wasPlaying = match.getTeamA().remove(playerId) | match.getTeamB().remove(playerId);
            if (wasPlaying && match.isRunning()) {
                matchScoreboard.clear(playerId);
            }
        }
    }

    /** Joins the given arena's match, auto-assigning whichever team currently has fewer players. */
    public JoinResult join(UUID playerId, String arenaId) {
        Optional<Arena> arena = arenaManager.find(arenaId);
        if (arena.isEmpty()) {
            return JoinResult.error("arena.not-found");
        }
        leave(playerId);
        Match match = getOrCreateMatch(arena.get().id());
        char team = match.getTeamA().size() <= match.getTeamB().size() ? 'a' : 'b';
        (team == 'a' ? match.getTeamA() : match.getTeamB()).add(playerId);
        return JoinResult.success(team);
    }

    public String start(String arenaId) {
        Optional<Arena> arenaOpt = arenaManager.find(arenaId);
        if (arenaOpt.isEmpty()) {
            return "arena.not-found";
        }
        Arena arena = arenaOpt.get();
        if (!arena.isReady()) {
            return "arena.not-ready";
        }
        Match match = getOrCreateMatch(arena.id());
        if (match.isRunning() || match.isCountdownActive()) {
            return "match.already-running";
        }
        if (match.getTeamA().isEmpty() || match.getTeamB().isEmpty()) {
            return "match.need-both-teams";
        }
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return "arena.world-not-loaded";
        }
        teleportTeam(match.getTeamA(), world, arena.spawnA());
        teleportTeam(match.getTeamB(), world, arena.spawnB());

        int countdownSeconds = plugin.getConfig().getInt("match.countdown-seconds", 3);
        long countdownEndTime = System.currentTimeMillis() + (countdownSeconds * 1000L);
        match.setCountdownEndMillis(countdownEndTime);

        scheduleCountdownTick(arena, match, countdownSeconds);
        return null;
    }

    public String stopWithMessage(String arenaId, String messageKey) {
        Match match = matchesByArena.get(ArenaManager.normalize(arenaId));
        if (match == null || !match.isRunning()) {
            return "match.not-running";
        }
        endMatch(match, messageKey);
        return null;
    }

    void endMatch(Match match, String announcementKey) {
        match.setRunning(false);
        removeBall(match);
        matchScoreboard.clearAll(match);
        announceToMatch(match, announcementKey, Map.of(
                "scoreA", String.valueOf(match.getScoreA()), "scoreB", String.valueOf(match.getScoreB())));
    }

    /** Sends a message to every online player on either team of this match. */
    public void announceToMatch(Match match, String key, Map<String, String> placeholders) {
        for (UUID playerId : match.getTeamA()) {
            messageIfOnline(playerId, key, placeholders);
        }
        for (UUID playerId : match.getTeamB()) {
            messageIfOnline(playerId, key, placeholders);
        }
    }

    void spawnBall(Arena arena, Match match) {
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return;
        }
        Point kickoff = arena.kickoff();
        Location location = new Location(world, kickoff.centerX(), kickoff.y(), kickoff.centerZ());
        Entity ball = world.spawnEntity(location, EntityType.SULFUR_CUBE);
        // No setAI(false) here, and no custom velocity code anywhere in this plugin - a Sulfur
        // Cube stops moving on its own the moment it's holding an absorbed block (see the
        // equipment set below) and becomes a pure physics object instead: hitting it converts all
        // combat damage into knockback launched opposite the hit, and it ricochets and decelerates
        // through vanilla's own "Bouncy" archetype. There is nothing left for this plugin to drive.
        if (ball instanceof LivingEntity livingEntity) {
            EntityEquipment equipment = livingEntity.getEquipment();
            if (equipment != null) {
                // The birch look-and-feel goes on the ball itself, not as scenery in the arena -
                // equipping a birch log in the body slot makes the ball visibly birch-textured
                // wherever it rolls, the same way a llama wears a carpet or a wolf wears armor.
                equipment.setItem(EquipmentSlot.BODY, new ItemStack(Material.BIRCH_LOG));
            }
        }
        match.setBallEntityId(ball.getUniqueId());
    }

    /** Called after a goal, or when the tracked ball entity is found dead/missing - either way, a fresh one appears at kickoff. */
    public void respawnBall(Match match) {
        removeBall(match);
        arenaManager.find(match.getArenaId()).ifPresent(arena -> spawnBall(arena, match));
    }

    private void removeBall(Match match) {
        if (match.getBallEntityId() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(match.getBallEntityId());
        if (entity != null) {
            entity.remove();
        }
        match.setBallEntityId(null);
    }

    private void teleportTeam(Set<UUID> team, World world, Point spawnPoint) {
        int index = 0;
        for (UUID playerId : team) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                double offsetX = (index % 3) * 1.5 - 1.5;
                double offsetZ = (index / 3) * 1.5 - 1.5;
                Location loc = new Location(world, spawnPoint.centerX() + offsetX, spawnPoint.y(), spawnPoint.centerZ() + offsetZ);
                loc.setDirection(new Location(world, spawnPoint.centerX(), spawnPoint.y(), spawnPoint.centerZ()).toVector()
                        .subtract(loc.toVector()).normalize());
                player.teleport(loc);
            }
            index++;
        }
    }

    private void scheduleCountdownTick(Arena arena, Match match, int secondsRemaining) {
        if (secondsRemaining > 0) {
            titleToMatch(match, messages.get("match.countdown-title", Map.of("seconds", String.valueOf(secondsRemaining))), messages.get("match.countdown-subtitle", Map.of()));
            soundToMatch(match, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f + (secondsRemaining * 0.3f));

            int nextSecond = secondsRemaining - 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> scheduleCountdownTick(arena, match, nextSecond), 20L);
        } else {
            match.resetScores();
            match.setStartedAtMillis(System.currentTimeMillis());
            match.setRunning(true);
            spawnBall(arena, match);

            titleToMatch(match, messages.get("match.kickoff-title", Map.of()), messages.get("match.kickoff-subtitle", Map.of()));
            soundToMatch(match, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            announceToMatch(match, "match.started-chat", Map.of("name", arena.id()));
        }
    }

    private void titleToMatch(Match match, Component title, Component subtitle) {
        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showTitle(Title.title(title, subtitle));
            }
        }
        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showTitle(Title.title(title, subtitle));
            }
        }
    }

    private void soundToMatch(Match match, Sound sound, float volume, float pitch) {
        for (UUID playerId : match.getTeamA()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
        for (UUID playerId : match.getTeamB()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private void messageIfOnline(UUID playerId, String key, Map<String, String> placeholders) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(messages.get(key, placeholders));
        }
    }
}
