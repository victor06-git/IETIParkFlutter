package com.mdominguez.ietiParkAndroid;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;

public class GameApp extends Game {

    private final AssetManager assetManager = new AssetManager();
    private final Array<String> menuOptions = new Array<>();
    private final Array<String> levelNames = new Array<>();
    private final Array<Array<String>> referencedImageFilesByLevel = new Array<>();
    private final ObjectSet<String> queuedAssets = new ObjectSet<>();
    private final ObjectMap<String, String> animationMediaById = new ObjectMap<>();
    private final ObjectMap<String, String> animationGroupById = new ObjectMap<>();
    private final ObjectMap<String, Array<String>> animationMediaByGroup = new ObjectMap<>();
    private final Array<AnimationMediaEntry> animationMediaEntries = new Array<>();

    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        configureFontFiltering(font);
        loadProjectData();
        setScreen(new MenuScreen(this));
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public ShapeRenderer getShapeRenderer() {
        return shapeRenderer;
    }

    public BitmapFont getFont() {
        return font;
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public Array<String> getMenuOptions() {
        return menuOptions;
    }

    public String getLevelName(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= levelNames.size) {
            return "Unknown";
        }
        return levelNames.get(levelIndex);
    }

    public void queueReferencedAssetsForLevel(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= referencedImageFilesByLevel.size) {
            return;
        }

        Array<String> levelFiles = referencedImageFilesByLevel.get(levelIndex);
        for (String relativePath : levelFiles) {
            String assetPath = "levels/" + relativePath;
            if (!queuedAssets.contains(assetPath)) {
                assetManager.load(assetPath, Texture.class);
                queuedAssets.add(assetPath);
            }
        }
    }

    public void unloadReferencedAssetsForLevel(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= referencedImageFilesByLevel.size) {
            return;
        }

        Array<String> levelFiles = referencedImageFilesByLevel.get(levelIndex);
        for (String relativePath : levelFiles) {
            String assetPath = "levels/" + relativePath;
            if (assetManager.isLoaded(assetPath, Texture.class)) {
                assetManager.unload(assetPath);
            }
            queuedAssets.remove(assetPath);
        }
    }

    private void loadProjectData() {
        menuOptions.clear();
        levelNames.clear();
        referencedImageFilesByLevel.clear();
        animationMediaById.clear();
        animationGroupById.clear();
        animationMediaByGroup.clear();
        animationMediaEntries.clear();

        FileHandle gameDataFile = Gdx.files.internal("levels/game_data.json");
        if (!gameDataFile.exists()) {
            addFallbackLevels();
            return;
        }

        try {
            JsonValue root = new JsonReader().parse(gameDataFile);
            JsonValue levels = root.get("levels");
            if (levels == null || !levels.isArray() || levels.size <= 0) {
                addFallbackLevels();
                return;
            }

            loadAnimationsFile(root);

            int index = 0;
            for (JsonValue level = levels.child; level != null; level = level.next) {
                String levelName = level.getString("name", "Level " + index);
                levelNames.add(levelName);
                menuOptions.add("LEVEL " + index);
                Array<String> levelImageFiles = new Array<>();
                collectImageFiles(level, levelImageFiles);
                collectAnimationMediaForLevel(level, levelImageFiles);
                referencedImageFilesByLevel.add(levelImageFiles);
                index++;
            }
        } catch (Exception ex) {
            Gdx.app.error("GameApp", "Failed to parse levels/game_data.json", ex);
            addFallbackLevels();
        }
    }

    private void addFallbackLevels() {
        levelNames.add("Level 0");
        levelNames.add("Level 1");
        menuOptions.add("LEVEL 0");
        menuOptions.add("LEVEL 1");
        referencedImageFilesByLevel.add(new Array<>());
        referencedImageFilesByLevel.add(new Array<>());
    }

    private void loadAnimationsFile(JsonValue root) {
        String animationsFilePath = root.getString("animationsFile", null);
        if (animationsFilePath == null || animationsFilePath.isEmpty()) {
            return;
        }

        FileHandle file = Gdx.files.internal("levels/" + animationsFilePath);
        if (!file.exists()) {
            return;
        }

        try {
            JsonValue animationsRoot = new JsonReader().parse(file);
            JsonValue animations = animationsRoot.get("animations");
            if (animations == null || !animations.isArray()) {
                return;
            }

            for (JsonValue animation = animations.child; animation != null; animation = animation.next) {
                String id = animation.getString("id", null);
                String name = animation.getString("name", null);
                String mediaFile = animation.getString("mediaFile", null);
                String groupId = animation.getString("groupId", "");
                if (id == null || mediaFile == null || !looksLikeImageFile(mediaFile)) {
                    continue;
                }
                animationMediaById.put(id, mediaFile);
                animationGroupById.put(id, groupId == null ? "" : groupId);
                if (groupId != null && !groupId.isEmpty()) {
                    Array<String> groupMedia = animationMediaByGroup.get(groupId);
                    if (groupMedia == null) {
                        groupMedia = new Array<>();
                        animationMediaByGroup.put(groupId, groupMedia);
                    }
                    if (!groupMedia.contains(mediaFile, false)) {
                        groupMedia.add(mediaFile);
                    }
                }
                String normalizedName = normalize(name);
                animationMediaEntries.add(new AnimationMediaEntry(
                    normalizedName,
                    mediaFile
                ));
            }
        } catch (Exception ex) {
            Gdx.app.error("GameApp", "Failed to parse animations file", ex);
        }
    }

    private void collectAnimationMediaForLevel(JsonValue level, Array<String> levelImageFiles) {
        JsonValue sprites = level.get("sprites");
        if (sprites == null || !sprites.isArray()) {
            return;
        }

        ObjectSet<String> spriteTokens = new ObjectSet<>();
        ObjectSet<String> animationGroups = new ObjectSet<>();
        for (JsonValue sprite = sprites.child; sprite != null; sprite = sprite.next) {
            String animationId = sprite.getString("animationId", null);
            if (animationId == null) {
                animationId = "";
            }

            String mediaFile = animationMediaById.get(animationId);
            if (mediaFile != null && !levelImageFiles.contains(mediaFile, false)) {
                levelImageFiles.add(mediaFile);
            }
            String groupId = animationGroupById.get(animationId);
            if (groupId != null && !groupId.isEmpty()) {
                animationGroups.add(groupId);
            }

            addTokens(spriteTokens, sprite.getString("type", ""));
            addTokens(spriteTokens, sprite.getString("name", ""));
        }

        for (String groupId : animationGroups) {
            Array<String> groupMedia = animationMediaByGroup.get(groupId);
            if (groupMedia == null || groupMedia.size <= 0) {
                continue;
            }
            for (int i = 0; i < groupMedia.size; i++) {
                String mediaFile = groupMedia.get(i);
                if (mediaFile != null && !levelImageFiles.contains(mediaFile, false)) {
                    levelImageFiles.add(mediaFile);
                }
            }
        }

        if (spriteTokens.size <= 0 || animationMediaEntries.size <= 0) {
            return;
        }

        for (int i = 0; i < animationMediaEntries.size; i++) {
            AnimationMediaEntry entry = animationMediaEntries.get(i);
            if (entry == null || entry.normalizedName.isEmpty()) {
                continue;
            }
            if (!containsAnyToken(entry.normalizedName, spriteTokens)) {
                continue;
            }
            if (!levelImageFiles.contains(entry.mediaFile, false)) {
                levelImageFiles.add(entry.mediaFile);
            }
        }
    }

    private void collectImageFiles(JsonValue node, Array<String> output) {
        if (node == null) {
            return;
        }

        if (node.isArray()) {
            for (JsonValue value = node.child; value != null; value = value.next) {
                collectImageFiles(value, output);
            }
            return;
        }

        if (node.isObject()) {
            for (JsonValue child = node.child; child != null; child = child.next) {
                if (child.isString() && looksLikeImageField(child.name) && looksLikeImageFile(child.asString())) {
                    String relativePath = child.asString();
                    if (!output.contains(relativePath, false)) {
                        output.add(relativePath);
                    }
                }
                collectImageFiles(child, output);
            }
        }
    }

    private boolean looksLikeImageField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return fieldName.endsWith("File");
    }

    private boolean looksLikeImageFile(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toLowerCase();
        return normalized.endsWith(".png")
            || normalized.endsWith(".jpg")
            || normalized.endsWith(".jpeg")
            || normalized.endsWith(".bmp");
    }

    private boolean containsAnyToken(String normalizedValue, ObjectSet<String> tokens) {
        if (normalizedValue == null || normalizedValue.isEmpty() || tokens == null || tokens.size <= 0) {
            return false;
        }
        for (String token : tokens) {
            if (token != null && !token.isEmpty() && normalizedValue.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private void addTokens(ObjectSet<String> tokens, String raw) {
        if (tokens == null) {
            return;
        }
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return;
        }
        String[] split = normalized.split("[^a-z0-9]+");
        for (int i = 0; i < split.length; i++) {
            String token = split[i];
            if (token != null && token.length() >= 3) {
                tokens.add(token);
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class AnimationMediaEntry {
        final String normalizedName;
        final String mediaFile;

        AnimationMediaEntry(String normalizedName, String mediaFile) {
            this.normalizedName = normalizedName == null ? "" : normalizedName;
            this.mediaFile = mediaFile;
        }
    }

    private void configureFontFiltering(BitmapFont targetFont) {
        targetFont.getData().markupEnabled = false;
        targetFont.setUseIntegerPositions(false);
        for (int i = 0; i < targetFont.getRegions().size; i++) {
            targetFont.getRegions().get(i).getTexture().setFilter(
                Texture.TextureFilter.Linear,
                Texture.TextureFilter.Linear
            );
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        assetManager.dispose();
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
    }
}
