package com.yamakotaro.ecoban.core;

/**
 * The kinds of punishment EcoBan tracks. Every backend server and the Velocity proxy share this
 * exact set - it's the vocabulary the storage layer, the migration importers, and the web
 * dashboard all speak.
 */
public enum PunishmentType {
    BAN,
    TEMPBAN,
    IPBAN,
    MUTE,
    TEMPMUTE,
    KICK,
    WARN;

    public boolean isBan() {
        return this == BAN || this == TEMPBAN || this == IPBAN;
    }

    public boolean isMute() {
        return this == MUTE || this == TEMPMUTE;
    }

    public boolean isIpBased() {
        return this == IPBAN;
    }

    /**
     * Kicks and warnings are logged for history but there's nothing ongoing to enforce - a kick
     * is instantaneous, and a warning alone never blocks anything.
     */
    public boolean canBeActive() {
        return this != KICK && this != WARN;
    }
}
