package com.yamakotaro.sulfursoccer.gimmick;

/**
 * The five field gimmicks a wand-selected region can be tagged with (see GimmickManager). BUMP and
 * WIND and WARP are dynamic - GimmickTask drives them every tick. ICE and BOUNCE are static - they
 * just place a special floor once (see GimmickBuilder) and let vanilla's own block physics (ice is
 * slippery, slime bounces) do the rest, the same lesson learned from the ball itself needing no
 * custom physics code once it's standing on the right material.
 */
public enum GimmickType {
    BUMP,
    WIND,
    ICE,
    BOUNCE,
    WARP
}
