package com.yamakotaro.ecorail.cart;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks one minecart spawned from a boarding sign, between the moment it departs and the
 * moment it either arrives at its destination or is given up on. heldChunks is the set of
 * packed chunk coordinates (see ChunkForceLoadTask#packChunk) this cart currently holds a
 * plugin chunk ticket for - tracked per-cart so exactly the chunks it no longer needs get
 * released as it moves, instead of leaking forced-loaded chunks along the whole line.
 */
public class ManagedCart {

    private final UUID entityId;
    private final String world;
    private final String destinationStationId;
    private final UUID ownerId;
    private final int forwardDirX;
    private final int forwardDirZ;
    private int lastChunkX;
    private int lastChunkZ;
    private long lastMovedAt = System.currentTimeMillis();
    private int missCount;
    private final Set<Long> heldChunks = new HashSet<>();

    public ManagedCart(UUID entityId, String world, int lastChunkX, int lastChunkZ, String destinationStationId,
                        UUID ownerId, int forwardDirX, int forwardDirZ) {
        this.entityId = entityId;
        this.world = world;
        this.lastChunkX = lastChunkX;
        this.lastChunkZ = lastChunkZ;
        this.destinationStationId = destinationStationId;
        this.ownerId = ownerId;
        this.forwardDirX = forwardDirX;
        this.forwardDirZ = forwardDirZ;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getForwardDirX() {
        return forwardDirX;
    }

    public int getForwardDirZ() {
        return forwardDirZ;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getWorld() {
        return world;
    }

    public String getDestinationStationId() {
        return destinationStationId;
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
