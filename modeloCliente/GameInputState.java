package com.mdominguez.ietiParkAppMobil;

public final class GameInputState {
    public float moveX;
    public float moveY;
    public boolean jumpPressed;
    public boolean jumpHeld;
    public boolean resetPressed;

    public void reset() {
        moveX = 0f;
        moveY = 0f;
        jumpPressed = false;
        jumpHeld = false;
        resetPressed = false;
    }
}
