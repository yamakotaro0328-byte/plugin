package com.yamakotaro.ecoevents.event;

import org.bukkit.Material;

/** One weighted entry in a SKYFALL_LOOT event's drop table. */
public record LootDrop(Material material, int amount, String name, int weight) {
}
