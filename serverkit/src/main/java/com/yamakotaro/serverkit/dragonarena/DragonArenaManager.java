package com.yamakotaro.serverkit.dragonarena;

import com.yamakotaro.serverkit.Messages;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Lightweight, self-built (no Multiverse-Core) instanced End-only arenas: one fresh THE_END
 * world per solo/team fight, deleted again the moment the fight ends. Kept intentionally
 * minimal - vanilla dragon AI, no custom phases, no persistence across restarts.
 */
public class DragonArenaManager {

    public enum StartResult { SUCCESS, NOT_LEADER, ALREADY_IN_FIGHT, ON_COOLDOWN, MAX_INSTANCES, WORLD_ERROR }

    public record StartOutcome(StartResult result, long cooldownSecondsRemaining) {
        static StartOutcome of(StartResult result) {
            return new StartOutcome(result, 0);
        }
    }

    public enum LeaveResult { SUCCESS, NOT_IN_FIGHT }

    private final Plugin plugin;
    private final Messages messages;
    private final PartyManager partyManager;
    private final Map<UUID, FightInstance> activeFights = new HashMap<>();
    private final List<FightInstance> instances = new ArrayList<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public DragonArenaManager(Plugin plugin, Messages messages, PartyManager partyManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.partyManager = partyManager;
    }

    public boolean isFighting(UUID uuid) {
        return activeFights.containsKey(uuid);
    }

    public boolean isDefeated(UUID uuid) {
        FightInstance instance = activeFights.get(uuid);
        return instance != null && instance.isDefeated(uuid);
    }

    public StartOutcome start(Player initiator) {
        UUID initiatorId = initiator.getUniqueId();
        UUID leader = partyManager.getLeader(initiatorId);
        if (!leader.equals(initiatorId)) {
            return StartOutcome.of(StartResult.NOT_LEADER);
        }
        Set<UUID> members = partyManager.getMembers(initiatorId);
        for (UUID member : members) {
            if (activeFights.containsKey(member)) {
                return StartOutcome.of(StartResult.ALREADY_IN_FIGHT);
            }
        }
        long now = System.currentTimeMillis();
        long maxRemainingMillis = 0;
        for (UUID member : members) {
            Long until = cooldownUntil.get(member);
            if (until != null && until > now) {
                maxRemainingMillis = Math.max(maxRemainingMillis, until - now);
            }
        }
        if (maxRemainingMillis > 0) {
            return new StartOutcome(StartResult.ON_COOLDOWN, (maxRemainingMillis + 999) / 1000);
        }
        int maxInstances = plugin.getConfig().getInt("dragonarena.max-instances", 3);
        if (instances.size() >= maxInstances) {
            return StartOutcome.of(StartResult.MAX_INSTANCES);
        }

        List<Player> onlineMembers = new ArrayList<>();
        for (UUID member : members) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                onlineMembers.add(p);
            }
        }
        if (onlineMembers.isEmpty()) {
            return StartOutcome.of(StartResult.WORLD_ERROR);
        }

        World arenaWorld = createArenaWorld();
        if (arenaWorld == null) {
            return StartOutcome.of(StartResult.WORLD_ERROR);
        }

        Set<UUID> participantIds = onlineMembers.stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toCollection(HashSet::new));
        FightInstance instance = new FightInstance(arenaWorld, participantIds);
        instances.add(instance);

        Location platform = buildArrivalPlatform(arenaWorld);
        for (Player p : onlineMembers) {
            instance.setReturnLocation(p.getUniqueId(), p.getLocation());
            activeFights.put(p.getUniqueId(), instance);
            p.teleport(platform);
        }
        return StartOutcome.of(StartResult.SUCCESS);
    }

    public LeaveResult leave(Player player) {
        UUID uuid = player.getUniqueId();
        FightInstance instance = activeFights.get(uuid);
        if (instance == null) {
            return LeaveResult.NOT_IN_FIGHT;
        }
        restoreIfDefeated(instance, player);
        Location back = instance.getReturnLocation(uuid);
        if (back != null) {
            player.teleport(back);
        }
        finishParticipant(instance, uuid);
        if (instance.isEmpty() || instance.allDefeated()) {
            destroyInstance(instance);
        }
        return LeaveResult.SUCCESS;
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        FightInstance instance = activeFights.get(uuid);
        if (instance == null) {
            return;
        }
        // Restore their gamemode before they fully disconnect, so a defeated (spectator)
        // player who quits isn't stuck in spectator mode the next time they log in.
        restoreIfDefeated(instance, player);
        finishParticipant(instance, uuid);
        if (instance.isEmpty() || instance.allDefeated()) {
            destroyInstance(instance);
        }
    }

    /**
     * Instead of letting a participant actually die (vanilla death screen/respawn button), the
     * fatal hit is cancelled by the listener and the player is switched to spectator mode with a
     * "You Died"-style title, so they can keep watching their party's fight. Once every remaining
     * participant has been defeated this way, the whole instance ends in failure.
     */
    public void onParticipantDefeated(Player player) {
        UUID uuid = player.getUniqueId();
        FightInstance instance = activeFights.get(uuid);
        if (instance == null || instance.isDefeated(uuid)) {
            return;
        }
        instance.markDefeated(uuid, player.getGameMode());
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.showTitle(Title.title(
                messages.get("dragonarena.defeat-title", Map.of()),
                messages.get("dragonarena.defeat-subtitle", Map.of()),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        player.sendMessage(messages.get("dragonarena.defeated-chat", Map.of()));

        if (instance.allDefeated()) {
            for (UUID member : new HashSet<>(instance.getParticipants())) {
                Player p = Bukkit.getPlayer(member);
                if (p != null) {
                    restoreIfDefeated(instance, p);
                    Location back = instance.getReturnLocation(member);
                    if (back != null) {
                        p.teleport(back);
                    }
                    p.sendMessage(messages.get("dragonarena.defeat", Map.of()));
                }
                finishParticipant(instance, member);
            }
            destroyInstance(instance);
        }
    }

    public void onDragonDefeated(World world) {
        FightInstance instance = findInstance(world);
        if (instance == null) {
            return;
        }
        for (UUID uuid : new HashSet<>(instance.getParticipants())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                restoreIfDefeated(instance, p);
                Location back = instance.getReturnLocation(uuid);
                if (back != null) {
                    p.teleport(back);
                }
                p.sendMessage(messages.get("dragonarena.victory", Map.of()));
            }
            finishParticipant(instance, uuid);
        }
        destroyInstance(instance);
    }

    public boolean isArenaWorld(World world) {
        return findInstance(world) != null;
    }

    private void restoreIfDefeated(FightInstance instance, Player player) {
        UUID uuid = player.getUniqueId();
        if (!instance.isDefeated(uuid)) {
            return;
        }
        player.setGameMode(instance.getPreviousGameMode(uuid));
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private void finishParticipant(FightInstance instance, UUID uuid) {
        instance.removeParticipant(uuid);
        activeFights.remove(uuid);
        long cooldownMillis = plugin.getConfig().getLong("dragonarena.cooldown-seconds", 300) * 1000L;
        cooldownUntil.put(uuid, System.currentTimeMillis() + cooldownMillis);
    }

    private FightInstance findInstance(World world) {
        for (FightInstance instance : instances) {
            if (instance.getWorld().equals(world)) {
                return instance;
            }
        }
        return null;
    }

    private void destroyInstance(FightInstance instance) {
        instances.remove(instance);
        World world = instance.getWorld();
        String worldName = world.getName();
        // Bukkit refuses to unload a world that still has players in it - move any stragglers
        // out first (e.g. a spectating, defeated participant who hasn't been teleported back
        // yet) so the unload below actually succeeds.
        for (Player straggler : new ArrayList<>(world.getPlayers())) {
            straggler.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not unload dragon arena world " + worldName + "; leaving its files on disk to avoid corrupting a still-loaded world");
            return;
        }
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        deleteRecursively(worldFolder);
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private World createArenaWorld() {
        String name = "serverkit_dragonarena_" + UUID.randomUUID();
        try {
            WorldCreator creator = new WorldCreator(name);
            creator.environment(World.Environment.THE_END);
            World world = creator.createWorld();
            if (world == null) {
                return null;
            }
            world.setAutoSave(false);
            world.setDifficulty(Difficulty.NORMAL);
            return world;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create dragon arena world", e);
            return null;
        }
    }

    /**
     * Fresh End worlds don't have the small arrival platform (vanilla only places that when a
     * player exits a real End portal); we build one ourselves at the same coordinates vanilla
     * uses for that platform, which generation always leaves as void.
     */
    private Location buildArrivalPlatform(World world) {
        int baseX = 100;
        int baseY = 49;
        int baseZ = 0;
        for (int x = baseX - 2; x <= baseX + 2; x++) {
            for (int z = baseZ - 2; z <= baseZ + 2; z++) {
                world.getBlockAt(x, baseY, z).setType(Material.OBSIDIAN);
                world.getBlockAt(x, baseY + 1, z).setType(Material.AIR);
                world.getBlockAt(x, baseY + 2, z).setType(Material.AIR);
            }
        }
        return new Location(world, baseX + 0.5, baseY + 1, baseZ + 0.5);
    }
}
