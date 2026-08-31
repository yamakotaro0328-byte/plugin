package com.yamakotaro.ecojobs.storage;

import com.yamakotaro.ecojobs.PlayerJobData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persists every player's job progress. config.yml's storage.type picks between
 * {@link YamlJobStorage} (default, no setup needed) and {@link MySqlJobStorage} (for sharing
 * progress across multiple servers).
 */
public interface JobStorage {

    Map<UUID, PlayerJobData> loadAll();

    /**
     * Persists the given data. dirtyUuids is a hint for storages that can cheaply save only
     * changed players (MySQL); a storage that always rewrites everything at once (YAML) can
     * ignore it.
     */
    void saveAll(Map<UUID, PlayerJobData> allData, Set<UUID> dirtyUuids);

    /**
     * Called on plugin disable. Closes any held resources (DB connections, ...).
     */
    void close();
}
