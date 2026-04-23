package com.mdominguez.ietiParkAppMobil;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

public final class LevelLoader {

    private static final String GAME_DATA_PATH = "levels/game_data.json";
    private static final float DEFAULT_VIEWPORT_WIDTH = 320f;
    private static final float DEFAULT_VIEWPORT_HEIGHT = 180f;
    private static final String DEFAULT_VIEWPORT_ADAPTATION = "letterbox";
    private static final float DEFAULT_DEPTH_SENSITIVITY = 0.08f;

    private LevelLoader() {
    }

    public static LevelData loadLevel(int levelIndex) {
        FileHandle gameDataFile = Gdx.files.internal(GAME_DATA_PATH);
        if (!gameDataFile.exists()) {
            return emptyLevel("Missing game_data.json");
        }

        try {
            JsonValue root = new JsonReader().parse(gameDataFile);
            JsonValue levels = root.get("levels");
            if (levels == null || !levels.isArray() || levels.size <= 0) {
                return emptyLevel("No levels");
            }

            int safeIndex = clamp(levelIndex, 0, levels.size - 1);
            JsonValue levelNode = levels.get(safeIndex);
            if (levelNode == null || !levelNode.isObject()) {
                return emptyLevel("Invalid level");
            }

            ObjectMap<String, MediaFrameSize> mediaFrameSizes = loadMediaFrameSizes(root);
            ObjectMap<String, LevelData.AnimationClip> animationClips = loadAnimationClips(root, mediaFrameSizes);
            return parseLevel(levelNode, animationClips, mediaFrameSizes);
        } catch (Exception ex) {
            Gdx.app.error("LevelLoader", "Failed to load level data", ex);
            return emptyLevel("Load error");
        }
    }

    private static LevelData parseLevel(
        JsonValue levelNode,
        ObjectMap<String, LevelData.AnimationClip> animationClips,
        ObjectMap<String, MediaFrameSize> mediaFrameSizes
    ) {
        String name = levelNode.getString("name", "Untitled Level");
        Color backgroundColor = parseColor(levelNode.getString("backgroundColorHex", "#000000"));
        float viewportWidth = positiveFiniteOrDefault(levelNode, "viewportWidth", DEFAULT_VIEWPORT_WIDTH);
        float viewportHeight = positiveFiniteOrDefault(levelNode, "viewportHeight", DEFAULT_VIEWPORT_HEIGHT);
        float viewportX = finiteOrDefault(levelNode, "viewportX", 0f);
        float viewportY = finiteOrDefault(levelNode, "viewportY", 0f);
        float depthSensitivity = nonNegativeFiniteOrDefault(levelNode, "depthSensitivity", DEFAULT_DEPTH_SENSITIVITY);
        String viewportAdaptation = normalizeViewportAdaptation(
            levelNode.getString("viewportAdaptation", DEFAULT_VIEWPORT_ADAPTATION)
        );

        Array<LevelData.LevelLayer> layers = new Array<>();
        JsonValue layersNode = levelNode.get("layers");
        if (layersNode != null && layersNode.isArray()) {
            for (JsonValue layerNode = layersNode.child; layerNode != null; layerNode = layerNode.next) {
                LevelData.LevelLayer layer = parseLayer(layerNode);
                if (layer != null) {
                    layers.add(layer);
                }
            }
        }

        Array<LevelData.LevelSprite> sprites = new Array<>();
        JsonValue spritesNode = levelNode.get("sprites");
        if (spritesNode != null && spritesNode.isArray()) {
            for (JsonValue spriteNode = spritesNode.child; spriteNode != null; spriteNode = spriteNode.next) {
                LevelData.LevelSprite sprite = parseSprite(spriteNode, animationClips, mediaFrameSizes);
                if (sprite != null) {
                    sprites.add(sprite);
                }
            }
        }

        String zonesFile = levelNode.getString("zonesFile", null);
        String pathsFile = levelNode.getString("pathsFile", null);
        Array<LevelData.LevelZone> zones = loadZones(zonesFile == null ? null : "levels/" + zonesFile);
        PathData pathData = loadPathData(pathsFile == null ? null : "levels/" + pathsFile);
        Array<LevelData.LevelPath> paths = pathData.paths;
        Array<LevelData.LevelPathBinding> pathBindings = pathData.bindings;

        float worldWidth = viewportX + viewportWidth;
        float worldHeight = viewportY + viewportHeight;

        for (int i = 0; i < layers.size; i++) {
            LevelData.LevelLayer layer = layers.get(i);
            int rows = layer.tileMap.length;
            int cols = rows == 0 ? 0 : maxRowLength(layer.tileMap);
            float layerRight = layer.x + cols * layer.tileWidth;
            float layerBottom = layer.y + rows * layer.tileHeight;
            worldWidth = Math.max(worldWidth, layerRight);
            worldHeight = Math.max(worldHeight, layerBottom);
        }

        for (int i = 0; i < sprites.size; i++) {
            LevelData.LevelSprite sprite = sprites.get(i);
            float spriteLeft = sprite.x - sprite.width * sprite.anchorX;
            float spriteTop = sprite.y - sprite.height * sprite.anchorY;
            float spriteRight = spriteLeft + sprite.width;
            float spriteBottom = spriteTop + sprite.height;
            worldWidth = Math.max(worldWidth, spriteRight);
            worldHeight = Math.max(worldHeight, spriteBottom);
        }

        for (int i = 0; i < zones.size; i++) {
            LevelData.LevelZone zone = zones.get(i);
            worldWidth = Math.max(worldWidth, zone.x + zone.width);
            worldHeight = Math.max(worldHeight, zone.y + zone.height);
        }

        for (int i = 0; i < paths.size; i++) {
            LevelData.LevelPath path = paths.get(i);
            for (int p = 0; p < path.points.size; p++) {
                Vector2 point = path.points.get(p);
                worldWidth = Math.max(worldWidth, point.x);
                worldHeight = Math.max(worldHeight, point.y);
            }
        }

        worldWidth = Math.max(worldWidth, DEFAULT_VIEWPORT_WIDTH);
        worldHeight = Math.max(worldHeight, DEFAULT_VIEWPORT_HEIGHT);

        return new LevelData(
            name,
            backgroundColor,
            viewportWidth,
            viewportHeight,
            viewportX,
            viewportY,
            viewportAdaptation,
            depthSensitivity,
            worldWidth,
            worldHeight,
            layers,
            sprites,
            zones,
            paths,
            pathBindings,
            animationClips
        );
    }

    private static LevelData.LevelLayer parseLayer(JsonValue layerNode) {
        String tilesFile = layerNode.getString("tilesSheetFile", null);
        String tileMapFile = layerNode.getString("tileMapFile", null);
        int tileWidth = layerNode.getInt("tilesWidth", 0);
        int tileHeight = layerNode.getInt("tilesHeight", 0);

        if (tilesFile == null || tilesFile.isEmpty() || tileMapFile == null || tileMapFile.isEmpty()) {
            return null;
        }
        if (tileWidth <= 0 || tileHeight <= 0) {
            return null;
        }

        return new LevelData.LevelLayer(
            layerNode.getString("name", "Layer"),
            layerNode.getBoolean("visible", true),
            layerNode.getFloat("depth", 0f),
            layerNode.getFloat("x", 0f),
            layerNode.getFloat("y", 0f),
            "levels/" + tilesFile,
            tileWidth,
            tileHeight,
            loadTileMap("levels/" + tileMapFile)
        );
    }

    private static LevelData.LevelSprite parseSprite(
        JsonValue spriteNode,
        ObjectMap<String, LevelData.AnimationClip> animationClips,
        ObjectMap<String, MediaFrameSize> mediaFrameSizes
    ) {
        String imageFile = spriteNode.getString("imageFile", null);
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        float width = spriteNode.getFloat("width", 0f);
        float height = spriteNode.getFloat("height", 0f);

        String animationId = spriteNode.getString("animationId", null);
        int frameIndex = 0;
        float anchorX = 0.5f;
        float anchorY = 0.5f;
        String texturePath = "levels/" + imageFile;
        if (animationId != null) {
            LevelData.AnimationClip clip = animationClips.get(animationId);
            if (clip != null) {
                if (clip.texturePath != null && !clip.texturePath.isEmpty()) {
                    texturePath = clip.texturePath;
                }
                frameIndex = Math.max(0, clip.startFrame);
                LevelData.FrameRig startRig = clip.frameRigs.get(frameIndex);
                if (startRig != null) {
                    anchorX = startRig.anchorX;
                    anchorY = startRig.anchorY;
                } else {
                    anchorX = clip.anchorX;
                    anchorY = clip.anchorY;
                }
            }
        }

        MediaFrameSize mediaSize = mediaFrameSizes.get(texturePath);
        if (mediaSize != null && mediaSize.width > 0f && mediaSize.height > 0f) {
            width = mediaSize.width;
            height = mediaSize.height;
        }
        if (width <= 0f || height <= 0f) {
            return null;
        }

        return new LevelData.LevelSprite(
            spriteNode.getString("name", "Sprite"),
            spriteNode.getString("type", ""),
            spriteNode.getFloat("depth", 0f),
            spriteNode.getFloat("x", 0f),
            spriteNode.getFloat("y", 0f),
            width,
            height,
            anchorX,
            anchorY,
            spriteNode.getBoolean("flipX", false),
            spriteNode.getBoolean("flipY", false),
            frameIndex,
            texturePath,
            animationId
        );
    }

    private static ObjectMap<String, MediaFrameSize> loadMediaFrameSizes(JsonValue root) {
        ObjectMap<String, MediaFrameSize> mapping = new ObjectMap<>();
        JsonValue assets = root.get("mediaAssets");
        if (assets == null || !assets.isArray()) {
            return mapping;
        }

        for (JsonValue asset = assets.child; asset != null; asset = asset.next) {
            String fileName = asset.getString("fileName", null);
            if (fileName == null || fileName.isEmpty()) {
                continue;
            }
            int tileWidth = asset.getInt("tileWidth", 0);
            int tileHeight = asset.getInt("tileHeight", 0);
            if (tileWidth <= 0 || tileHeight <= 0) {
                continue;
            }
            mapping.put(
                "levels/" + fileName,
                new MediaFrameSize(tileWidth, tileHeight)
            );
        }
        return mapping;
    }

    private static ObjectMap<String, LevelData.AnimationClip> loadAnimationClips(
        JsonValue root,
        ObjectMap<String, MediaFrameSize> mediaFrameSizes
    ) {
        ObjectMap<String, LevelData.AnimationClip> mapping = new ObjectMap<>();
        String animationsFilePath = root.getString("animationsFile", null);
        if (animationsFilePath == null || animationsFilePath.isEmpty()) {
            return mapping;
        }

        FileHandle animationsFile = Gdx.files.internal("levels/" + animationsFilePath);
        if (!animationsFile.exists()) {
            return mapping;
        }

        try {
            JsonValue animationsRoot = new JsonReader().parse(animationsFile);
            JsonValue animations = animationsRoot.get("animations");
            if (animations == null || !animations.isArray()) {
                return mapping;
            }

            for (JsonValue animation = animations.child; animation != null; animation = animation.next) {
                String id = animation.getString("id", null);
                int startFrame = Math.max(0, animation.getInt("startFrame", 0));
                if (id != null && !id.isEmpty()) {
                    String mediaFile = animation.getString("mediaFile", null);
                    String texturePath = mediaFile == null || mediaFile.isEmpty() ? null : "levels/" + mediaFile;
                    MediaFrameSize mediaSize = texturePath == null ? null : mediaFrameSizes.get(texturePath);
                    int frameWidth = mediaSize == null ? 0 : Math.max(0, Math.round(mediaSize.width));
                    int frameHeight = mediaSize == null ? 0 : Math.max(0, Math.round(mediaSize.height));
                    float anchorX = anchorOrDefault(animation.getFloat("anchorX", 0.5f), 0.5f);
                    float anchorY = anchorOrDefault(animation.getFloat("anchorY", 0.5f), 0.5f);
                    int endFrame = Math.max(startFrame, animation.getInt("endFrame", startFrame));
                    float fps = positiveFiniteOrDefault(animation, "fps", 8f);
                    boolean loop = animation.getBoolean("loop", true);
                    ObjectMap<Integer, LevelData.FrameRig> frameRigByFrame = new ObjectMap<>();
                    Array<LevelData.HitBox> clipHitBoxes = parseHitBoxes(animation.get("hitBoxes"));

                    JsonValue frameRigsNode = animation.get("frameRigs");
                    if (frameRigsNode != null && frameRigsNode.isArray()) {
                        for (JsonValue frameRigNode = frameRigsNode.child; frameRigNode != null; frameRigNode = frameRigNode.next) {
                            int frame = frameRigNode.getInt("frame", -1);
                            if (frame < 0) {
                                continue;
                            }
                            float rigAnchorX = anchorOrDefault(frameRigNode.getFloat("anchorX", anchorX), anchorX);
                            float rigAnchorY = anchorOrDefault(frameRigNode.getFloat("anchorY", anchorY), anchorY);
                            Array<LevelData.HitBox> rigHitBoxes = parseHitBoxes(frameRigNode.get("hitBoxes"));
                            frameRigByFrame.put(frame, new LevelData.FrameRig(rigAnchorX, rigAnchorY, rigHitBoxes));
                        }
                    }

                    mapping.put(
                        id,
                        new LevelData.AnimationClip(
                            id,
                            animation.getString("name", id),
                            texturePath,
                            frameWidth,
                            frameHeight,
                            startFrame,
                            endFrame,
                            fps,
                            loop,
                            anchorX,
                            anchorY,
                            clipHitBoxes,
                            frameRigByFrame
                        )
                    );
                }
            }
        } catch (Exception ex) {
            Gdx.app.error("LevelLoader", "Failed to parse animations file", ex);
        }

        return mapping;
    }

    private static Array<LevelData.LevelZone> loadZones(String zonesPath) {
        Array<LevelData.LevelZone> zones = new Array<>();
        if (zonesPath == null || zonesPath.isEmpty()) {
            return zones;
        }

        FileHandle file = Gdx.files.internal(zonesPath);
        if (!file.exists()) {
            return zones;
        }

        try {
            JsonValue root = new JsonReader().parse(file);
            JsonValue zonesNode = root.get("zones");
            if (zonesNode == null || !zonesNode.isArray()) {
                return zones;
            }

            for (JsonValue zoneNode = zonesNode.child; zoneNode != null; zoneNode = zoneNode.next) {
                float x = zoneNode.getFloat("x", 0f);
                float y = zoneNode.getFloat("y", 0f);
                float width = zoneNode.getFloat("width", 0f);
                float height = zoneNode.getFloat("height", 0f);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)) {
                    continue;
                }
                if (width <= 0f || height <= 0f) {
                    continue;
                }

                zones.add(new LevelData.LevelZone(
                    zoneNode.getString("name", "Zone"),
                    zoneNode.getString("type", ""),
                    zoneNode.getString("gameplayData", ""),
                    zoneNode.getString("groupId", ""),
                    x,
                    y,
                    width,
                    height,
                    parseColor(zoneNode.getString("color", "yellow"))
                ));
            }
        } catch (Exception ex) {
            Gdx.app.error("LevelLoader", "Failed to parse zones file " + zonesPath, ex);
        }
        return zones;
    }

    private static PathData loadPathData(String pathsPath) {
        PathData output = new PathData();
        if (pathsPath == null || pathsPath.isEmpty()) {
            return output;
        }

        FileHandle file = Gdx.files.internal(pathsPath);
        if (!file.exists()) {
            return output;
        }

        try {
            JsonValue root = new JsonReader().parse(file);
            JsonValue pathsNode = root.get("paths");
            if (pathsNode != null && pathsNode.isArray()) {
                for (JsonValue pathNode = pathsNode.child; pathNode != null; pathNode = pathNode.next) {
                    Array<Vector2> points = new Array<>();
                    JsonValue pointsNode = pathNode.get("points");
                    if (pointsNode != null && pointsNode.isArray()) {
                        for (JsonValue pointNode = pointsNode.child; pointNode != null; pointNode = pointNode.next) {
                            float x = pointNode.getFloat("x", Float.NaN);
                            float y = pointNode.getFloat("y", Float.NaN);
                            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                                continue;
                            }
                            points.add(new Vector2(x, y));
                        }
                    }
                    if (points.size <= 0) {
                        continue;
                    }

                    output.paths.add(new LevelData.LevelPath(
                        pathNode.getString("id", ""),
                        pathNode.getString("name", "Path"),
                        parseColor(pathNode.getString("color", "yellow")),
                        points
                    ));
                }
            }

            JsonValue bindingsNode = root.get("pathBindings");
            if (bindingsNode != null && bindingsNode.isArray()) {
                for (JsonValue bindingNode = bindingsNode.child; bindingNode != null; bindingNode = bindingNode.next) {
                    String pathId = bindingNode.getString("pathId", "").trim();
                    String targetType = bindingNode.getString("targetType", "").trim().toLowerCase();
                    int targetIndex = bindingNode.getInt("targetIndex", -1);
                    if (pathId.isEmpty() || targetIndex < 0) {
                        continue;
                    }
                    int durationMs = bindingNode.getInt("durationMs", 2000);
                    float durationSeconds = durationMs > 0 ? durationMs / 1000f : 2f;
                    output.bindings.add(new LevelData.LevelPathBinding(
                        bindingNode.getString("id", ""),
                        pathId,
                        targetType,
                        targetIndex,
                        bindingNode.getString("behavior", "restart").trim().toLowerCase(),
                        bindingNode.getBoolean("enabled", true),
                        bindingNode.getBoolean("relativeToInitialPosition", true),
                        durationSeconds
                    ));
                }
            }
        } catch (Exception ex) {
            Gdx.app.error("LevelLoader", "Failed to parse paths file " + pathsPath, ex);
        }
        return output;
    }

    private static int[][] loadTileMap(String tileMapPath) {
        FileHandle tileMapFile = Gdx.files.internal(tileMapPath);
        if (!tileMapFile.exists()) {
            return new int[0][0];
        }

        try {
            JsonValue root = new JsonReader().parse(tileMapFile);
            JsonValue rowsNode = root.get("tileMap");
            if (rowsNode == null || !rowsNode.isArray()) {
                return new int[0][0];
            }

            int[][] rows = new int[rowsNode.size][];
            int rowIndex = 0;
            for (JsonValue rowNode = rowsNode.child; rowNode != null; rowNode = rowNode.next) {
                if (!rowNode.isArray()) {
                    rows[rowIndex++] = new int[0];
                    continue;
                }

                int[] row = new int[rowNode.size];
                int col = 0;
                for (JsonValue value = rowNode.child; value != null; value = value.next) {
                    row[col++] = value.asInt();
                }
                rows[rowIndex++] = row;
            }
            return rows;
        } catch (Exception ex) {
            Gdx.app.error("LevelLoader", "Failed to parse tile map " + tileMapPath, ex);
            return new int[0][0];
        }
    }

    private static Color parseColor(String value) {
        if (value == null || value.isEmpty()) {
            return Color.BLACK.cpy();
        }

        String normalized = value.trim().toLowerCase();
        if (normalized.startsWith("#")) {
            String hex = normalized.substring(1);
            if (hex.length() == 6) {
                return Color.valueOf(hex);
            }
            if (hex.length() == 8) {
                return Color.valueOf(hex);
            }
        }

        switch (normalized) {
            case "white":
                return Color.WHITE.cpy();
            case "red":
                return Color.RED.cpy();
            case "green":
                return Color.GREEN.cpy();
            case "blue":
                return Color.BLUE.cpy();
            case "yellow":
                return Color.YELLOW.cpy();
            case "orange":
                return Color.ORANGE.cpy();
            case "purple":
                return Color.PURPLE.cpy();
            case "pink":
                return Color.PINK.cpy();
            case "gray":
            case "grey":
                return Color.GRAY.cpy();
            default:
                return Color.BLACK.cpy();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static LevelData emptyLevel(String name) {
        return new LevelData(
            name,
            Color.BLACK.cpy(),
            DEFAULT_VIEWPORT_WIDTH,
            DEFAULT_VIEWPORT_HEIGHT,
            0f,
            0f,
            DEFAULT_VIEWPORT_ADAPTATION,
            DEFAULT_DEPTH_SENSITIVITY,
            DEFAULT_VIEWPORT_WIDTH,
            DEFAULT_VIEWPORT_HEIGHT,
            new Array<>(),
            new Array<>(),
            new Array<>(),
            new Array<>(),
            new Array<>(),
            new ObjectMap<>()
        );
    }

    private static int maxRowLength(int[][] rows) {
        int max = 0;
        for (int i = 0; i < rows.length; i++) {
            max = Math.max(max, rows[i].length);
        }
        return max;
    }

    private static float positiveFiniteOrDefault(JsonValue node, String field, float fallback) {
        float value = node.getFloat(field, fallback);
        if (!Float.isFinite(value) || value <= 0f) {
            return fallback;
        }
        return value;
    }

    private static float finiteOrDefault(JsonValue node, String field, float fallback) {
        float value = node.getFloat(field, fallback);
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return value;
    }

    private static float nonNegativeFiniteOrDefault(JsonValue node, String field, float fallback) {
        float value = node.getFloat(field, fallback);
        if (!Float.isFinite(value) || value < 0f) {
            return fallback;
        }
        return value;
    }

    private static float anchorOrDefault(float value, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private static Array<LevelData.HitBox> parseHitBoxes(JsonValue hitBoxesNode) {
        Array<LevelData.HitBox> hitBoxes = new Array<>();
        if (hitBoxesNode == null || !hitBoxesNode.isArray()) {
            return hitBoxes;
        }

        for (JsonValue hitBoxNode = hitBoxesNode.child; hitBoxNode != null; hitBoxNode = hitBoxNode.next) {
            float x = hitBoxNode.getFloat("x", Float.NaN);
            float y = hitBoxNode.getFloat("y", Float.NaN);
            float width = hitBoxNode.getFloat("width", Float.NaN);
            float height = hitBoxNode.getFloat("height", Float.NaN);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)) {
                continue;
            }
            if (width <= 0f || height <= 0f) {
                continue;
            }
            hitBoxes.add(new LevelData.HitBox(
                hitBoxNode.getString("id", ""),
                hitBoxNode.getString("name", ""),
                x,
                y,
                width,
                height
            ));
        }
        return hitBoxes;
    }

    private static String normalizeViewportAdaptation(String raw) {
        if (raw == null) {
            return DEFAULT_VIEWPORT_ADAPTATION;
        }
        String normalized = raw.trim().toLowerCase();
        switch (normalized) {
            case "fit":
            case "contain":
            case "letterbox":
                return "letterbox";
            case "expand":
                return "expand";
            case "stretch":
            case "strech":
                return "stretch";
            default:
                return DEFAULT_VIEWPORT_ADAPTATION;
        }
    }

    private static final class MediaFrameSize {
        final float width;
        final float height;

        MediaFrameSize(float width, float height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class PathData {
        final Array<LevelData.LevelPath> paths = new Array<>();
        final Array<LevelData.LevelPathBinding> bindings = new Array<>();
    }

}
