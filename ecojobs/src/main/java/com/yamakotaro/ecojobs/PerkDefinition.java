package com.yamakotaro.ecojobs;

/**
 * One job perk (config.yml's jobs.&lt;job&gt;.perks, or explorer's own perks list) - unlocked at an
 * "effective level" (see {@link PerkManager#effectiveLevel}), not a raw level, so prestiging a
 * job never takes a perk away.
 *
 * <p>{@code value}'s meaning depends on {@code type}:
 * <ul>
 *   <li>{@link #PAY_BONUS} - extra pay, as a percentage (5 = +5%).
 *   <li>{@link #POTION} - the effect's amplifier (1 = level I); {@code effect} names the
 *       {@code PotionEffectType} (e.g. {@code FAST_DIGGING}).
 *   <li>{@link #DOUBLE_DROP} - chance (0-100) that a mined/chopped/harvested block's drop doubles.
 *   <li>{@link #AUTO_SMELT} - unused; ore blocks drop already smelted per config.yml's
 *       top-level {@code auto-smelt-map}.
 *   <li>{@link #XP_ORB_BONUS} - flat extra vanilla xp (not job xp) granted per action.
 * </ul>
 */
public record PerkDefinition(int level, String type, double value, String effect) {

    public static final String PAY_BONUS = "pay-bonus";
    public static final String POTION = "potion";
    public static final String DOUBLE_DROP = "double-drop";
    public static final String AUTO_SMELT = "auto-smelt";
    public static final String XP_ORB_BONUS = "xp-orb-bonus";
}
