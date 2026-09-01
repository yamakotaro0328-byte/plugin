package com.yamakotaro.sulfursoccer.arena;

public record Point(int x, int y, int z) {

    public double centerX() {
        return x + 0.5;
    }

    public double centerZ() {
        return z + 0.5;
    }
}
