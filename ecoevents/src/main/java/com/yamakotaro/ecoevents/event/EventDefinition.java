package com.yamakotaro.ecoevents.event;

import java.util.List;

/** Static, config-loaded definition of one environmental event. */
public record EventDefinition(String id, String displayName, Mechanic mechanic, int weight, double radius,
                               int durationSeconds, double magnitude, List<PotionEffectSpec> effects,
                               List<LootDrop> loot) {
}
