package com.mdominguez.ietiParkAndroid;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntFloatMap;
import com.badlogic.gdx.utils.IntSet;

public final class GameplayControllerPlatformer extends GameplayControllerBase {

    private static final float MOVE_SPEED_PER_SECOND = 150f;
    private static final float GRAVITY_PER_SECOND_SQ = 2088f;
    private static final float JUMP_IMPULSE_PER_SECOND = 708f;
    private static final float MAX_FALL_SPEED_PER_SECOND = 840f;
    private static final float FLOOR_SUPPORT_DELTA = 1f;
    private static final float COLLISION_EPSILON = 1.2f;
    private static final float FLOOR_WALL_LIKE_RATIO = 1.2f;
    private static final float DRAGON_STOMP_MIN_FALL_SPEED = 25f;
    private static final float DRAGON_DAMAGE_PERCENT = 20f;
    private static final float DRAGON_TOUCH_DAMAGE_INTERVAL_SECONDS = 0.5f;
    private static final float START_LIFE_PERCENT = 100f;
    private static final float DRAGON_DEATH_FALLBACK_DURATION_SECONDS = 0.7f;
    private static final String DRAGON_DEATH_ANIMATION_NAME = "Dragon Death";
    private static final float DEFAULT_ANIMATION_FPS = 8f;

    private final IntArray floorZoneIndices = new IntArray();
    private final IntArray solidZoneIndices = new IntArray();
    private final IntArray deathZoneIndices = new IntArray();
    private final IntArray gemSpriteIndices;
    private final IntArray dragonSpriteIndices;
    private final IntFloatMap dragonDeathStartSecondsBySprite = new IntFloatMap();
    private final IntArray completedDragonDeathSpriteIndices = new IntArray();
    private final IntSet collectedGemSpriteIndices = new IntSet();
    private final IntSet removedDragonSpriteIndices = new IntSet();
    private final IntSet touchingDragonSpriteIndices = new IntSet();
    private final IntSet touchingDragonNowCache = new IntSet();
    private final IntFloatMap nextDragonDamageSecondsBySprite = new IntFloatMap();
    private final IntArray expiredDragonDamageSpriteIndices = new IntArray();
    private final Rectangle previousPlayerRectCache = new Rectangle();
    private final GameInputState inputState;
    private final float dragonDeathDurationSeconds;
    private float velocityX = 0f;
    private float velocityY = 0f;
    private float lifePercent = START_LIFE_PERCENT;
    private float simulationTimeSeconds = 0f;
    private boolean onGround = false;
    private boolean gameOver = false;
    private boolean win = false;
    private boolean jumpQueued = false;
    private boolean facingRight = true;

    public GameplayControllerPlatformer(
        LevelData levelData,
        Array<LevelRenderer.SpriteRuntimeState> spriteRuntimeStates,
        boolean[] layerVisibilityStates,
        Array<RuntimeTransform> zoneRuntimeStates,
        Array<RuntimeTransform> zonePreviousRuntimeStates,
        GameInputState inputState
    ) {
        super(levelData, spriteRuntimeStates, layerVisibilityStates, zoneRuntimeStates, zonePreviousRuntimeStates);
        this.inputState = inputState;
        classifyZones();
        gemSpriteIndices = findSpriteIndicesByTypeOrName("gem");
        dragonSpriteIndices = findSpriteIndicesByTypeOrName("dragon");
        dragonDeathDurationSeconds = resolveDragonDeathDurationSeconds();
        onGround = isStandingOnFloor();
        updatePlayerAnimationSelection();
        syncPlayerToSpriteRuntime();
    }

    public int getCollectedGemsCount() {
        return collectedGemSpriteIndices.size;
    }

    public int getTotalGemsCount() {
        return gemSpriteIndices.size;
    }

    public float getLifePercent() {
        return lifePercent;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isWin() {
        return win;
    }

    @Override
    public void handleInput() {
        if (inputState.jumpPressed) {
            jumpQueued = true;
        }
    }

    @Override
    public void fixedUpdate(float dtSeconds) {
        simulationTimeSeconds += Math.max(0f, dtSeconds);
        pruneCompletedDragonDeaths();

        if (!hasPlayer()) {
            return;
        }

        if (gameOver || win) {
            velocityX = 0f;
            velocityY = 0f;
            jumpQueued = false;
            updatePlayerAnimationSelection();
            syncPlayerToSpriteRuntime();
            return;
        }

        float horizontalInput = Math.abs(inputState.moveX) < 0.12f ? 0f : inputState.moveX;
        if (horizontalInput == 0f) {
            velocityX = 0f;
        } else if (horizontalInput < 0f) {
            velocityX = horizontalInput * MOVE_SPEED_PER_SECOND;
            facingRight = false;
        } else {
            velocityX = horizontalInput * MOVE_SPEED_PER_SECOND;
            facingRight = true;
        }
        setPlayerFlip(!facingRight, false);

        applyMovingFloorCarry();

        boolean hasSupport = isStandingOnFloor();
        if (hasSupport && velocityY >= 0f) {
            velocityY = 0f;
            onGround = true;
        } else if (!hasSupport) {
            onGround = false;
        }

        if (jumpQueued && onGround) {
            velocityY = -JUMP_IMPULSE_PER_SECOND;
            onGround = false;
        }
        jumpQueued = false;

        if (!onGround || velocityY < 0f) {
            velocityY += GRAVITY_PER_SECOND_SQ * dtSeconds;
            if (velocityY > MAX_FALL_SPEED_PER_SECOND) {
                velocityY = MAX_FALL_SPEED_PER_SECOND;
            }
        }

        float previousY = playerY;
        float previousX = playerX;
        playerX += velocityX * dtSeconds;
        resolveHorizontalCollisions(previousX);

        playerY += velocityY * dtSeconds;
        boolean landed = resolveVerticalCollisions(previousY);
        boolean standingOnFloor = isStandingOnFloor();
        if ((landed || standingOnFloor) && velocityY >= 0f) {
            velocityY = 0f;
            onGround = true;
        } else {
            onGround = false;
        }

        collectTouchedGems();
        handleDragonInteractions();
        if (!gameOver && isTouchingDeathZone()) {
            triggerGameOver();
        }

        updatePlayerAnimationSelection();
        syncPlayerToSpriteRuntime();
    }

    private void classifyZones() {
        floorZoneIndices.clear();
        solidZoneIndices.clear();
        deathZoneIndices.clear();
        for (int i = 0; i < levelData.zones.size; i++) {
            LevelData.LevelZone zone = levelData.zones.get(i);
            String type = normalize(zone.type);
            String name = normalize(zone.name);
            if (containsAny(type, "death") || containsAny(name, "death")) {
                deathZoneIndices.add(i);
                continue;
            }
            boolean isFloor = containsAny(type, "floor", "platform") || containsAny(name, "floor", "platform");
            boolean isSolid = containsAny(type, "wall", "mur", "solid", "bloc", "block")
                || containsAny(name, "wall", "mur", "solid", "bloc", "block");
            boolean isWallLikeFloor = isFloor && zone.height > zone.width * FLOOR_WALL_LIKE_RATIO;

            if (isFloor) {
                floorZoneIndices.add(i);
            }
            if (isSolid || isWallLikeFloor) {
                solidZoneIndices.add(i);
            }
        }
    }

    private void resolveHorizontalCollisions(float previousX) {
        if (!hasPlayer() || solidZoneIndices.size <= 0 || Math.abs(playerX - previousX) <= 0.0001f) {
            return;
        }

        Rectangle playerRect = playerRect(rectCacheA);
        Rectangle previousRect = playerRectAt(previousX, playerY, previousPlayerRectCache);
        float leftOffset = playerX - playerRect.x;
        float rightOffset = playerRect.x + playerRect.width - playerX;

        float bestCrossDistance = Float.POSITIVE_INFINITY;
        float bestResolvedX = playerX;
        boolean collided = false;
        boolean movingRight = velocityX > 0f;

        for (int i = 0; i < solidZoneIndices.size; i++) {
            int zoneIndex = solidZoneIndices.get(i);
            Rectangle zoneRect = zoneRectAtIndex(zoneIndex, rectCacheB);
            if (!overlapsVerticallyForSweep(previousRect, playerRect, zoneRect)) {
                continue;
            }

            if (movingRight) {
                float previousRight = previousRect.x + previousRect.width;
                float currentRight = playerRect.x + playerRect.width;
                float zoneLeft = zoneRect.x;
                if (previousRight <= zoneLeft + COLLISION_EPSILON && currentRight >= zoneLeft - COLLISION_EPSILON) {
                    float crossDistance = zoneLeft - previousRight;
                    if (crossDistance < bestCrossDistance) {
                        bestCrossDistance = crossDistance;
                        bestResolvedX = zoneLeft - rightOffset;
                        collided = true;
                    }
                }
            } else {
                float previousLeft = previousRect.x;
                float currentLeft = playerRect.x;
                float zoneRight = zoneRect.x + zoneRect.width;
                if (previousLeft >= zoneRight - COLLISION_EPSILON && currentLeft <= zoneRight + COLLISION_EPSILON) {
                    float crossDistance = previousLeft - zoneRight;
                    if (crossDistance < bestCrossDistance) {
                        bestCrossDistance = crossDistance;
                        bestResolvedX = zoneRight + leftOffset;
                        collided = true;
                    }
                }
            }
        }

        if (collided) {
            playerX = bestResolvedX;
            velocityX = 0f;
        }
    }

    private boolean resolveVerticalCollisions(float previousY) {
        if (floorZoneIndices.size <= 0) {
            return false;
        }

        Rectangle playerRect = playerRect(rectCacheA);
        Rectangle previousRect = playerRectAt(playerX, previousY, previousPlayerRectCache);

        // One-way floor behavior:
        // - Collide only when moving downward.
        // - Ignore floor collisions while moving upward (jump-through from below).
        if (velocityY <= 0f) {
            return false;
        }

        float previousBottom = previousRect.y + previousRect.height;
        float currentBottom = playerRect.y + playerRect.height;
        float playerBottomOffset = currentBottom - playerY;
        float bestCrossDistance = Float.POSITIVE_INFINITY;
        float bestLandingTop = Float.NaN;

        for (int i = 0; i < floorZoneIndices.size; i++) {
            int zoneIndex = floorZoneIndices.get(i);
            Rectangle zoneRect = zoneRectAtIndex(zoneIndex, rectCacheB);
            float zoneTop = zoneRect.y;

            if (!overlapsHorizontallyForSweep(previousRect, playerRect, zoneRect)) {
                continue;
            }
            if (!crossedZoneTop(previousBottom, currentBottom, zoneTop)) {
                continue;
            }

            float crossDistance = zoneTop - previousBottom;
            if (crossDistance < bestCrossDistance) {
                bestCrossDistance = crossDistance;
                bestLandingTop = zoneTop;
            }
        }

        if (Float.isFinite(bestLandingTop)) {
            playerY = bestLandingTop - playerBottomOffset;
            velocityY = 0f;
            return true;
        }

        // Fallback: if already penetrating near a floor top, push back upward.
        float correctedY = playerY;
        boolean landed = false;
        for (int i = 0; i < 4; i++) {
            Rectangle correctedRect = playerRectAt(playerX, correctedY, rectCacheA);
            float maxPenetration = 0f;
            for (int z = 0; z < floorZoneIndices.size; z++) {
                int zoneIndex = floorZoneIndices.get(z);
                Rectangle zoneRect = zoneRectAtIndex(zoneIndex, rectCacheB);
                if (!overlapsHorizontallyForSweep(correctedRect, correctedRect, zoneRect)) {
                    continue;
                }
                float zoneTop = zoneRect.y;
                boolean crossedTop = correctedRect.y + correctedRect.height > zoneTop
                    && correctedRect.y < zoneTop + 4f;
                if (!crossedTop) {
                    continue;
                }
                float penetration = correctedRect.y + correctedRect.height - zoneTop;
                if (penetration > maxPenetration) {
                    maxPenetration = penetration;
                }
            }
            if (maxPenetration <= 0f) {
                break;
            }
            correctedY -= maxPenetration + 0.01f;
            landed = true;
        }

        if (landed) {
            playerY = correctedY;
            velocityY = 0f;
            return true;
        }
        return false;
    }

    private boolean isStandingOnFloor() {
        if (!hasPlayer() || floorZoneIndices.size <= 0) {
            return false;
        }

        Rectangle playerRect = playerRect(rectCacheA);
        float testBottom = playerRect.y + playerRect.height + 0.5f;
        float testLeft = playerRect.x;
        float testRight = playerRect.x + playerRect.width;

        for (int i = 0; i < floorZoneIndices.size; i++) {
            int zoneIndex = floorZoneIndices.get(i);
            Rectangle zoneRect = zoneRectAtIndex(zoneIndex, rectCacheB);
            boolean overlapsHorizontally = testRight > zoneRect.x && testLeft < zoneRect.x + zoneRect.width;
            if (!overlapsHorizontally) {
                continue;
            }
            float bottomDelta = Math.abs(testBottom - zoneRect.y);
            if (bottomDelta <= FLOOR_SUPPORT_DELTA) {
                return true;
            }
        }
        return false;
    }

    private boolean isTouchingDeathZone() {
        if (deathZoneIndices.size <= 0) {
            return false;
        }
        return spriteOverlapsAnyZoneByHitBoxes(playerSpriteIndex, playerX, playerY, deathZoneIndices);
    }

    private void collectTouchedGems() {
        if (gemSpriteIndices.size <= 0) {
            return;
        }

        for (int i = 0; i < gemSpriteIndices.size; i++) {
            int spriteIndex = gemSpriteIndices.get(i);
            if (collectedGemSpriteIndices.contains(spriteIndex)) {
                continue;
            }
            if (spriteIndex < 0 || spriteIndex >= spriteRuntimeStates.size) {
                continue;
            }
            LevelRenderer.SpriteRuntimeState runtime = spriteRuntimeStates.get(spriteIndex);
            if (!runtime.visible) {
                continue;
            }
            if (spritesOverlapByHitBoxes(
                playerSpriteIndex,
                playerX,
                playerY,
                spriteIndex,
                runtime.worldX,
                runtime.worldY
            )) {
                collectedGemSpriteIndices.add(spriteIndex);
                setSpriteVisible(spriteIndex, false);
            }
        }

        if (gemSpriteIndices.size > 0 && collectedGemSpriteIndices.size >= gemSpriteIndices.size) {
            triggerWin();
        }
    }

    private void handleDragonInteractions() {
        if (gameOver || dragonSpriteIndices.size <= 0) {
            return;
        }

        boolean foxyIsFalling = !onGround && velocityY > DRAGON_STOMP_MIN_FALL_SPEED;
        touchingDragonNowCache.clear();

        for (int i = 0; i < dragonSpriteIndices.size; i++) {
            int spriteIndex = dragonSpriteIndices.get(i);
            if (removedDragonSpriteIndices.contains(spriteIndex)
                || dragonDeathStartSecondsBySprite.containsKey(spriteIndex)) {
                continue;
            }
            if (spriteIndex < 0 || spriteIndex >= spriteRuntimeStates.size) {
                continue;
            }
            LevelRenderer.SpriteRuntimeState dragonRuntime = spriteRuntimeStates.get(spriteIndex);
            if (!dragonRuntime.visible) {
                continue;
            }
            if (!spritesOverlapByHitBoxes(
                playerSpriteIndex,
                playerX,
                playerY,
                spriteIndex,
                dragonRuntime.worldX,
                dragonRuntime.worldY
            )) {
                continue;
            }

            if (foxyIsFalling) {
                startDragonDeath(spriteIndex);
                velocityY = -JUMP_IMPULSE_PER_SECOND * 0.38f;
                onGround = false;
                continue;
            }

            touchingDragonNowCache.add(spriteIndex);
            float nextDamageSeconds = nextDragonDamageSecondsBySprite.get(spriteIndex, Float.NEGATIVE_INFINITY);
            if (simulationTimeSeconds >= nextDamageSeconds) {
                applyDragonDamage();
                nextDragonDamageSecondsBySprite.put(
                    spriteIndex,
                    simulationTimeSeconds + DRAGON_TOUCH_DAMAGE_INTERVAL_SECONDS
                );
                if (gameOver) {
                    break;
                }
            }
        }

        touchingDragonSpriteIndices.clear();
        IntSet.IntSetIterator iterator = touchingDragonNowCache.iterator();
        while (iterator.hasNext) {
            touchingDragonSpriteIndices.add(iterator.next());
        }

        expiredDragonDamageSpriteIndices.clear();
        IntFloatMap.Keys cooldownKeys = nextDragonDamageSecondsBySprite.keys();
        while (cooldownKeys.hasNext) {
            int spriteIndex = cooldownKeys.next();
            if (!touchingDragonNowCache.contains(spriteIndex)) {
                expiredDragonDamageSpriteIndices.add(spriteIndex);
            }
        }
        for (int i = 0; i < expiredDragonDamageSpriteIndices.size; i++) {
            int spriteIndex = expiredDragonDamageSpriteIndices.get(i);
            nextDragonDamageSecondsBySprite.remove(spriteIndex, -1f);
        }
        expiredDragonDamageSpriteIndices.clear();
    }

    private void startDragonDeath(int spriteIndex) {
        if (spriteIndex < 0 || spriteIndex >= spriteRuntimeStates.size) {
            return;
        }
        if (dragonDeathStartSecondsBySprite.containsKey(spriteIndex)) {
            return;
        }
        dragonDeathStartSecondsBySprite.put(spriteIndex, simulationTimeSeconds);
        touchingDragonSpriteIndices.remove(spriteIndex);
        nextDragonDamageSecondsBySprite.remove(spriteIndex, -1f);
        setAnimationOverrideByName(spriteIndex, DRAGON_DEATH_ANIMATION_NAME);
    }

    private void pruneCompletedDragonDeaths() {
        if (dragonDeathStartSecondsBySprite.size <= 0) {
            return;
        }

        completedDragonDeathSpriteIndices.clear();
        IntFloatMap.Keys keys = dragonDeathStartSecondsBySprite.keys();
        while (keys.hasNext) {
            int spriteIndex = keys.next();
            float startSeconds = dragonDeathStartSecondsBySprite.get(spriteIndex, simulationTimeSeconds);
            float elapsedSeconds = simulationTimeSeconds - startSeconds;
            if (elapsedSeconds >= dragonDeathDurationSeconds) {
                completedDragonDeathSpriteIndices.add(spriteIndex);
            }
        }

        for (int i = 0; i < completedDragonDeathSpriteIndices.size; i++) {
            int spriteIndex = completedDragonDeathSpriteIndices.get(i);
            dragonDeathStartSecondsBySprite.remove(spriteIndex, -1f);
            removedDragonSpriteIndices.add(spriteIndex);
            setAnimationOverrideByName(spriteIndex, null);
            setSpriteVisible(spriteIndex, false);
        }
        completedDragonDeathSpriteIndices.clear();
    }

    private float resolveDragonDeathDurationSeconds() {
        String animationId = findAnimationIdByName(DRAGON_DEATH_ANIMATION_NAME);
        if (animationId == null || animationId.isEmpty()) {
            return DRAGON_DEATH_FALLBACK_DURATION_SECONDS;
        }
        LevelData.AnimationClip clip = levelData.animationClips.get(animationId);
        if (clip == null) {
            return DRAGON_DEATH_FALLBACK_DURATION_SECONDS;
        }

        int spanFrames = Math.max(1, clip.endFrame - clip.startFrame + 1);
        float fps = Float.isFinite(clip.fps) && clip.fps > 0f ? clip.fps : DEFAULT_ANIMATION_FPS;
        float durationSeconds = spanFrames / fps;
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0f) {
            return DRAGON_DEATH_FALLBACK_DURATION_SECONDS;
        }
        return durationSeconds;
    }

    private void applyDragonDamage() {
        lifePercent -= DRAGON_DAMAGE_PERCENT;
        if (lifePercent <= 0f) {
            lifePercent = 0f;
            triggerGameOver();
        }
    }

    private void triggerGameOver() {
        gameOver = true;
        win = false;
        velocityX = 0f;
        velocityY = 0f;
        onGround = false;
        jumpQueued = false;
    }

    private void triggerWin() {
        win = true;
        gameOver = false;
        velocityX = 0f;
        velocityY = 0f;
        onGround = false;
        jumpQueued = false;
    }

    private void resetRuntimeState() {
        resetPlayerToSpawn();
        velocityX = 0f;
        velocityY = 0f;
        lifePercent = START_LIFE_PERCENT;
        simulationTimeSeconds = 0f;
        gameOver = false;
        win = false;
        jumpQueued = false;
        onGround = isStandingOnFloor();
        touchingDragonSpriteIndices.clear();
        touchingDragonNowCache.clear();
        nextDragonDamageSecondsBySprite.clear();
        expiredDragonDamageSpriteIndices.clear();
        collectedGemSpriteIndices.clear();
        removedDragonSpriteIndices.clear();
        dragonDeathStartSecondsBySprite.clear();
        completedDragonDeathSpriteIndices.clear();
        restoreSpritesVisible(gemSpriteIndices);
        restoreSpritesVisible(dragonSpriteIndices);
        clearAnimationOverrides(dragonSpriteIndices);
        setPlayerFlip(false, false);
        updatePlayerAnimationSelection();
        syncPlayerToSpriteRuntime();
    }

    private void restoreSpritesVisible(IntArray indices) {
        for (int i = 0; i < indices.size; i++) {
            setSpriteVisible(indices.get(i), true);
        }
    }

    private void clearAnimationOverrides(IntArray indices) {
        for (int i = 0; i < indices.size; i++) {
            setAnimationOverrideByName(indices.get(i), null);
        }
    }

    private void applyMovingFloorCarry() {
        if (!hasPlayer() || floorZoneIndices.size <= 0 || zoneRuntimeStates == null || zonePreviousRuntimeStates == null) {
            return;
        }

        Rectangle playerRect = playerRect(rectCacheA);
        float bestCarryMagnitudeSq = 0f;
        float carryX = 0f;
        float carryY = 0f;

        for (int i = 0; i < floorZoneIndices.size; i++) {
            int zoneIndex = floorZoneIndices.get(i);
            if (zoneIndex < 0 || zoneIndex >= zoneRuntimeStates.size || zoneIndex >= zonePreviousRuntimeStates.size) {
                continue;
            }
            RuntimeTransform current = zoneRuntimeStates.get(zoneIndex);
            RuntimeTransform previous = zonePreviousRuntimeStates.get(zoneIndex);
            float deltaX = current.x - previous.x;
            float deltaY = current.y - previous.y;
            if (Math.abs(deltaX) <= 0.0001f && Math.abs(deltaY) <= 0.0001f) {
                continue;
            }

            Rectangle previousZoneRect = zoneRectAtPreviousIndex(zoneIndex, rectCacheB);
            if (!isStandingOnFloorRect(playerRect, previousZoneRect)) {
                continue;
            }

            float magnitudeSq = deltaX * deltaX + deltaY * deltaY;
            if (magnitudeSq > bestCarryMagnitudeSq) {
                bestCarryMagnitudeSq = magnitudeSq;
                carryX = deltaX;
                carryY = deltaY;
            }
        }

        if (bestCarryMagnitudeSq > 0f) {
            playerX += carryX;
            playerY += carryY;
        }
    }

    private boolean overlapsHorizontallyForSweep(Rectangle previousRect, Rectangle currentRect, Rectangle zoneRect) {
        float sweepLeft = Math.min(previousRect.x, currentRect.x);
        float sweepRight = Math.max(previousRect.x + previousRect.width, currentRect.x + currentRect.width);
        return sweepRight > zoneRect.x + COLLISION_EPSILON
            && sweepLeft < zoneRect.x + zoneRect.width - COLLISION_EPSILON;
    }

    private boolean overlapsVerticallyForSweep(Rectangle previousRect, Rectangle currentRect, Rectangle zoneRect) {
        float sweepTop = Math.min(previousRect.y, currentRect.y);
        float sweepBottom = Math.max(previousRect.y + previousRect.height, currentRect.y + currentRect.height);
        return sweepBottom > zoneRect.y + COLLISION_EPSILON
            && sweepTop < zoneRect.y + zoneRect.height - COLLISION_EPSILON;
    }

    private boolean crossedZoneTop(float previousBottom, float currentBottom, float zoneTop) {
        return previousBottom <= zoneTop + COLLISION_EPSILON
            && currentBottom >= zoneTop - COLLISION_EPSILON;
    }

    private boolean isStandingOnFloorRect(Rectangle playerRect, Rectangle floorRect) {
        float playerBottom = playerRect.y + playerRect.height + 0.5f;
        boolean overlapsHorizontally = playerRect.x + playerRect.width > floorRect.x
            && playerRect.x < floorRect.x + floorRect.width;
        if (!overlapsHorizontally) {
            return false;
        }
        float bottomDelta = Math.abs(playerBottom - floorRect.y);
        return bottomDelta <= FLOOR_SUPPORT_DELTA;
    }

    private void updatePlayerAnimationSelection() {
        if (!hasPlayer()) {
            return;
        }

        final float verticalThreshold = 5f;
        final float moveThreshold = 2f;
        String animationName = "Foxy Idle";
        if (!onGround) {
            if (velocityY < -verticalThreshold) {
                animationName = "Foxy Jump Up";
            } else {
                animationName = "Foxy Jump Fall";
            }
        } else if (Math.abs(velocityX) > moveThreshold) {
            animationName = "Foxy Walk";
        }

        setPlayerFlip(!facingRight, false);
        setPlayerAnimationOverrideByName(animationName);
    }
}
