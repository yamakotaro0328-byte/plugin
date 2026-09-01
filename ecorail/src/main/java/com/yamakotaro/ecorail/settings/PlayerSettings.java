package com.yamakotaro.ecorail.settings;

/**
 * antiReverse: correct a managed cart back to its launch direction if it starts rolling
 * backward. playerCollision: whether this player's carts push players standing in their path
 * (vanilla behavior) or pass through them.
 */
public record PlayerSettings(boolean antiReverse, boolean playerCollision) {
}
