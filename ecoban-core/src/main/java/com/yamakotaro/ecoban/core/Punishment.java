package com.yamakotaro.ecoban.core;

import java.util.UUID;

/**
 * One punishment record - a ban, mute, kick, or warning, either still in force or historical.
 * Punishments are never deleted once lifted or expired, only marked inactive, so /history and the
 * web dashboard always show the full record.
 */
public class Punishment {

    private long id = -1;
    private final PunishmentType type;
    private final UUID targetUuid;
    private final String targetName;
    private final String ip;
    private final String reason;
    private final String operatorName;
    private final long createdAt;
    private final long expiresAt;
    private boolean active;
    private String removedByName;
    private String removedReason;
    private long removedAt;

    public Punishment(PunishmentType type, UUID targetUuid, String targetName, String ip, String reason,
                       String operatorName, long createdAt, long expiresAt, boolean active) {
        this.type = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.ip = ip;
        this.reason = reason;
        this.operatorName = operatorName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public PunishmentType getType() {
        return type;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getIp() {
        return ip;
    }

    public String getReason() {
        return reason;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * @return epoch millis this punishment expires at, or 0 for permanent.
     */
    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isPermanent() {
        return expiresAt <= 0;
    }

    public boolean isExpired(long now) {
        return !isPermanent() && now >= expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getRemovedByName() {
        return removedByName;
    }

    public void setRemovedByName(String removedByName) {
        this.removedByName = removedByName;
    }

    public String getRemovedReason() {
        return removedReason;
    }

    public void setRemovedReason(String removedReason) {
        this.removedReason = removedReason;
    }

    public long getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(long removedAt) {
        this.removedAt = removedAt;
    }
}
