package com.yamakotaro.ecoboss.boss;

import org.bukkit.entity.EntityType;

import java.time.DayOfWeek;
import java.util.List;

/** Static, config-loaded definition of one boss. Where it spawns is stored separately (BossLocationManager). */
public record BossDefinition(String id, String displayName, EntityType entityType, BossType type,
                              int healthBoostAmplifier, int strengthAmplifier, int cooldownMinutes,
                              int worldIntervalMinutes, DayOfWeek eventDayOfWeek, int eventHour,
                              List<Phase> phases, List<Reward> loot) {
}
