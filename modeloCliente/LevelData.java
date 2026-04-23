package com.mdominguez.ietiParkAppMobil;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

public final class LevelData {

    public final String name;
    public final Color backgroundColor;
    public final float viewportWidth;
    public final float viewportHeight;
    public final float viewportX;
    public final float viewportY;
    public final String viewportAdaptation;
    public final float depthSensitivity;
    public final float worldWidth;
    public final float worldHeight;
    public final Array<LevelLayer> layers;
    public final Array<LevelSprite> sprites;
    public final Array<LevelZone> zones;
    public final Array<LevelPath> paths;
    public final Array<LevelPathBinding> pathBindings;
    public final ObjectMap<String, AnimationClip> animationClips;

    public LevelData(
        String name,
        Color backgroundColor,
        float viewportWidth,
        float viewportHeight,
        float viewportX,
        float viewportY,
        String viewportAdaptation,
        float depthSensitivity,
        float worldWidth,
        float worldHeight,
        Array<LevelLayer> layers,
        Array<LevelSprite> sprites,
        Array<LevelZone> zones,
        Array<LevelPath> paths,
        Array<LevelPathBinding> pathBindings,
        ObjectMap<String, AnimationClip> animationClips
    ) {
        this.name = name;
        this.backgroundColor = backgroundColor;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.viewportX = viewportX;
        this.viewportY = viewportY;
        this.viewportAdaptation = viewportAdaptation;
        this.depthSensitivity = depthSensitivity;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.layers = layers;
        this.sprites = sprites;
        this.zones = zones;
        this.paths = paths;
        this.pathBindings = pathBindings;
        this.animationClips = animationClips;
    }

    public static final class LevelLayer {
        public final String name;
        public final boolean visible;
        public final float depth;
        public final float x;
        public final float y;
        public final String tilesTexturePath;
        public final int tileWidth;
        public final int tileHeight;
        public final int[][] tileMap;

        public LevelLayer(
            String name,
            boolean visible,
            float depth,
            float x,
            float y,
            String tilesTexturePath,
            int tileWidth,
            int tileHeight,
            int[][] tileMap
        ) {
            this.name = name;
            this.visible = visible;
            this.depth = depth;
            this.x = x;
            this.y = y;
            this.tilesTexturePath = tilesTexturePath;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.tileMap = tileMap;
        }
    }

    public static final class LevelSprite {
        public final String name;
        public final String type;
        public final float depth;
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        public final float anchorX;
        public final float anchorY;
        public final boolean flipX;
        public final boolean flipY;
        public final int frameIndex;
        public final String texturePath;
        public final String animationId;

        public LevelSprite(
            String name,
            String type,
            float depth,
            float x,
            float y,
            float width,
            float height,
            float anchorX,
            float anchorY,
            boolean flipX,
            boolean flipY,
            int frameIndex,
            String texturePath,
            String animationId
        ) {
            this.name = name;
            this.type = type;
            this.depth = depth;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.flipX = flipX;
            this.flipY = flipY;
            this.frameIndex = frameIndex;
            this.texturePath = texturePath;
            this.animationId = animationId;
        }
    }

    public static final class LevelZone {
        public final String name;
        public final String type;
        public final String gameplayData;
        public final String groupId;
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        public final Color color;

        public LevelZone(
            String name,
            String type,
            String gameplayData,
            String groupId,
            float x,
            float y,
            float width,
            float height,
            Color color
        ) {
            this.name = name;
            this.type = type;
            this.gameplayData = gameplayData;
            this.groupId = groupId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
        }
    }

    public static final class LevelPath {
        public final String id;
        public final String name;
        public final Color color;
        public final Array<Vector2> points;

        public LevelPath(
            String id,
            String name,
            Color color,
            Array<Vector2> points
        ) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.points = points;
        }
    }

    public static final class LevelPathBinding {
        public final String id;
        public final String pathId;
        public final String targetType;
        public final int targetIndex;
        public final String behavior;
        public final boolean enabled;
        public final boolean relativeToInitialPosition;
        public final float durationSeconds;

        public LevelPathBinding(
            String id,
            String pathId,
            String targetType,
            int targetIndex,
            String behavior,
            boolean enabled,
            boolean relativeToInitialPosition,
            float durationSeconds
        ) {
            this.id = id;
            this.pathId = pathId;
            this.targetType = targetType;
            this.targetIndex = targetIndex;
            this.behavior = behavior;
            this.enabled = enabled;
            this.relativeToInitialPosition = relativeToInitialPosition;
            this.durationSeconds = durationSeconds;
        }
    }

    public static final class AnimationClip {
        public final String id;
        public final String name;
        public final String texturePath;
        public final int frameWidth;
        public final int frameHeight;
        public final int startFrame;
        public final int endFrame;
        public final float fps;
        public final boolean loop;
        public final float anchorX;
        public final float anchorY;
        public final Array<HitBox> hitBoxes;
        public final ObjectMap<Integer, FrameRig> frameRigs;

        public AnimationClip(
            String id,
            String name,
            String texturePath,
            int frameWidth,
            int frameHeight,
            int startFrame,
            int endFrame,
            float fps,
            boolean loop,
            float anchorX,
            float anchorY,
            Array<HitBox> hitBoxes,
            ObjectMap<Integer, FrameRig> frameRigs
        ) {
            this.id = id;
            this.name = name;
            this.texturePath = texturePath;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.startFrame = startFrame;
            this.endFrame = endFrame;
            this.fps = fps;
            this.loop = loop;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.hitBoxes = hitBoxes;
            this.frameRigs = frameRigs;
        }
    }

    public static final class FrameRig {
        public final float anchorX;
        public final float anchorY;
        public final Array<HitBox> hitBoxes;

        public FrameRig(float anchorX, float anchorY, Array<HitBox> hitBoxes) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.hitBoxes = hitBoxes;
        }
    }

    public static final class HitBox {
        public final String id;
        public final String name;
        public final float x;
        public final float y;
        public final float width;
        public final float height;

        public HitBox(
            String id,
            String name,
            float x,
            float y,
            float width,
            float height
        ) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
