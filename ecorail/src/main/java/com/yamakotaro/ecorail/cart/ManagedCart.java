package com.yamakotaro.ecorail.cart;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A minecart an admin has told EcoRail to keep running forever (see /ecorail cart mark). There's
 * no route data at all - the physical rails the admin already built are the route. This just:
 * (1) force-loads a small square of chunks around the cart's live position so vanilla rail
 * physics keeps it moving with nobody nearby (see ChunkForceLoadTask), and (2) pauses it for a
 * configured number of seconds whenever it reaches a marked stop point, then relaunches it in
 * whichever direction it was already heading.
 *
 * <p>forwardDirX/Z is continuously refreshed to the cart's last significant direction of travel
 * (not fixed at creation) - it's both the anti-reverse reference and the relaunch direction after
 * a dwell stop.
 *
 * <p>lastHandledStopKey remembers which stop point this cart most recently dwelled at, so it
 * doesn't instantly re-trigger the same stop the moment it relaunches while still inside its
 * radius; it's cleared once the cart moves away, so the same stop can trigger again next lap.
 *
 * <p>heldChunks is the set of packed chunk coordinates (see ChunkForceLoadTask#packChunk) this
 * cart currently holds a plugin chunk ticket for - tracked per-cart so exactly the chunks it no
 * longer needs get released as it moves, instead of leaking forced-loaded chunks along the line.
 */
public class ManagedCart {

    private final UUID entityId;
    private final String world;
    private int forwardDirX;
    private int forwardDirZ;
    private long dwellUntilMillis;
    private String lastHandledStopKey;
    private int lastChunkX;
    private int lastChunkZ;
    private long lastMovedAt = System.currentTimeMillis();
    private int missCount;
    private final Set<Long> heldChunks = new HashSet<>();

    public ManagedCart(UUID entityId, String world, int lastChunkX, int lastChunkZ, int forwardDirX, int forwardDirZ) {
        this.entityId = entityId;
        this.world = world;
        this.lastChunkX = lastChunkX;
        this.lastChunkZ = lastChunkZ;
        this.forwardDirX = forwardDirX;
        this.forwardDirZ = forwardDirZ;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getWorld() {
        return world;
    }

    public int getForwardDirX() {
        return forwardDirX;
    }

    public int getForwardDirZ() {
        return forwardDirZ;
    }

    public void setForwardDirection(int forwardDirX, int forwardDirZ) {
        this.forwardDirX = forwardDirX;
        this.forwardDirZ = forwardDirZ;
    }

    public long getDwellUntilMillis() {
        return dwellUntilMillis;
    }

    public void setDwellUntilMillis(long dwellUntilMillis) {
        this.dwellUntilMillis = dwellUntilMillis;
    }

    public boolean isDwelling(long now) {
        return dwellUntilMillis > now;
    }

    public String getLastHandledStopKey() {
        return lastHandledStopKey;
    }

    public void setLastHandledStopKey(String lastHandledStopKey) {
        this.lastHandledStopKey = lastHandledStopKey;
    }

    public int getLastChunkX() {
        return lastChunkX;
    }

    public int getLastChunkZ() {
        return lastChunkZ;
    }

    public void setLastChunk(int x, int z) {
        this.lastChunkX = x;
        this.lastChunkZ = z;
    }

    public long getLastMovedAt() {
        return lastMovedAt;
    }

    public void markMoved() {
        this.lastMovedAt = System.currentTimeMillis();
    }

    public int getMissCount() {
        return missCount;
    }

    public void incrementMissCount() {
        missCount++;
    }

    public void resetMissCount() {
        missCount = 0;
    }

    public Set<Long> getHeldChunks() {
        return heldChunks;
    }
}
