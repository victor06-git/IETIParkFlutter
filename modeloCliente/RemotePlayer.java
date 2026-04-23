package com.mdominguez.ietiParkAppMobil;

public class RemotePlayer {
    public String nickname;
    public float x, y;
    public String direction;
    public boolean flipX;
    public boolean tempFlag;
    public String cat;
    public boolean hasPosition; // false hasta recibir el primer MOVE con coordenadas

    public RemotePlayer(String nickname) {
        this.nickname = nickname;
        this.x = 0f;
        this.y = 0f;
        this.direction = "RIGHT";
        this.flipX = false;
        this.tempFlag = true;
        this.hasPosition = false;
    }
}

