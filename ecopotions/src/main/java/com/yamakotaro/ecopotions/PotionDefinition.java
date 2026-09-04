package com.yamakotaro.ecopotions;

import org.bukkit.Color;

import java.util.List;

/** config.yml の potions 以下の1エントリ。 */
public record PotionDefinition(
        String id,
        String displayName,
        Color color,
        double price,
        int durationSeconds,
        List<EffectSpec> effects
) {
}
