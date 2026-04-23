package com.mdominguez.ietiParkAppMobil;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;

public abstract class GameplayControllerBase implements GameplayController {

    protected final LevelData levelData;
    protected final Array<LevelRenderer.SpriteRuntimeState> spriteRuntimeStates;
    protected final boolean[] layerVisibilityStates;
    protected final Array<RuntimeTransform> zoneRuntimeStates;
    protected final Array<RuntimeTransform> zonePreviousRuntimeStates;
    private final String[] animationOverrideBySpriteIndex;
    private final ObjectMap<String, String> animationIdByName = new ObjectMap<>();
    protected final Rectangle rectCacheA = new Rectangle();
    protected final Rectangle rectCacheB = new Rectangle();

    protected final int playerSpriteIndex;
    protected float spawnX;
    protected float spawnY;
    protected float playerX;
    protected float playerY;

    protected GameplayControllerBase(
        LevelData levelData,
        Array<LevelRenderer.SpriteRuntimeState> spriteRuntimeStates,
        boolean[] layerVisibilityStates,
        Array<RuntimeTransform> zoneRuntimeStates,
        Array<RuntimeTransform> zonePreviousRuntimeStates
    ) {
        this.levelData = levelData;
        this.spriteRuntimeStates = spriteRuntimeStates;
        this.layerVisibilityStates = layerVisibilityStates;
        this.zoneRuntimeStates = zoneRuntimeStates;
        this.zonePreviousRuntimeStates = zonePreviousRuntimeStates;
        this.animationOverrideBySpriteIndex = new String[levelData.sprites.size];

        for (ObjectMap.Entry<String, LevelData.AnimationClip> entry : levelData.animationClips) {
            LevelData.AnimationClip clip = entry.value;
            if (clip == null || clip.name == null || clip.name.trim().isEmpty()) {
                continue;
            }
            animationIdByName.put(normalize(clip.name), clip.id);
        }

        this.playerSpriteIndex = findPlayerSpriteIndex();
        if (hasPlayer()) {
            LevelRenderer.SpriteRuntimeState state = playerState();
            spawnX = state.worldX;
            spawnY = state.worldY;
            playerX = spawnX;
            playerY = spawnY;
        } else {
            spawnX = 0f;
            spawnY = 0f;
            playerX = 0f;
            playerY = 0f;
        }
    }

    @Override
    public final boolean hasCameraTarget() {
        return hasPlayer();
    }

    @Override
    public final float getCameraTargetX() {
        return playerX;
    }

    @Override
    public final float getCameraTargetY() {
        return playerY;
    }

    @Override
    public final String animationOverrideForSprite(int spriteIndex) {
        if (spriteIndex < 0 || spriteIndex >= animationOverrideBySpriteIndex.length) {
            return null;
        }
        return animationOverrideBySpriteIndex[spriteIndex];
    }

    protected final boolean hasPlayer() {
        return playerSpriteIndex >= 0
            && playerSpriteIndex < levelData.sprites.size
            && playerSpriteIndex < spriteRuntimeStates.size;
    }

    protected final LevelData.LevelSprite playerSprite() {
        return levelData.sprites.get(playerSpriteIndex);
    }

    protected final LevelRenderer.SpriteRuntimeState playerState() {
        return spriteRuntimeStates.get(playerSpriteIndex);
    }

    protected final void syncPlayerToSpriteRuntime() {
        if (!hasPlayer()) {
            return;
        }
        LevelRenderer.SpriteRuntimeState runtime = playerState();
        runtime.worldX = playerX;
        runtime.worldY = playerY;
    }

    protected void resetPlayerToSpawn() {
        playerX = spawnX;
        playerY = spawnY;
        syncPlayerToSpriteRuntime();
    }

    protected final void setPlayerFlip(boolean flipX, boolean flipY) {
        if (!hasPlayer()) {
            return;
        }
        LevelRenderer.SpriteRuntimeState runtime = playerState();
        runtime.flipX = flipX;
        runtime.flipY = flipY;
    }

    protected final Rectangle playerRectAt(float worldX, float worldY, Rectangle out) {
        return spriteRectAt(playerSpriteIndex, worldX, worldY, out);
    }

    protected final Rectangle playerRect(Rectangle out) {
        return playerRectAt(playerX, playerY, out);
    }

    protected final Rectangle spriteRectAt(int spriteIndex, float worldX, float worldY, Rectangle out) {
        if (spriteIndex < 0 || spriteIndex >= levelData.sprites.size || spriteIndex >= spriteRuntimeStates.size) {
            out.set(0f, 0f, 0f, 0f);
            return out;
        }

        LevelData.LevelSprite sprite = levelData.sprites.get(spriteIndex);
        LevelRenderer.SpriteRuntimeState runtime = spriteRuntimeStates.get(spriteIndex);
        Array<LevelData.HitBox> hitBoxes = activeHitBoxes(spriteIndex);
        if (hitBoxes == null || hitBoxes.size <= 0) {
            setFullSpriteRect(sprite, runtime, worldX, worldY, out);
            return out;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < hitBoxes.size; i++) {
            LevelData.HitBox hitBox = hitBoxes.get(i);
            if (hitBox == null || hitBox.width <= 0f || hitBox.height <= 0f) {
                continue;
            }
            hitBoxRectAt(sprite, runtime, worldX, worldY, hitBox, rectCacheB);
            minX = Math.min(minX, rectCacheB.x);
            minY = Math.min(minY, rectCacheB.y);
            maxX = Math.max(maxX, rectCacheB.x + rectCacheB.width);
            maxY = Math.max(maxY, rectCacheB.y + rectCacheB.height);
        }

        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) {
            setFullSpriteRect(sprite, runtime, worldX, worldY, out);
            return out;
        }

        out.set(minX, minY, maxX - minX, maxY - minY);
        return out;
    }

    protected final Rectangle spriteRectAtCurrent(int spriteIndex, Rectangle out) {
        if (spriteIndex < 0 || spriteIndex >= spriteRuntimeStates.size) {
            out.set(0f, 0f, 0f, 0f);
            return out;
        }
        LevelRenderer.SpriteRuntimeState runtime = spriteRuntimeStates.get(spriteIndex);
        return spriteRectAt(spriteIndex, runtime.worldX, runtime.worldY, out);
    }

    protected final Rectangle zoneRect(LevelData.LevelZone zone, Rectangle out) {
        int zoneIndex = levelData.zones.indexOf(zone, true);
        if (zoneIndex >= 0) {
            return zoneRectAtIndex(zoneIndex, out);
        }
        out.set(zone.x, zone.y, zone.width, zone.height);
        return out;
    }

    protected final Rectangle zoneRectAtIndex(int zoneIndex, Rectangle out) {
        if (zoneIndex < 0 || zoneIndex >= levelData.zones.size) {
            out.set(0f, 0f, 0f, 0f);
            return out;
        }
        LevelData.LevelZone zone = levelData.zones.get(zoneIndex);
        RuntimeTransform runtime = zoneRuntimeStates != null && zoneIndex < zoneRuntimeStates.size
            ? zoneRuntimeStates.get(zoneIndex)
            : null;
        float zoneX = runtime == null ? zone.x : runtime.x;
        float zoneY = runtime == null ? zone.y : runtime.y;
        out.set(zoneX, zoneY, zone.width, zone.height);
        return out;
    }

    protected final Rectangle zoneRectAtPreviousIndex(int zoneIndex, Rectangle out) {
        if (zoneIndex < 0 || zoneIndex >= levelData.zones.size) {
            out.set(0f, 0f, 0f, 0f);
            return out;
        }
        LevelData.LevelZone zone = levelData.zones.get(zoneIndex);
        RuntimeTransform runtime = zonePreviousRuntimeStates != null && zoneIndex < zonePreviousRuntimeStates.size
            ? zonePreviousRuntimeStates.get(zoneIndex)
            : null;
        float zoneX = runtime == null ? zone.x : runtime.x;
        float zoneY = runtime == null ? zone.y : runtime.y;
        out.set(zoneX, zoneY, zone.width, zone.height);
        return out;
    }

    protected final void setSpriteVisible(int spriteIndex, boolean visible) {
        if (spriteIndex < 0 || spriteIndex >= spriteRuntimeStates.size) {
            return;
        }
        spriteRuntimeStates.get(spriteIndex).visible = visible;
    }

    protected final void setPlayerAnimationOverrideByName(String animationName) {
        if (!hasPlayer()) {
            return;
        }
        setAnimationOverrideByName(playerSpriteIndex, animationName);
    }

    protected final void setAnimationOverrideByName(int spriteIndex, String animationName) {
        if (spriteIndex < 0 || spriteIndex >= animationOverrideBySpriteIndex.length) {
            return;
        }
        if (animationName == null || animationName.trim().isEmpty()) {
            animationOverrideBySpriteIndex[spriteIndex] = null;
            return;
        }
        animationOverrideBySpriteIndex[spriteIndex] = animationIdByName.get(normalize(animationName));
    }

    protected final String findAnimationIdByName(String animationName) {
        if (animationName == null || animationName.trim().isEmpty()) {
            return null;
        }
        return animationIdByName.get(normalize(animationName));
    }

    protected final IntArray findSpriteIndicesByTypeOrName(String... tokens) {
        IntArray indices = new IntArray();
        for (int i = 0; i < levelData.sprites.size; i++) {
            LevelData.LevelSprite sprite = levelData.sprites.get(i);
            String type = normalize(sprite.type);
            String name = normalize(sprite.name);
            if (containsAny(type, tokens) || containsAny(name, tokens)) {
                indices.add(i);
            }
        }
        return indices;
    }

    protected final IntArray findZoneIndicesByTypeOrName(String... tokens) {
        IntArray indices = new IntArray();
        for (int i = 0; i < levelData.zones.size; i++) {
            LevelData.LevelZone zone = levelData.zones.get(i);
            String type = normalize(zone.type);
            String name = normalize(zone.name);
            if (containsAny(type, tokens) || containsAny(name, tokens)) {
                indices.add(i);
            }
        }
        return indices;
    }

    protected final boolean overlapsAnyZone(Rectangle bounds, IntArray zoneIndices) {
        for (int i = 0; i < zoneIndices.size; i++) {
            int idx = zoneIndices.get(i);
            if (idx < 0 || idx >= levelData.zones.size) {
                continue;
            }
            if (bounds.overlaps(zoneRectAtIndex(idx, rectCacheB))) {
                return true;
            }
        }
        return false;
    }

    protected final boolean spriteOverlapsAnyZoneByHitBoxes(
        int spriteIndex,
        float worldX,
        float worldY,
        IntArray zoneIndices
    ) {
        if (zoneIndices == null || zoneIndices.size <= 0) {
            return false;
        }
        if (spriteIndex < 0 || spriteIndex >= levelData.sprites.size || spriteIndex >= spriteRuntimeStates.size) {
            return false;
        }

        LevelData.LevelSprite sprite = levelData.sprites.get(spriteIndex);
        LevelRenderer.SpriteRuntimeState runtime = spriteRuntimeStates.get(spriteIndex);
        Array<LevelData.HitBox> hitBoxes = activeHitBoxes(spriteIndex);
        if (hitBoxes == null || hitBoxes.size <= 0) {
            Rectangle spriteBounds = spriteRectAt(spriteIndex, worldX, worldY, rectCacheA);
            return overlapsAnyZone(spriteBounds, zoneIndices);
        }

        for (int h = 0; h < hitBoxes.size; h++) {
            LevelData.HitBox hitBox = hitBoxes.get(h);
            if (hitBox == null || hitBox.width <= 0f || hitBox.height <= 0f) {
                continue;
            }
            Rectangle hitBoxRect = hitBoxRectAt(sprite, runtime, worldX, worldY, hitBox, rectCacheA);
            if (overlapsAnyZone(hitBoxRect, zoneIndices)) {
                return true;
            }
        }
        return false;
    }

    protected final boolean spritesOverlapByHitBoxes(
        int firstSpriteIndex,
        float firstWorldX,
        float firstWorldY,
        int secondSpriteIndex,
        float secondWorldX,
        float secondWorldY
    ) {
        if (firstSpriteIndex < 0
            || firstSpriteIndex >= levelData.sprites.size
            || firstSpriteIndex >= spriteRuntimeStates.size
            || secondSpriteIndex < 0
            || secondSpriteIndex >= levelData.sprites.size
            || secondSpriteIndex >= spriteRuntimeStates.size) {
            return false;
        }

        LevelData.LevelSprite firstSprite = levelData.sprites.get(firstSpriteIndex);
        LevelRenderer.SpriteRuntimeState firstRuntime = spriteRuntimeStates.get(firstSpriteIndex);
        LevelData.LevelSprite secondSprite = levelData.sprites.get(secondSpriteIndex);
        LevelRenderer.SpriteRuntimeState secondRuntime = spriteRuntimeStates.get(secondSpriteIndex);
        Array<LevelData.HitBox> firstHitBoxes = activeHitBoxes(firstSpriteIndex);
        Array<LevelData.HitBox> secondHitBoxes = activeHitBoxes(secondSpriteIndex);

        if (firstHitBoxes == null || firstHitBoxes.size <= 0 || secondHitBoxes == null || secondHitBoxes.size <= 0) {
            Rectangle firstBounds = spriteRectAt(firstSpriteIndex, firstWorldX, firstWorldY, rectCacheA);
            Rectangle secondBounds = spriteRectAt(secondSpriteIndex, secondWorldX, secondWorldY, rectCacheB);
            return firstBounds.overlaps(secondBounds);
        }

        for (int i = 0; i < firstHitBoxes.size; i++) {
            LevelData.HitBox firstHitBox = firstHitBoxes.get(i);
            if (firstHitBox == null || firstHitBox.width <= 0f || firstHitBox.height <= 0f) {
                continue;
            }
            Rectangle firstRect = hitBoxRectAt(
                firstSprite,
                firstRuntime,
                firstWorldX,
                firstWorldY,
                firstHitBox,
                rectCacheA
            );

            for (int j = 0; j < secondHitBoxes.size; j++) {
                LevelData.HitBox secondHitBox = secondHitBoxes.get(j);
                if (secondHitBox == null || secondHitBox.width <= 0f || secondHitBox.height <= 0f) {
                    continue;
                }
                Rectangle secondRect = hitBoxRectAt(
                    secondSprite,
                    secondRuntime,
                    secondWorldX,
                    secondWorldY,
                    secondHitBox,
                    rectCacheB
                );
                if (firstRect.overlaps(secondRect)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected final String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    protected final boolean containsAny(String value, String... needles) {
        if (value == null || value.isEmpty() || needles == null || needles.length == 0) {
            return false;
        }
        for (int i = 0; i < needles.length; i++) {
            String needle = needles[i];
            if (needle != null && !needle.isEmpty() && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int findPlayerSpriteIndex() {
        // Prefer explicit cat sprites (cat, kitty, gato) first
        for (int i = 0; i < levelData.sprites.size; i++) {
            LevelData.LevelSprite sprite = levelData.sprites.get(i);
            String type = normalize(sprite.type);
            String name = normalize(sprite.name);
            if (containsAny(type, "cat", "kitty", "gato") || containsAny(name, "cat", "kitty", "gato")) {
                return i;
            }
        }

        // Fallback to traditional player/hero tokens
        for (int i = 0; i < levelData.sprites.size; i++) {
            LevelData.LevelSprite sprite = levelData.sprites.get(i);
            String type = normalize(sprite.type);
            String name = normalize(sprite.name);
            if (containsAny(type, "player", "hero", "heroi", "foxy")
                || containsAny(name, "player", "hero", "heroi", "foxy")) {
                return i;
            }
        }

        // Avoid implicitly choosing the first sprite (often a shop). If none found, return -1.
        return -1;
    }

    private void setFullSpriteRect(
        LevelData.LevelSprite sprite,
        LevelRenderer.SpriteRuntimeState runtime,
        float worldX,
        float worldY,
        Rectangle out
    ) {
        float anchorX = runtime == null ? sprite.anchorX : runtime.anchorX;
        float anchorY = runtime == null ? sprite.anchorY : runtime.anchorY;
        float frameWidth = runtime != null && runtime.frameWidth > 0 ? runtime.frameWidth : sprite.width;
        float frameHeight = runtime != null && runtime.frameHeight > 0 ? runtime.frameHeight : sprite.height;
        float left = worldX - frameWidth * anchorX;
        float top = worldY - frameHeight * anchorY;
        out.set(left, top, frameWidth, frameHeight);
    }

    private Rectangle hitBoxRectAt(
        LevelData.LevelSprite sprite,
        LevelRenderer.SpriteRuntimeState runtime,
        float worldX,
        float worldY,
        LevelData.HitBox hitBox,
        Rectangle out
    ) {
        float anchorX = runtime == null ? sprite.anchorX : runtime.anchorX;
        float anchorY = runtime == null ? sprite.anchorY : runtime.anchorY;
        float frameWidth = runtime != null && runtime.frameWidth > 0 ? runtime.frameWidth : sprite.width;
        float frameHeight = runtime != null && runtime.frameHeight > 0 ? runtime.frameHeight : sprite.height;
        float left = worldX - frameWidth * anchorX;
        float top = worldY - frameHeight * anchorY;

        float normalizedX = hitBox.x;
        float normalizedY = hitBox.y;
        if (runtime != null && runtime.flipX) {
            normalizedX = 1f - hitBox.x - hitBox.width;
        }
        if (runtime != null && runtime.flipY) {
            normalizedY = 1f - hitBox.y - hitBox.height;
        }

        float x = left + normalizedX * frameWidth;
        float y = top + normalizedY * frameHeight;
        float width = hitBox.width * frameWidth;
        float height = hitBox.height * frameHeight;
        out.set(x, y, width, height);
        return out;
    }

    private Array<LevelData.HitBox> activeHitBoxes(int spriteIndex) {
        if (spriteIndex < 0 || spriteIndex >= spriteRuntimeStates.size || spriteIndex >= levelData.sprites.size) {
            return null;
        }
        LevelData.LevelSprite sprite = levelData.sprites.get(spriteIndex);
        LevelRenderer.SpriteRuntimeState runtime = spriteRuntimeStates.get(spriteIndex);
        String animationId = runtime == null ? null : runtime.animationId;
        if (animationId == null || animationId.isEmpty()) {
            animationId = sprite.animationId;
        }
        if (animationId == null || animationId.isEmpty()) {
            return null;
        }
        LevelData.AnimationClip clip = levelData.animationClips.get(animationId);
        if (clip == null) {
            return null;
        }
        int frameIndex = runtime == null ? sprite.frameIndex : runtime.frameIndex;
        LevelData.FrameRig frameRig = clip.frameRigs.get(frameIndex);
        if (frameRig != null && frameRig.hitBoxes != null && frameRig.hitBoxes.size > 0) {
            return frameRig.hitBoxes;
        }
        if (clip.hitBoxes != null && clip.hitBoxes.size > 0) {
            return clip.hitBoxes;
        }
        return null;
    }
}
