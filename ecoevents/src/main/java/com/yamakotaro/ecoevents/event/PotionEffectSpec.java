package com.yamakotaro.ecoevents.event;

import org.bukkit.potion.PotionEffectType;

public record PotionEffectSpec(PotionEffectType type, int amplifier) {
}
