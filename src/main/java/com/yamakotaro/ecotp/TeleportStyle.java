package com.yamakotaro.ecotp;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * テレポートの演出の種類。待機中の螺旋 (spiral / sparkle)、終わり際と到着時のリング (ring)、
 * 到着時の効果音 (sound) を決める。DEFAULT 以外は /tpeffect で購入して選ぶ。
 */
public enum TeleportStyle {
    DEFAULT(Material.ENDER_PEARL, Particle.ENCHANT, Particle.PORTAL, Particle.END_ROD, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 3, 0),
    FLAME(Material.BLAZE_POWDER, Particle.FLAME, Particle.SMALL_FLAME, Particle.FLAME, Sound.ITEM_FIRECHARGE_USE, 2, 5000),
    CHERRY(Material.CHERRY_SAPLING, Particle.CHERRY_LEAVES, Particle.CHERRY_LEAVES, Particle.CHERRY_LEAVES, Sound.BLOCK_CHERRY_LEAVES_BREAK, 2, 5000),
    LIGHTNING(Material.LIGHTNING_ROD, Particle.ELECTRIC_SPARK, Particle.WAX_OFF, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3, 10000),
    HEART(Material.PINK_DYE, Particle.HEART, Particle.HAPPY_VILLAGER, Particle.HEART, Sound.ENTITY_PLAYER_LEVELUP, 1, 3000),
    SOUL(Material.SOUL_LANTERN, Particle.SOUL_FIRE_FLAME, Particle.SOUL, Particle.SOUL_FIRE_FLAME, Sound.PARTICLE_SOUL_ESCAPE, 2, 8000),
    SNOW(Material.SNOWBALL, Particle.SNOWFLAKE, Particle.SNOWFLAKE, Particle.SNOWFLAKE, Sound.BLOCK_POWDER_SNOW_PLACE, 3, 3000);

    final Material icon;
    final Particle spiral;
    final Particle sparkle;
    final Particle ring;
    final Sound sound;
    final int density;
    final double defaultPrice;

    TeleportStyle(Material icon, Particle spiral, Particle sparkle, Particle ring, Sound sound, int density, double defaultPrice) {
        this.icon = icon;
        this.spiral = spiral;
        this.sparkle = sparkle;
        this.ring = ring;
        this.sound = sound;
        this.density = density;
        this.defaultPrice = defaultPrice;
    }

    public Material getIcon() {
        return icon;
    }

    /** @return config.yml / messages.yml で使う名前 (小文字)。 */
    public String key() {
        return name().toLowerCase();
    }

    public static TeleportStyle parse(String text) {
        if (text == null) {
            return null;
        }
        for (TeleportStyle style : values()) {
            if (style.key().equalsIgnoreCase(text)) {
                return style;
            }
        }
        return null;
    }
}
