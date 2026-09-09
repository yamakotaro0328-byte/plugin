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

    /** A single punishment by its row id - the web dashboard's permalink/detail view. */
    Punishment getById(long id);

    List<Punishment> getHistory(UUID uuid);

    /**
     * Matches by player name or IP address (substring, case-insensitive) - used by the web
     * dashboard's search box and /history when given a name instead of knowing the UUID.
     */
    List<Punishment> search(String query, int limit);

    /** Same match as {@link #search(String, int)}, one page at a time - see {@link #countSearch(String)} for the total. */
    List<Punishment> search(String query, int limit, int offset);

    int countSearch(String query);

    /**
     * @param type null to list every still-active punishment regardless of type.
     */
    List<Punishment> listActive(PunishmentType type, int limit);

    /**
     * One page of punishments, newest first.
     *
     * @param type       null to include every type.
     * @param activeOnly false also includes expired/lifted/kick/warn rows - the dashboard's
     *                   "include history" toggle.
     */
    List<Punishment> list(PunishmentType type, boolean activeOnly, int limit, int offset);

    /**
     * Same as {@link #list(PunishmentType, boolean, int, int)}, but lets the caller pick the sort
     * column - the dashboard's sortable "Issued"/"Expires" table headers.
     *
     * @param sortColumn "created_at" or "expires_at"; anything else falls back to id-descending.
     */
    List<Punishment> list(PunishmentType type, boolean activeOnly, int limit, int offset, String sortColumn, boolean ascending);

    /** @see #list(PunishmentType, boolean, int, int) - same filter, just a row count instead of the rows. */
    int count(PunishmentType type, boolean activeOnly);

    /** Punishments issued per calendar day over the last {@code days} days (including today), oldest first - powers the dashboard's activity chart. */
    List<DailyCount> dailyCounts(int days);

    record DailyCount(long dayStartMillis, int count) {
    }

    /** Staff leaderboard: operators ranked by how many punishments they've issued since sinceMillis (0 = all time), most first. */
    List<OperatorCount> topOperators(long sinceMillis, int limit);

    record OperatorCount(String operatorName, int count) {
    }

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

    /**
     * Free-text staff notes on a player ("known alt of X", "watch for griefing") - separate from
     * the punishment log because a note isn't an action taken against the player, just intel for
     * other staff. Unlike punishment records, notes are never shown to the public dashboard view.
     */
    record PlayerNote(long id, UUID targetUuid, String authorName, String text, long createdAt) {
    }

    List<PlayerNote> listNotes(UUID targetUuid);

    PlayerNote addNote(UUID targetUuid, String authorName, String text);

    boolean deleteNote(long id);

    void close();
}
