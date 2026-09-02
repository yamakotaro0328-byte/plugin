package com.yamakotaro.ecoboss.boss;

import org.bukkit.entity.EntityType;

/**
 * A health-percent threshold that, once crossed, fires once: a message, optionally an "enrage"
 * (extra Speed+Strength on the boss), and optionally summoned adds.
 */
public record Phase(int healthPercent, String message, boolean enrage, EntityType summonType, int summonCount) {
}
