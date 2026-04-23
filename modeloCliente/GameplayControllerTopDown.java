package com.mdominguez.ietiParkAppMobil;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectSet;

public final class GameplayControllerTopDown extends GameplayControllerBase {

    private static final float MOVE_SPEED_PER_SECOND = 95f;
    private static final float DIAGONAL_NORMALIZE = 0.70710677f;

    private final IntArray blockedZoneIndices = new IntArray();
    private final IntArray arbreZoneIndices = new IntArray();
    private final IntArray futureBridgeZoneIndices = new IntArray();
    private final ObjectSet<String> collectibleArbreTileKeys = new ObjectSet<>();
    private final ObjectSet<String> collectedArbreTileKeys = new ObjectSet<>();
    private final Rectangle tileRectCache = new Rectangle();
    private final GameInputState inputState;

    private final int decorationsLayerIndex;
    private final int hiddenBridgeLayerIndex;
    private boolean wasInsideFutureBridgeZone = false;
    private Direction direction = Direction.DOWN;
    private boolean moving = false;

    public GameplayControllerTopDown(
        LevelData levelData,
        Array<LevelRenderer.SpriteRuntimeState> spriteRuntimeStates,
        boolean[] layerVisibilityStates,
        Array<RuntimeTransform> zoneRuntimeStates,
        Array<RuntimeTransform> zonePreviousRuntimeStates,
        GameInputState inputState
    ) {
        super(levelData, spriteRuntimeStates, layerVisibilityStates, zoneRuntimeStates, zonePreviousRuntimeStates);
        this.inputState = inputState;

        decorationsLayerIndex = findLayerIndexByName("decoracions", "decorations");
        hiddenBridgeLayerIndex = findLayerIndexByName("pont amagat", "hidden bridge");

        classifyZones();
        buildCollectibleArbreTiles();
        updatePlayerAnimationSelection();
        syncPlayerToSpriteRuntime();
    }

    public int getCollectedArbresCount() {
        return collectedArbreTileKeys.size;
    }

    public int getTotalArbresCount() {
        return collectibleArbreTileKeys.size;
    }

    public boolean isWin() {
        return collectibleArbreTileKeys.size > 0 && collectedArbreTileKeys.size >= collectibleArbreTileKeys.size;
    }

    @Override
    public void handleInput() {
        if (inputState.resetPressed) {
            resetPlayerToSpawn();
        }
    }

    @Override
    public void fixedUpdate(float dtSeconds) {
        if (!hasPlayer()) {
            return;
        }

        float inputX = inputState.moveX;
        float inputY = inputState.moveY;
        boolean left = inputX < -0.12f;
        boolean right = inputX > 0.12f;
        boolean up = inputY < -0.12f;
        boolean down = inputY > 0.12f;

        if (inputX != 0f && inputY != 0f) {
            float length = (float) Math.sqrt(inputX * inputX + inputY * inputY);
            if (length > 1f) {
                inputX /= length;
                inputY /= length;
            }
        }

        float dx = inputX * MOVE_SPEED_PER_SECOND * dtSeconds;
        float dy = inputY * MOVE_SPEED_PER_SECOND * dtSeconds;
        updateDirection(up, down, left, right);

        if (dx != 0f) {
            float nextX = playerX + dx;
            if (!wouldCollideBlocked(nextX, playerY)) {
                playerX = nextX;
            }
        }
        if (dy != 0f) {
            float nextY = playerY + dy;
            if (!wouldCollideBlocked(playerX, nextY)) {
                playerY = nextY;
            }
        }

        moving = left || right || up || down;
        updatePlayerAnimationSelection();

        revealHiddenBridgeIfNeeded();
        collectArbreTileIfNeeded();
        syncPlayerToSpriteRuntime();
    }

    @Override
    protected void resetPlayerToSpawn() {
        super.resetPlayerToSpawn();
        wasInsideFutureBridgeZone = false;
        direction = Direction.DOWN;
        moving = false;
        setPlayerFlip(false, false);
        updatePlayerAnimationSelection();
    }

    private void classifyZones() {
        blockedZoneIndices.clear();
        arbreZoneIndices.clear();
        futureBridgeZoneIndices.clear();

        for (int i = 0; i < levelData.zones.size; i++) {
            LevelData.LevelZone zone = levelData.zones.get(i);
            String type = normalize(zone.type);
            String name = normalize(zone.name);
            String gameplayData = normalize(zone.gameplayData);
            boolean isWall = containsAny(type, "mur", "wall") || containsAny(name, "mur", "wall");
            boolean isWater = containsAny(type, "aigua", "water") || containsAny(name, "aigua", "water");
            boolean isBridge = containsAny(type, "pont", "bridge") || containsAny(name, "pont", "bridge");
            boolean isTemporary = containsAny(type, "temporal")
                || containsAny(name, "temporal")
                || "futur pont".equals(gameplayData)
                || "future bridge".equals(gameplayData);

            if (isWall || (isWater && !isBridge && !isTemporary)) {
                blockedZoneIndices.add(i);
            }
            if (containsAny(type, "arbre") || containsAny(name, "arbre", "tree")) {
                arbreZoneIndices.add(i);
            }
            if ("futur pont".equals(gameplayData) || "future bridge".equals(gameplayData)) {
                futureBridgeZoneIndices.add(i);
            }
        }
    }

    private boolean wouldCollideBlocked(float nextX, float nextY) {
        return spriteOverlapsAnyZoneByHitBoxes(playerSpriteIndex, nextX, nextY, blockedZoneIndices);
    }

    private int findLayerIndexByName(String... tokens) {
        for (int i = 0; i < levelData.layers.size; i++) {
            String layerName = normalize(levelData.layers.get(i).name);
            if (containsAny(layerName, tokens)) {
                return i;
            }
        }
        return -1;
    }

    private void revealHiddenBridgeIfNeeded() {
        if (hiddenBridgeLayerIndex < 0
            || layerVisibilityStates == null
            || hiddenBridgeLayerIndex >= layerVisibilityStates.length
            || futureBridgeZoneIndices.size <= 0) {
            return;
        }

        boolean insideFutureBridge =
            spriteOverlapsAnyZoneByHitBoxes(playerSpriteIndex, playerX, playerY, futureBridgeZoneIndices);
        if (insideFutureBridge && !wasInsideFutureBridgeZone) {
            layerVisibilityStates[hiddenBridgeLayerIndex] = true;
        }
        wasInsideFutureBridgeZone = insideFutureBridge;
    }

    private void buildCollectibleArbreTiles() {
        collectibleArbreTileKeys.clear();
        if (decorationsLayerIndex < 0 || decorationsLayerIndex >= levelData.layers.size || arbreZoneIndices.size <= 0) {
            return;
        }

        LevelData.LevelLayer layer = levelData.layers.get(decorationsLayerIndex);
        if (layer.tileMap == null || layer.tileMap.length == 0 || layer.tileWidth <= 0 || layer.tileHeight <= 0) {
            return;
        }

        for (int tileY = 0; tileY < layer.tileMap.length; tileY++) {
            int[] row = layer.tileMap[tileY];
            for (int tileX = 0; tileX < row.length; tileX++) {
                if (row[tileX] < 0) {
                    continue;
                }
                tileRectCache.set(
                    layer.x + tileX * layer.tileWidth,
                    layer.y + tileY * layer.tileHeight,
                    layer.tileWidth,
                    layer.tileHeight
                );
                if (overlapsAnyZone(tileRectCache, arbreZoneIndices)) {
                    collectibleArbreTileKeys.add(tileKey(tileX, tileY));
                }
            }
        }
    }

    private void collectArbreTileIfNeeded() {
        if (decorationsLayerIndex < 0 || decorationsLayerIndex >= levelData.layers.size || arbreZoneIndices.size <= 0) {
            return;
        }
        if (!spriteOverlapsAnyZoneByHitBoxes(playerSpriteIndex, playerX, playerY, arbreZoneIndices)) {
            return;
        }

        LevelData.LevelLayer layer = levelData.layers.get(decorationsLayerIndex);
        if (layer.tileMap == null || layer.tileMap.length == 0 || layer.tileWidth <= 0 || layer.tileHeight <= 0) {
            return;
        }

        int tileX = MathUtils.floor((playerX - layer.x) / layer.tileWidth);
        int tileY = MathUtils.floor((playerY - layer.y) / layer.tileHeight);
        if (tileY < 0 || tileY >= layer.tileMap.length) {
            return;
        }
        int[] row = layer.tileMap[tileY];
        if (tileX < 0 || tileX >= row.length) {
            return;
        }
        if (row[tileX] < 0) {
            return;
        }

        String key = tileKey(tileX, tileY);
        if (!collectibleArbreTileKeys.contains(key) || collectedArbreTileKeys.contains(key)) {
            return;
        }

        row[tileX] = -1;
        collectedArbreTileKeys.add(key);
    }

    private String tileKey(int x, int y) {
        return x + ":" + y;
    }

    private void updateDirection(boolean up, boolean down, boolean left, boolean right) {
        if (up && left) {
            direction = Direction.UP_LEFT;
        } else if (up && right) {
            direction = Direction.UP_RIGHT;
        } else if (down && left) {
            direction = Direction.DOWN_LEFT;
        } else if (down && right) {
            direction = Direction.DOWN_RIGHT;
        } else if (up) {
            direction = Direction.UP;
        } else if (down) {
            direction = Direction.DOWN;
        } else if (left) {
            direction = Direction.LEFT;
        } else if (right) {
            direction = Direction.RIGHT;
        }
    }

    private void updatePlayerAnimationSelection() {
        if (!hasPlayer()) {
            return;
        }

        String prefix = moving ? "Heroi Camina " : "Heroi Aturat ";
        String suffix;
        boolean flipX;
        switch (direction) {
            case UP_LEFT:
                suffix = "Amunt-Dreta";
                flipX = true;
                break;
            case UP:
                suffix = "Amunt";
                flipX = false;
                break;
            case UP_RIGHT:
                suffix = "Amunt-Dreta";
                flipX = false;
                break;
            case LEFT:
                suffix = "Dreta";
                flipX = true;
                break;
            case RIGHT:
                suffix = "Dreta";
                flipX = false;
                break;
            case DOWN_LEFT:
                suffix = "Avall-Dreta";
                flipX = true;
                break;
            case DOWN_RIGHT:
                suffix = "Avall-Dreta";
                flipX = false;
                break;
            case DOWN:
            default:
                suffix = "Avall";
                flipX = false;
                break;
        }

        setPlayerFlip(flipX, false);
        setPlayerAnimationOverrideByName(prefix + suffix);
    }

    private enum Direction {
        UP_LEFT,
        UP,
        UP_RIGHT,
        LEFT,
        RIGHT,
        DOWN_LEFT,
        DOWN,
        DOWN_RIGHT
    }
}
