package com.yamakotaro.serverkit.staff;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FreezeManager {

    private final Set<UUID> frozen = new HashSet<>();

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    /** @return true if now frozen, false if it was unfrozen */
    public boolean toggle(UUID uuid) {
        if (frozen.remove(uuid)) {
            return false;
        }
        frozen.add(uuid);
        return true;
    }

    public void clear(UUID uuid) {
        frozen.remove(uuid);
    }
}
