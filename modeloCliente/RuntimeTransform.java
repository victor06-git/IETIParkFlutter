package com.mdominguez.ietiParkAppMobil;

public final class RuntimeTransform {
    public final float initialX;
    public final float initialY;
    public float x;
    public float y;

    public RuntimeTransform(float x, float y) {
        this.initialX = x;
        this.initialY = y;
        this.x = x;
        this.y = y;
    }
}
