package com.yamakotaro.ecoevents.event;

/**
 * One of the underlying behaviors an event can use. Many of the 30 configured events share the
 * same mechanic with different names/magnitudes/effects - e.g. "meteor_strike" and "rogue_comet"
 * both use METEOR_STRIKE, just tuned differently.
 */
public enum Mechanic {
    METEOR_STRIKE,
    METEOR_SHOWER,
    THUNDERSTORM,
    LIGHTNING_BARRAGE,
    BLIZZARD,
    TYPHOON,
    SANDSTORM,
    EARTHQUAKE,
    PLAGUE,
    BLESSING,
    SKYFALL_LOOT,
    VOID_RIFT,
    WILDFIRE,
    FROST_WAVE,
    AURORA,
    ECLIPSE
}
