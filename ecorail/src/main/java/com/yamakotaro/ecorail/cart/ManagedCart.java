package com.yamakotaro.ecorail.cart;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Every minecart on the server is managed the moment it exists (see CartAutoManageListener) -
 * there's no opt-in step. This: (1) force-loads a small square of chunks around the cart's live
 * position so vanilla rail physics keeps it moving with nobody nearby (see ChunkForceLoadTask),
 * (2) pauses it for a configured number of seconds whenever it reaches a marked stop point, then
 * relaunches it in whichever direction it was already heading, and (3) never lets it sit stopped
 * or run backward outside of an intentional dwell - see ChunkForceLoadTask's per-tick correction.
 *
 * <p>forwardDirX/Z is continuously refreshed to the cart's last significant direction of travel
 * (not fixed at creation) - it's both the direction ChunkForceLoadTask holds the cart to and the
 * relaunch direction after a dwell stop. It starts at (0, 0) - a cart that's never actually moved
 * (a purely decorative one nobody has pushed) is left alone rather than forced into motion.
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
    private final UUID ownerId;
    private int forwardDirX;
    private int forwardDirZ;
    private long dwellUntilMillis;
    private String lastHandledStopKey;
    private int lastBlockX;
    private int lastBlockZ;
    private int missCount;
    private final Set<Long> heldChunks = new HashSet<>();

    /** ownerId is null for a cart EcoRail discovered rather than saw placed (pre-existing, or from a chunk load) - it's then only breakable by ecorail.admin. */
    public ManagedCart(UUID entityId, String world, int lastBlockX, int lastBlockZ, int forwardDirX, int forwardDirZ, UUID ownerId) {
        this.entityId = entityId;
        this.world = world;
        this.lastBlockX = lastBlockX;
        this.lastBlockZ = lastBlockZ;
        this.forwardDirX = forwardDirX;
        this.forwardDirZ = forwardDirZ;
        this.ownerId = ownerId;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getOwnerId() {
        return ownerId;
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

    public int getLastBlockX() {
        return lastBlockX;
    }

    public int getLastBlockZ() {
        return lastBlockZ;
    }

    public void setLastBlock(int x, int z) {
        this.lastBlockX = x;
        this.lastBlockZ = z;
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
