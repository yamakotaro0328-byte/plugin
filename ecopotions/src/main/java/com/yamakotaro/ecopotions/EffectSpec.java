package com.yamakotaro.ecopotions;

import org.bukkit.potion.PotionEffectType;

/** ポーション1本に詰める効果1つ。amplifier 0 = レベルI、1 = レベルII…。 */
public record EffectSpec(PotionEffectType type, int amplifier) {
}
