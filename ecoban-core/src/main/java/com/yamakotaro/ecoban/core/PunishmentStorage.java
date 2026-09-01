package com.yamakotaro.ecoban.core;

import java.util.List;
import java.util.UUID;

/**
 * Persists punishments. config.yml's storage.type picks between {@link SqlitePunishmentStorage}
 * (default, zero setup, single process only) and {@link MySqlPunishmentStorage} (required if
 * EcoBan runs on a Velocity proxy alongside backend servers, since SQLite can't be shared between
 * processes - the proxy and every backend server must point at the same MySQL database).
 */
public interface PunishmentStorage {

    Punishment insert(Punishment punishment);

    /**
     * Marks a punishment inactive (lifting a ban/mute early, or the system clearing an expired
     * one). The row itself is kept forever for history.
     *
     * @return true if a matching active punishment was found and deactivated.
     */
    boolean remove(long id, String removedByName, String removedReason);

    Punishment getActiveBan(UUID uuid);

    Punishment getActiveIpBan(String ip);

    Punishment getActiveMute(UUID uuid);

    List<Punishment> getHistory(UUID uuid);

    /**
     * Matches by player name or IP address (substring, case-insensitive) - used by the web
     * dashboard's search box and /history when given a name instead of knowing the UUID.
     */
    List<Punishment> search(String query, int limit);

    /**
     * @param type null to list every still-active punishment regardless of type.
     */
    List<Punishment> listActive(PunishmentType type, int limit);

    /**
     * Sweeps every active, non-permanent punishment whose expiry has passed and marks it
     * inactive. Punishment checks (see PunishmentManager) also self-correct on the spot when they
     * happen to find an expired one first, so this is background hygiene rather than a
     * correctness requirement.
     */
    void deactivateExpired();

    /**
     * Queues a kick for a player who may or may not currently be online anywhere on the network -
     * the only punishment type that needs a live action rather than a future login/chat check, so
     * the platform plugins poll this queue (see {@link #pollPendingKicks(int)}) instead of the
     * web dashboard reaching into a running server directly.
     */
    void enqueueKick(UUID targetUuid, String targetName, String reason, String operatorName);

    record PendingKick(long id, UUID targetUuid, String targetName, String reason, String operatorName) {
    }

    List<PendingKick> pollPendingKicks(int limit);

    void markKickHandled(long id);

    void close();
}
