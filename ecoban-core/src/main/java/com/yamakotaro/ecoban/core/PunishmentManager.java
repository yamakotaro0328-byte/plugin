package com.yamakotaro.ecoban.core;

import java.util.List;
import java.util.UUID;

/**
 * Platform-agnostic punishment logic shared by the Paper plugin (ecoban) and the Velocity proxy
 * plugin (ecoban-velocity) - each platform's commands and login/chat listeners are thin wrappers
 * around this, so the actual rules live in exactly one place.
 */
public class PunishmentManager {

    private final PunishmentStorage storage;

    public PunishmentManager(PunishmentStorage storage) {
        this.storage = storage;
    }

    public PunishmentStorage getStorage() {
        return storage;
    }

    public Punishment ban(UUID uuid, String name, String reason, String operator, long durationMillis) {
        PunishmentType type = durationMillis > 0 ? PunishmentType.TEMPBAN : PunishmentType.BAN;
        return issue(type, uuid, name, null, reason, operator, durationMillis);
    }

    public Punishment ipban(String ip, String name, String reason, String operator) {
        return issue(PunishmentType.IPBAN, null, name, ip, reason, operator, 0);
    }

    public Punishment mute(UUID uuid, String name, String reason, String operator, long durationMillis) {
        PunishmentType type = durationMillis > 0 ? PunishmentType.TEMPMUTE : PunishmentType.MUTE;
        return issue(type, uuid, name, null, reason, operator, durationMillis);
    }

    /**
     * Logs a kick to history and, if the target is online anywhere on the network, queues it to
     * actually be carried out (see PunishmentStorage#enqueueKick) - a kick issued from the web
     * dashboard has no live connection to reach into, so the platform plugins poll for this
     * instead.
     */
    public Punishment kick(UUID uuid, String name, String reason, String operator) {
        Punishment punishment = issue(PunishmentType.KICK, uuid, name, null, reason, operator, 0);
        storage.enqueueKick(uuid, name, reason, operator);
        return punishment;
    }

    public Punishment warn(UUID uuid, String name, String reason, String operator) {
        return issue(PunishmentType.WARN, uuid, name, null, reason, operator, 0);
    }

    private Punishment issue(PunishmentType type, UUID uuid, String name, String ip, String reason, String operator, long durationMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = durationMillis > 0 ? now + durationMillis : 0;
        Punishment punishment = new Punishment(type, uuid, name, ip, reason, operator, now, expiresAt, type.canBeActive());
        return storage.insert(punishment);
    }

    public boolean unban(UUID uuid, String operator, String reason) {
        Punishment active = storage.getActiveBan(uuid);
        return active != null && storage.remove(active.getId(), operator, reason);
    }

    public boolean unbanIp(String ip, String operator, String reason) {
        Punishment active = storage.getActiveIpBan(ip);
        return active != null && storage.remove(active.getId(), operator, reason);
    }

    public boolean unmute(UUID uuid, String operator, String reason) {
        Punishment active = storage.getActiveMute(uuid);
        return active != null && storage.remove(active.getId(), operator, reason);
    }

    /**
     * The ban currently blocking this player from joining, if any (their own UUID ban takes
     * priority, then their IP). Self-correcting: an active row that has actually expired is
     * deactivated on the spot rather than waiting for the next background sweep, so a player
     * whose tempban just lapsed is never wrongly rejected between sweeps.
     */
    public Punishment checkBan(UUID uuid, String ip) {
        Punishment ban = expireIfNeeded(storage.getActiveBan(uuid));
        if (ban != null) {
            return ban;
        }
        return ip != null ? expireIfNeeded(storage.getActiveIpBan(ip)) : null;
    }

    public Punishment checkMute(UUID uuid) {
        return expireIfNeeded(storage.getActiveMute(uuid));
    }

    private Punishment expireIfNeeded(Punishment punishment) {
        if (punishment == null) {
            return null;
        }
        if (punishment.isExpired(System.currentTimeMillis())) {
            storage.remove(punishment.getId(), "EcoBan", "expired");
            return null;
        }
        return punishment;
    }

    public List<Punishment> history(UUID uuid) {
        return storage.getHistory(uuid);
    }

    public List<Punishment> search(String query, int limit) {
        return storage.search(query, limit);
    }

    public List<Punishment> listActive(PunishmentType type, int limit) {
        return storage.listActive(type, limit);
    }

    public void deactivateExpired() {
        storage.deactivateExpired();
    }

    public List<PunishmentStorage.PendingKick> pollPendingKicks(int limit) {
        return storage.pollPendingKicks(limit);
    }

    public void markKickHandled(long id) {
        storage.markKickHandled(id);
    }
}
