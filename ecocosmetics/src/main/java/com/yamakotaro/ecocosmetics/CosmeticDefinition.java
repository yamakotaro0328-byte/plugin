package com.yamakotaro.ecocosmetics;

import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * config.yml の cosmetics 以下の1エントリ。particle は PARTICLE/JOIN_EFFECT のみ、
 * prefix は TITLE のみ使う (もう一方は null)。
 */
public record CosmeticDefinition(
        String id,
        Category category,
        String displayName,
        Material icon,
        double price,
        Particle particle,
        String prefix
) {
}
