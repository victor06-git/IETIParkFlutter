package com.mdominguez.ietiParkAppMobil;

public interface GameplayController {
    void handleInput();

    void fixedUpdate(float dtSeconds);

    String animationOverrideForSprite(int spriteIndex);

    boolean hasCameraTarget();

    float getCameraTargetX();

    float getCameraTargetY();
}
