package com.mdominguez.ietiParkAppMobil;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;

public final class LevelRenderer {

    private static final float MIN_DEPTH_PROJECTION_FACTOR = 0.25f;
    private static final float MAX_DEPTH_PROJECTION_FACTOR = 4.0f;
    private static final float TILE_OVERDRAW = 0.02f;
    private static final int TILE_CULL_PADDING = 1;
    private final ObjectMap<String, TextureRegion[][]> splitCache = new ObjectMap<>();
    private final ObjectSet<Texture> configuredTextures = new ObjectSet<>();

    public void render(
        LevelData level,
        AssetManager assets,
        SpriteBatch batch,
        OrthographicCamera camera,
        Array<SpriteRuntimeState> spriteRuntimeStates,
        boolean[] layerVisibilityStates,
        Array<RuntimeTransform> layerRuntimeStates
    ) {
        FloatArray depths = collectDepths(level);
        depths.sort();
        depths.reverse();

        float baseZoom = camera.zoom;
        for (int i = 0; i < depths.size; i++) {
            float depth = depths.get(i);
            float projectionFactor = depthProjectionFactorForDepth(depth, level.depthSensitivity);
            camera.zoom = baseZoom / projectionFactor;
            camera.update();
            batch.setProjectionMatrix(camera.combined);
            float halfViewWidth = camera.viewportWidth * camera.zoom * 0.5f;
            float halfViewHeight = camera.viewportHeight * camera.zoom * 0.5f;
            float visibleLeft = camera.position.x - halfViewWidth;
            float visibleRight = camera.position.x + halfViewWidth;
            float visibleBottom = camera.position.y - halfViewHeight;
            float visibleTop = camera.position.y + halfViewHeight;

            renderLayersAtDepth(
                level.layers,
                depth,
                assets,
                batch,
                level.worldHeight,
                layerVisibilityStates,
                layerRuntimeStates,
                visibleLeft,
                visibleRight,
                visibleBottom,
                visibleTop
            );
            renderSpritesAtDepth(level.sprites, spriteRuntimeStates, depth, assets, batch, level.worldHeight);
        }

        camera.zoom = baseZoom;
        camera.update();
        batch.setProjectionMatrix(camera.combined);
    }

    private FloatArray collectDepths(LevelData level) {
        FloatArray values = new FloatArray();
        for (int i = 0; i < level.layers.size; i++) {
            LevelData.LevelLayer layer = level.layers.get(i);
            if (layer.visible && !containsDepth(values, layer.depth)) {
                values.add(layer.depth);
            }
        }
        for (int i = 0; i < level.sprites.size; i++) {
            LevelData.LevelSprite sprite = level.sprites.get(i);
            if (!containsDepth(values, sprite.depth)) {
                values.add(sprite.depth);
            }
        }
        return values;
    }

    private void renderLayersAtDepth(
        Array<LevelData.LevelLayer> layers,
        float depth,
        AssetManager assets,
        SpriteBatch batch,
        float worldHeight,
        boolean[] layerVisibilityStates,
        Array<RuntimeTransform> layerRuntimeStates,
        float visibleLeft,
        float visibleRight,
        float visibleBottom,
        float visibleTop
    ) {
        // Keep the same painter order as games_tool: reversed layer list per depth.
        for (int i = layers.size - 1; i >= 0; i--) {
            LevelData.LevelLayer layer = layers.get(i);
            boolean visible = layer.visible;
            if (layerVisibilityStates != null && i >= 0 && i < layerVisibilityStates.length) {
                visible = layerVisibilityStates[i];
            }
            if (!visible || !sameDepth(layer.depth, depth)) {
                continue;
            }
            RuntimeTransform runtime = layerRuntimeStates != null && i >= 0 && i < layerRuntimeStates.size
                ? layerRuntimeStates.get(i)
                : null;
            drawLayer(layer, runtime, assets, batch, worldHeight, visibleLeft, visibleRight, visibleBottom, visibleTop);
        }
    }

    private void drawLayer(
        LevelData.LevelLayer layer,
        RuntimeTransform runtime,
        AssetManager assets,
        SpriteBatch batch,
        float worldHeight,
        float visibleLeft,
        float visibleRight,
        float visibleBottom,
        float visibleTop
    ) {
        if (!assets.isLoaded(layer.tilesTexturePath, Texture.class)
            || layer.tileMap == null
            || layer.tileMap.length == 0
            || layer.tileWidth <= 0
            || layer.tileHeight <= 0) {
            return;
        }

        Texture texture = assets.get(layer.tilesTexturePath, Texture.class);
        configureTexture(texture);
        TextureRegion[][] regions = getSplitRegions(layer.tilesTexturePath, texture, layer.tileWidth, layer.tileHeight);
        if (regions.length == 0 || regions[0].length == 0) {
            return;
        }

        int cols = regions[0].length;
        float layerX = runtime == null ? layer.x : runtime.x;
        float layerY = runtime == null ? layer.y : runtime.y;
        int firstCol = Math.max(0, (int)Math.floor((visibleLeft - layerX) / layer.tileWidth) - TILE_CULL_PADDING);
        int lastCol = (int)Math.ceil((visibleRight - layerX) / layer.tileWidth) - 1 + TILE_CULL_PADDING;
        float visibleTopDown = worldHeight - visibleTop;
        float visibleBottomDown = worldHeight - visibleBottom;
        int firstRow = Math.max(0, (int)Math.floor((visibleTopDown - layerY) / layer.tileHeight) - TILE_CULL_PADDING);
        int lastRow = Math.min(
            layer.tileMap.length - 1,
            (int)Math.ceil((visibleBottomDown - layerY) / layer.tileHeight) - 1 + TILE_CULL_PADDING
        );
        if (lastCol < 0 || lastRow < firstRow) {
            return;
        }

        for (int row = firstRow; row <= lastRow; row++) {
            int[] rowData = layer.tileMap[row];
            if (rowData == null || rowData.length == 0) {
                continue;
            }

            int rowFirstCol = Math.max(0, firstCol);
            int rowLastCol = Math.min(rowData.length - 1, lastCol);
            if (rowFirstCol > rowLastCol) {
                continue;
            }

            for (int col = rowFirstCol; col <= rowLastCol; col++) {
                int tileIndex = rowData[col];
                if (tileIndex < 0) {
                    continue;
                }

                int srcRow = tileIndex / cols;
                int srcCol = tileIndex % cols;
                if (srcRow < 0 || srcRow >= regions.length || srcCol < 0 || srcCol >= regions[srcRow].length) {
                    continue;
                }

                float x = layerX + col * layer.tileWidth;
                float yDown = layerY + row * layer.tileHeight;
                float y = worldHeight - yDown - layer.tileHeight;
                batch.draw(
                    regions[srcRow][srcCol],
                    x - TILE_OVERDRAW,
                    y - TILE_OVERDRAW,
                    layer.tileWidth + TILE_OVERDRAW * 2f,
                    layer.tileHeight + TILE_OVERDRAW * 2f
                );
            }
        }
    }

    private void renderSpritesAtDepth(
        Array<LevelData.LevelSprite> sprites,
        Array<SpriteRuntimeState> spriteRuntimeStates,
        float depth,
        AssetManager assets,
        SpriteBatch batch,
        float worldHeight
    ) {
        for (int i = 0; i < sprites.size; i++) {
            LevelData.LevelSprite sprite = sprites.get(i);
            if (!sameDepth(sprite.depth, depth)) {
                continue;
            }
            SpriteRuntimeState runtimeState =
                spriteRuntimeStates != null && i < spriteRuntimeStates.size ? spriteRuntimeStates.get(i) : null;
            drawSprite(sprite, runtimeState, assets, batch, worldHeight);
        }
    }

    private void drawSprite(
        LevelData.LevelSprite sprite,
        SpriteRuntimeState runtimeState,
        AssetManager assets,
        SpriteBatch batch,
        float worldHeight
    ) {
        if (runtimeState != null && !runtimeState.visible) {
            return;
        }
        String texturePath = runtimeState == null || runtimeState.texturePath == null || runtimeState.texturePath.isEmpty()
            ? sprite.texturePath
            : runtimeState.texturePath;
        if (!assets.isLoaded(texturePath, Texture.class)) {
            return;
        }

        int frameIndex = runtimeState == null ? sprite.frameIndex : runtimeState.frameIndex;
        float anchorX = runtimeState == null ? sprite.anchorX : runtimeState.anchorX;
        float anchorY = runtimeState == null ? sprite.anchorY : runtimeState.anchorY;
        float worldX = runtimeState == null ? sprite.x : runtimeState.worldX;
        float worldY = runtimeState == null ? sprite.y : runtimeState.worldY;
        boolean flipX = runtimeState == null ? sprite.flipX : runtimeState.flipX;
        boolean flipY = runtimeState == null ? sprite.flipY : runtimeState.flipY;
        Texture texture = assets.get(texturePath, Texture.class);
        configureTexture(texture);
        int frameWidth = runtimeState == null ? Math.max(1, Math.round(sprite.width)) : Math.max(1, runtimeState.frameWidth);
        int frameHeight = runtimeState == null ? Math.max(1, Math.round(sprite.height)) : Math.max(1, runtimeState.frameHeight);
        frameWidth = Math.min(frameWidth, texture.getWidth());
        frameHeight = Math.min(frameHeight, texture.getHeight());
        float leftDown = worldX - frameWidth * anchorX;
        float topDown = worldY - frameHeight * anchorY;
        float x = leftDown;
        float y = worldHeight - topDown - frameHeight;
        float originX = frameWidth * anchorX;
        float originY = frameHeight * (1f - anchorY);
        TextureRegion[][] regions = getSplitRegions(texturePath, texture, frameWidth, frameHeight);
        if (regions.length == 0 || regions[0].length == 0) {
            return;
        }

        int cols = regions[0].length;
        int rows = regions.length;
        int total = rows * cols;
        int frame = Math.max(0, Math.min(total - 1, frameIndex));
        int srcCol = frame % cols;
        int srcRow = frame / cols;
        if (srcRow < 0 || srcRow >= rows || srcCol < 0 || srcCol >= cols) {
            return;
        }
        TextureRegion region = regions[srcRow][srcCol];

        float renderWidth = runtimeState != null && runtimeState.frameWidth > 0 ? runtimeState.frameWidth : sprite.width;
        float renderHeight = runtimeState != null && runtimeState.frameHeight > 0 ? runtimeState.frameHeight : sprite.height;

        batch.draw(
            region,
            x,
            y,
            originX,
            originY,
            renderWidth,
            renderHeight,
            flipX ? -1f : 1f,
            flipY ? -1f : 1f,
            0f
        );
    }

    private TextureRegion[][] getSplitRegions(String texturePath, Texture texture, int tileWidth, int tileHeight) {
        String key = texturePath + "#" + tileWidth + "x" + tileHeight;
        TextureRegion[][] cached = splitCache.get(key);
        if (cached != null) {
            return cached;
        }
        TextureRegion[][] split = TextureRegion.split(texture, tileWidth, tileHeight);
        splitCache.put(key, split);
        return split;
    }

    private void configureTexture(Texture texture) {
        if (texture == null || configuredTextures.contains(texture)) {
            return;
        }
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        configuredTextures.add(texture);
    }

    private boolean containsDepth(FloatArray values, float depth) {
        for (int i = 0; i < values.size; i++) {
            if (sameDepth(values.get(i), depth)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameDepth(float a, float b) {
        return Math.abs(a - b) <= 0.000001f;
    }

    private float depthProjectionFactorForDepth(float depth, float sensitivity) {
        float safeSensitivity = Float.isFinite(sensitivity) && sensitivity >= 0f ? sensitivity : 0.08f;
        float factor = (float) Math.exp(-depth * safeSensitivity);
        return Math.max(MIN_DEPTH_PROJECTION_FACTOR, Math.min(MAX_DEPTH_PROJECTION_FACTOR, factor));
    }

    public static final class SpriteRuntimeState {
        public int frameIndex;
        public float anchorX;
        public float anchorY;
        public float worldX;
        public float worldY;
        public boolean visible;
        public boolean flipX;
        public boolean flipY;
        public int frameWidth;
        public int frameHeight;
        public String texturePath;
        public String animationId;

        public SpriteRuntimeState(
            int frameIndex,
            float anchorX,
            float anchorY,
            float worldX,
            float worldY,
            boolean visible,
            boolean flipX,
            boolean flipY,
            int frameWidth,
            int frameHeight,
            String texturePath,
            String animationId
        ) {
            this.frameIndex = frameIndex;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.worldX = worldX;
            this.worldY = worldY;
            this.visible = visible;
            this.flipX = flipX;
            this.flipY = flipY;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.texturePath = texturePath;
            this.animationId = animationId;
        }
    }
}
