package com.mdominguez.ietiParkAndroid;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.List;

public class PlayScreen extends ScreenAdapter {
    private static final float FIXED_STEP_SECONDS = 1f / 30f;
    private static final int PLAYER_SLOTS = 8;
    private static final float TOUCH_CONTROL_MARGIN = 30f;
    private static final float JOYSTICK_BASE_RADIUS = 78f;
    private static final float JOYSTICK_KNOB_RADIUS = 30f;
    private static final float JOYSTICK_CAPTURE_RADIUS = 126f;
    private static final float ACTION_BUTTON_RADIUS = 58f;
    private static final float TOUCH_AXIS_DEAD_ZONE = 0.18f;
    private static final int MAX_TOUCH_POINTS = 20;
    private static final float PLAYER_DRAW_SIZE = 32f;
    private static final float CAT_SOURCE_BASE_SIZE = 16f;
    private static final float CARRIED_POTION_SIZE = 20f;
    // El spritesheet de la poción es grande; en el mundo la dibujamos más pequeña.
    private static final float POTION_FLOOR_SIZE = 24f;
    private static final float TREE_DRAW_SIZE = 100f;
    private static final float TREE_ALIVE_FPS = 4f;
    private static final Color HUD = Color.BLACK;
    private static final Color PANEL = Color.valueOf("07140ACC");
    private static final Color STROKE = Color.valueOf("7EE5A4CC");
    private static final Color ACCENT = Color.valueOf("35FF74DD");

    private final GameApp game;
    private final int levelIndex;
    private final String nickname;
    private final LevelData levelData;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final OrthographicCamera hudCamera = new OrthographicCamera();
    private final Viewport viewport;
    private final Viewport hudViewport = new ScreenViewport(hudCamera);
    private final LevelRenderer levelRenderer = new LevelRenderer();
    private final Array<LevelRenderer.SpriteRuntimeState> spriteRuntimeStates = new Array<>();
    private final Array<RuntimeTransform> layerRuntimeStates = new Array<>();
    private final boolean[] layerVisibilityStates;
    private final ObjectMap<String, String> animationIdByName = new ObjectMap<>();
    private final ObjectMap<Integer, Integer> playerSlotByCat = new ObjectMap<>();
    private final FloatArray animationElapsed = new FloatArray();
    private final GameInputState inputState = new GameInputState();
    private final Vector2 joystickCenter = new Vector2();
    private final Vector2 joystickKnobOffset = new Vector2();
    private final Vector2 actionButtonCenter = new Vector2();
    private final Vector2 hudTouchPoint = new Vector2();
    private final Vector3 hudTouchPoint3 = new Vector3();
    private final GlyphLayout layout = new GlyphLayout();
    private final Rectangle backButton = new Rectangle();

    private int firstPlayerSpriteIndex;
    private int carriedPotionSpriteIndex = -1;
    private int treeSpriteIndex = -1;
    private boolean treeWasOpening = false;
    private boolean treeAnimationFinished = false;
    private float treeAnimationTime = 0f;
    private int joystickPointer = -1;
    private int actionPointer = -1;
    private float sendAccumulator = 0f;
    private boolean previousJumpHeld = false;

    public PlayScreen(GameApp game, int levelIndex) {
        this(game, levelIndex, GameSession.get().getRequestedNickname());
    }

    public PlayScreen(GameApp game, int levelIndex, String nickname) {
        this.game = game;
        this.levelIndex = levelIndex;
        this.nickname = GameSession.sanitizeNickname(nickname);
        this.levelData = LevelLoader.loadLevel(levelIndex);
        this.layerVisibilityStates = buildInitialLayerVisibility(levelData);
        this.viewport = new FitViewport(levelData.viewportWidth, levelData.viewportHeight, camera);
        buildAnimationIndex();
        hideEditorCats();
        addPlayerSlots();
        addSmallPotionSprite();
        initializeRuntimeStates();
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);
        hudViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        updateTouchControlLayout();
        updateBackButtonBounds();
        applyInitialCamera();
    }

    @Override public void show() {
        Gdx.input.setInputProcessor(null);
        Gdx.input.setOnscreenKeyboardVisible(false);
        AndroidHardwareInputBridge.setCaptureEnabled(isAndroidRuntime());
        if (!GameSession.get().isConnected()) GameSession.get().connect(nickname);
    }

    @Override public void hide() {
        AndroidHardwareInputBridge.setCaptureEnabled(false);
    }

    @Override public void render(float delta) {
        updateInput();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || handleBackButton()) {
            GameSession.get().disconnect();
            game.setScreen(new MenuScreen(game));
            return;
        }

        sendAccumulator += Math.max(0f, delta);
        if (sendAccumulator >= FIXED_STEP_SECONDS || inputState.jumpPressed) {
            sendAccumulator = 0f;
            GameSession.get().sendInput(inputState.moveX, inputState.jumpPressed, inputState.jumpHeld);
        }

        applyNetworkState(delta);
        updateCamera();
        viewport.apply();
        ScreenUtils.clear(levelData.backgroundColor);
        SpriteBatch batch = game.getBatch();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        levelRenderer.render(levelData, game.getAssetManager(), batch, camera, spriteRuntimeStates, layerVisibilityStates, layerRuntimeStates);
        drawPotionOverCarrier(batch);
        batch.end();
        renderHud();
    }

    private void applyNetworkState(float delta) {
        List<GameSession.PlayerState> players = GameSession.get().snapshotPlayers();
        for (int i = firstPlayerSpriteIndex; i < firstPlayerSpriteIndex + PLAYER_SLOTS && i < spriteRuntimeStates.size; i++) {
            spriteRuntimeStates.get(i).visible = false;
        }
        GameSession.WorldState world = GameSession.get().snapshotWorld();

        float dt = Math.max(0f, delta);
        // 1) Pintamos los jugadores que el servidor dice que están en la sala.
        for (GameSession.PlayerState p : players) {
            if (p.cat < 1 || p.cat > PLAYER_SLOTS) continue;
            Integer slotIndexObj = playerSlotByCat.get(p.cat);
            if (slotIndexObj == null) continue;
            int slotIndex = slotIndexObj;
            LevelRenderer.SpriteRuntimeState state = spriteRuntimeStates.get(slotIndex);
            state.visible = true;
            state.worldX = p.x;
            state.worldY = p.y;
            state.flipX = !p.facingRight;
            String animName = (p.anim == null || p.anim.isEmpty() ? "idle" : p.anim) + "_cat" + p.cat;
            applyAnimation(slotIndex, animName, dt);
        }

        // 2) Pintamos el mundo: árbol fijo y poción en el suelo o sobre su portador.
        applyPotionAndTree(world, players);
    }

    private void applyPotionAndTree(GameSession.WorldState world, List<GameSession.PlayerState> players) {
        if (world == null) return;

        // 1) Poción en el suelo: se anima siempre mientras esté disponible.
        // Cuando alguien la lleva, se oculta la grande y se dibuja una pequeña encima del gato.
        boolean potionOnFloor = !world.potionTaken && !world.potionConsumed && findPotionCarrier(world, players) == null;
        for (int i = 0; i < levelData.sprites.size && i < spriteRuntimeStates.size; i++) {
            LevelData.LevelSprite sprite = levelData.sprites.get(i);
            String type = normalize(sprite.type + " " + sprite.name);
            LevelRenderer.SpriteRuntimeState state = spriteRuntimeStates.get(i);

            if (type.contains("potion") && i != carriedPotionSpriteIndex) {
                state.visible = potionOnFloor;
                if (potionOnFloor) {
                    state.worldX = world.potionX;
                    state.worldY = world.potionY;
                    // La poción usa frames de 45x45. No hay que aplicarle la escala de gato.
                    applyWorldAnimation(i, "potion_red", Gdx.graphics.getDeltaTime(), POTION_FLOOR_SIZE, POTION_FLOOR_SIZE);
                }
            }

            if (type.contains("tree")) {
                treeSpriteIndex = i;
                state.worldX = world.doorX + world.doorWidth * 0.5f;
                state.worldY = world.doorY + world.doorHeight;
            }
        }

        // 2) Árbol: empieza muerto y animado en bucle. Cuando la poción lo cura,
        // reproduce tree_alive lentamente una sola vez y se queda en el último frame.
        updateTreeAnimation(world);

        if (carriedPotionSpriteIndex >= 0 && carriedPotionSpriteIndex < spriteRuntimeStates.size) {
            spriteRuntimeStates.get(carriedPotionSpriteIndex).visible = false;
        }
    }

    private void updateTreeAnimation(GameSession.WorldState world) {
        if (treeSpriteIndex < 0 || treeSpriteIndex >= spriteRuntimeStates.size) return;

        if (world.doorOpen) {
            if (!world.treeOpening && !treeWasOpening) {
                treeWasOpening = true;
                treeAnimationFinished = true;
                treeAnimationTime = 999f;
            } else if (!treeWasOpening) {
                treeWasOpening = true;
                treeAnimationFinished = false;
                treeAnimationTime = 0f;
            }
            playTreeAliveOnce(treeSpriteIndex);
        } else {
            treeWasOpening = false;
            treeAnimationFinished = false;
            treeAnimationTime = 0f;
            applyWorldAnimation(treeSpriteIndex, "tree_died", Gdx.graphics.getDeltaTime(), TREE_DRAW_SIZE, TREE_DRAW_SIZE);
        }
        // Anclar el árbol por la base para que coincida con worldY = doorY + doorHeight
        LevelRenderer.SpriteRuntimeState treeState = spriteRuntimeStates.get(treeSpriteIndex);
        treeState.anchorX = 0.5f;
        treeState.anchorY = 1.0f;
    }

    private void playTreeAliveOnce(int spriteIndex) {
        String id = animationIdByName.get(normalize("tree_alive"));
        if (id == null) return;
        LevelData.AnimationClip clip = levelData.animationClips.get(id);
        if (clip == null) return;

        LevelRenderer.SpriteRuntimeState state = spriteRuntimeStates.get(spriteIndex);
        state.visible = true;
        state.animationId = id;
        state.texturePath = clip.texturePath;
        state.frameWidth = clip.frameWidth;
        state.frameHeight = clip.frameHeight;
        state.drawWidth = TREE_DRAW_SIZE;
        state.drawHeight = TREE_DRAW_SIZE;
        state.anchorX = clip.anchorX;
        state.anchorY = clip.anchorY;

        int total = totalFrames(state.texturePath, state.frameWidth, state.frameHeight);
        int start = Math.max(0, Math.min(total - 1, clip.startFrame));
        int end = Math.max(start, Math.min(total - 1, clip.endFrame));

        if (!treeAnimationFinished) {
            treeAnimationTime += Gdx.graphics.getDeltaTime();
            int frameOffset = (int)(treeAnimationTime * TREE_ALIVE_FPS);
            int frame = start + frameOffset;
            if (frame >= end) {
                frame = end;
                treeAnimationFinished = true;
            }
            state.frameIndex = frame;
        } else {
            state.frameIndex = end;
        }
    }

    private GameSession.PlayerState findPotionCarrier(GameSession.WorldState world, List<GameSession.PlayerState> players) {
        if (world == null || world.potionCarrierId == null || world.potionCarrierId.length() == 0) return null;
        for (GameSession.PlayerState p : players) {
            if (p != null && world.potionCarrierId.equals(p.id)) return p;
        }
        return null;
    }

    private void applyAnimation(int spriteIndex, String animationName, float dt) {
        String id = animationIdByName.get(normalize(animationName));
        LevelRenderer.SpriteRuntimeState state = spriteRuntimeStates.get(spriteIndex);
        if (id == null) return;
        LevelData.AnimationClip clip = levelData.animationClips.get(id);
        if (clip == null) return;
        state.animationId = id;
        state.texturePath = clip.texturePath;
        state.frameWidth = clip.frameWidth;
        state.frameHeight = clip.frameHeight;
        // Las animaciones run/jump usan frames más grandes que idle.
        // Para que el gato no parezca pequeño, mantenemos la misma escala de píxel:
        // idle 16px -> 32px, run 20px -> 40px, jump 20x24px -> 40x48px.
        state.drawWidth = PLAYER_DRAW_SIZE * Math.max(1f, clip.frameWidth) / CAT_SOURCE_BASE_SIZE;
        state.drawHeight = PLAYER_DRAW_SIZE * Math.max(1f, clip.frameHeight) / CAT_SOURCE_BASE_SIZE;
        state.anchorX = 0.5f;
        state.anchorY = 0.75f;
        float elapsed = animationElapsed.get(spriteIndex) + dt;
        animationElapsed.set(spriteIndex, elapsed);
        int total = totalFrames(state.texturePath, state.frameWidth, state.frameHeight);
        int start = Math.max(0, Math.min(total - 1, clip.startFrame));
        int end = Math.max(start, Math.min(total - 1, clip.endFrame));
        int span = Math.max(1, end - start + 1);
        int frame = start + ((int)(elapsed * Math.max(1f, clip.fps)) % span);
        state.frameIndex = frame;
    }

    private void applyWorldAnimation(int spriteIndex, String animationName, float dt, float drawWidth, float drawHeight) {
        String id = animationIdByName.get(normalize(animationName));
        if (id == null || spriteIndex < 0 || spriteIndex >= spriteRuntimeStates.size) return;

        LevelData.AnimationClip clip = levelData.animationClips.get(id);
        if (clip == null) return;

        LevelRenderer.SpriteRuntimeState state = spriteRuntimeStates.get(spriteIndex);
        state.visible = true;
        state.animationId = id;
        state.texturePath = clip.texturePath;
        state.frameWidth = clip.frameWidth;
        state.frameHeight = clip.frameHeight;
        state.drawWidth = drawWidth;
        state.drawHeight = drawHeight;
        state.anchorX = clip.anchorX;
        state.anchorY = clip.anchorY;

        float elapsed = animationElapsed.get(spriteIndex) + dt;
        animationElapsed.set(spriteIndex, elapsed);
        int total = totalFrames(state.texturePath, state.frameWidth, state.frameHeight);
        int start = Math.max(0, Math.min(total - 1, clip.startFrame));
        int end = Math.max(start, Math.min(total - 1, clip.endFrame));
        int span = Math.max(1, end - start + 1);
        state.frameIndex = start + ((int)(elapsed * Math.max(1f, clip.fps)) % span);
    }

    private int totalFrames(String path, int fw, int fh) {
        if (path == null || fw <= 0 || fh <= 0 || !game.getAssetManager().isLoaded(path, Texture.class)) return 1;
        Texture t = game.getAssetManager().get(path, Texture.class);
        return Math.max(1, (t.getWidth() / fw) * (t.getHeight() / fh));
    }

    private void updateInput() {
        inputState.reset();
        if (isAndroidRuntime()) {
            if (AndroidHardwareInputBridge.isLeftPressed()) inputState.moveX -= 1f;
            if (AndroidHardwareInputBridge.isRightPressed()) inputState.moveX += 1f;
            inputState.jumpHeld = AndroidHardwareInputBridge.isJumpHeld();
            inputState.jumpPressed = AndroidHardwareInputBridge.consumeJumpQueued();
        } else {
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) inputState.moveX -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) inputState.moveX += 1f;
            inputState.jumpHeld = Gdx.input.isKeyPressed(Input.Keys.SPACE) || Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
            inputState.jumpPressed = inputState.jumpHeld && !previousJumpHeld;
        }
        applyTouchInput();
        previousJumpHeld = inputState.jumpHeld;
    }

    private void applyTouchInput() {
        if (!shouldShowTouchControls()) return;
        updateTouchControlLayout();
        if (!isPointerStillActive(joystickPointer)) { joystickPointer = -1; joystickKnobOffset.setZero(); }
        if (!isPointerStillActive(actionPointer)) actionPointer = -1;
        for (int pointer = 0; pointer < MAX_TOUCH_POINTS; pointer++) {
            if (!Gdx.input.isTouched(pointer) || pointer == joystickPointer || pointer == actionPointer) continue;
            hudViewport.unproject(hudTouchPoint3.set(Gdx.input.getX(pointer), Gdx.input.getY(pointer), 0));
            hudTouchPoint.set(hudTouchPoint3.x, hudTouchPoint3.y);
            if (joystickPointer < 0 && hudTouchPoint.dst(joystickCenter) <= JOYSTICK_CAPTURE_RADIUS) joystickPointer = pointer;
            else if (actionPointer < 0 && hudTouchPoint.dst(actionButtonCenter) <= ACTION_BUTTON_RADIUS) actionPointer = pointer;
        }
        if (joystickPointer >= 0) {
            hudViewport.unproject(hudTouchPoint3.set(Gdx.input.getX(joystickPointer), Gdx.input.getY(joystickPointer), 0));
            joystickKnobOffset.set(hudTouchPoint3.x - joystickCenter.x, hudTouchPoint3.y - joystickCenter.y);
            if (joystickKnobOffset.len() > JOYSTICK_BASE_RADIUS) joystickKnobOffset.setLength(JOYSTICK_BASE_RADIUS);
            float axis = joystickKnobOffset.x / JOYSTICK_BASE_RADIUS;
            inputState.moveX = Math.abs(axis) < TOUCH_AXIS_DEAD_ZONE ? inputState.moveX : axis;
        }
        boolean actionHeld = actionPointer >= 0;
        inputState.jumpPressed = inputState.jumpPressed || (actionHeld && !previousJumpHeld);
        inputState.jumpHeld = inputState.jumpHeld || actionHeld;
    }

    private void renderHud() {
        hudViewport.apply();
        ShapeRenderer shapes = game.getShapeRenderer();
        shapes.setProjectionMatrix(hudCamera.combined);
        if (shouldShowTouchControls()) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(PANEL); shapes.circle(joystickCenter.x, joystickCenter.y, JOYSTICK_BASE_RADIUS, 48);
            shapes.setColor(ACCENT); shapes.circle(joystickCenter.x + joystickKnobOffset.x, joystickCenter.y + joystickKnobOffset.y, JOYSTICK_KNOB_RADIUS, 32);
            shapes.setColor(PANEL); shapes.circle(actionButtonCenter.x, actionButtonCenter.y, ACTION_BUTTON_RADIUS, 48);
            shapes.end();
            shapes.begin(ShapeRenderer.ShapeType.Line);
            shapes.setColor(STROKE); shapes.circle(joystickCenter.x, joystickCenter.y, JOYSTICK_BASE_RADIUS, 48); shapes.circle(actionButtonCenter.x, actionButtonCenter.y, ACTION_BUTTON_RADIUS, 48);
            shapes.end();
        }
        SpriteBatch batch = game.getBatch();
        BitmapFont font = game.getFont();
        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        font.getData().setScale(1.25f);
        font.setColor(HUD);
        font.draw(batch, "< MENU", backButton.x, backButton.y + backButton.height - 8);

        // En escritorio no enseñamos el botón táctil, así que tampoco escribimos JUMP.
        if (shouldShowTouchControls()) {
            font.getData().setScale(1.05f);
            font.setColor(Color.BLACK);
            layout.setText(font, "JUMP");
            font.draw(batch, layout, actionButtonCenter.x - layout.width * 0.5f, actionButtonCenter.y + layout.height * 0.5f);
        }

        // Nick arriba a la derecha para no pisar el botón de menú.
        String playerText = GameSession.get().getMyNickname() + " (" + GameSession.get().getMyCatColor() + ")";
        font.getData().setScale(1.15f);
        font.setColor(Color.BLACK);
        layout.setText(font, playerText);
        font.draw(batch, layout, hudViewport.getWorldWidth() - layout.width - 18f, hudViewport.getWorldHeight() - 18f);
        font.getData().setScale(1f); font.setColor(Color.WHITE);
        batch.end();
    }


    private void drawPotionOverCarrier(SpriteBatch batch) {
        GameSession.WorldState world = GameSession.get().snapshotWorld();
        if (world == null || world.potionConsumed) return;

        List<GameSession.PlayerState> players = GameSession.get().snapshotPlayers();
        GameSession.PlayerState carrier = findPotionCarrier(world, players);
        if (carrier == null) return;

        String texturePath = findPotionTexturePath();
        if (texturePath == null || !game.getAssetManager().isLoaded(texturePath, Texture.class)) return;

        Texture potionTexture = game.getAssetManager().get(texturePath, Texture.class);
        int frameW = 45;
        int frameH = 45;
        int cols = Math.max(1, potionTexture.getWidth() / frameW);
        int rows = Math.max(1, potionTexture.getHeight() / frameH);
        int total = Math.max(1, cols * rows);
        int frame = ((int)(animationElapsed.get(carriedPotionSpriteIndex) * 10f)) % total;
        animationElapsed.set(carriedPotionSpriteIndex, animationElapsed.get(carriedPotionSpriteIndex) + Gdx.graphics.getDeltaTime());

        TextureRegion region = new TextureRegion(
            potionTexture,
            (frame % cols) * frameW,
            (frame / cols) * frameH,
            frameW,
            frameH
        );

        float size = CARRIED_POTION_SIZE;
        float x = carrier.x - size * 0.5f;
        float yDown = carrier.y - 30f;
        float y = levelData.worldHeight - yDown - size * 0.5f;
        batch.draw(region, x, y, size, size);
    }

    private String findPotionTexturePath() {
        for (int i = 0; i < levelData.sprites.size; i++) {
            LevelData.LevelSprite s = levelData.sprites.get(i);
            String type = normalize(s.type + " " + s.name);
            if (type.contains("potion") && !type.contains("carried")) return s.texturePath;
        }
        return null;
    }

    private boolean handleBackButton() {
        if (!Gdx.input.justTouched()) return false;
        hudViewport.unproject(hudTouchPoint3.set(Gdx.input.getX(), Gdx.input.getY(), 0));
        return backButton.contains(hudTouchPoint3.x, hudTouchPoint3.y);
    }

    private void buildAnimationIndex() {
        for (ObjectMap.Entry<String, LevelData.AnimationClip> e : levelData.animationClips) {
            if (e.value != null && e.value.name != null) animationIdByName.put(normalize(e.value.name), e.value.id);
        }
    }

    private void hideEditorCats() {
        for (int i = 0; i < levelData.sprites.size; i++) {
            LevelData.LevelSprite s = levelData.sprites.get(i);
            if (normalize(s.type + " " + s.name).contains("cat")) {
                // handled through runtime state after it is built
            }
        }
    }

    private void addPlayerSlots() {
        firstPlayerSpriteIndex = levelData.sprites.size;
        for (int cat = 1; cat <= PLAYER_SLOTS; cat++) {
            String animId = animationIdByName.get(normalize("idle_cat" + cat));
            String texture = "levels/media/idle_cat" + cat + ".png";
            // Todos los gatos se dibujan al mismo tamaño aunque cambie la animación.
            float drawW = PLAYER_DRAW_SIZE, drawH = PLAYER_DRAW_SIZE, ax = 0.5f, ay = 0.75f;
            LevelData.AnimationClip clip = animId == null ? null : levelData.animationClips.get(animId);
            if (clip != null) { texture = clip.texturePath; }
            levelData.sprites.add(new LevelData.LevelSprite("player_cat" + cat, "player", 0f, -200, -200, drawW, drawH, ax, ay, false, false, 0, texture, animId));
            playerSlotByCat.put(cat, firstPlayerSpriteIndex + cat - 1);
        }
    }


    private void addSmallPotionSprite() {
        for (int i = 0; i < levelData.sprites.size; i++) {
            LevelData.LevelSprite s = levelData.sprites.get(i);
            String type = normalize(s.type + " " + s.name);
            if (type.contains("potion")) {
                carriedPotionSpriteIndex = levelData.sprites.size;
                levelData.sprites.add(new LevelData.LevelSprite(
                    "potion_carried",
                    "potion_carried",
                    s.depth,
                    -200,
                    -200,
                    CARRIED_POTION_SIZE,
                    CARRIED_POTION_SIZE,
                    0.5f,
                    0.5f,
                    false,
                    false,
                    s.frameIndex,
                    s.texturePath,
                    s.animationId
                ));
                return;
            }
        }
    }

    private void initializeRuntimeStates() {
        spriteRuntimeStates.clear(); animationElapsed.clear();
        for (int i = 0; i < levelData.sprites.size; i++) {
            LevelData.LevelSprite s = levelData.sprites.get(i);
            String kind = normalize(s.type + " " + s.name);
            boolean visible = (!kind.contains("cat") || i >= firstPlayerSpriteIndex) && !kind.contains("potion_carried");
            LevelRenderer.SpriteRuntimeState runtime = new LevelRenderer.SpriteRuntimeState(s.frameIndex, s.anchorX, s.anchorY, s.x, s.y, visible, s.flipX, s.flipY, Math.max(1, Math.round(s.width)), Math.max(1, Math.round(s.height)), s.texturePath, s.animationId);
            runtime.drawWidth = s.width;
            runtime.drawHeight = s.height;
            spriteRuntimeStates.add(runtime);
            animationElapsed.add(0f);
        }
        layerRuntimeStates.clear();
        for (int i = 0; i < levelData.layers.size; i++) layerRuntimeStates.add(new RuntimeTransform(levelData.layers.get(i).x, levelData.layers.get(i).y));
    }

    private boolean[] buildInitialLayerVisibility(LevelData level) {
        boolean[] visible = new boolean[level.layers.size];
        for (int i = 0; i < visible.length; i++) visible[i] = level.layers.get(i).visible;
        return visible;
    }

    private void applyInitialCamera() {
        camera.setToOrtho(false);
        camera.position.set(levelData.viewportX + levelData.viewportWidth * 0.5f, levelData.worldHeight - levelData.viewportY - levelData.viewportHeight * 0.5f, 0);
        camera.update();
    }

    private void updateCamera() {
        // Cámara fija: siempre se ve el nivel completo, no sigue al jugador.
        applyInitialCamera();
    }

    private void updateTouchControlLayout() {
        float w = hudViewport.getWorldWidth(), h = hudViewport.getWorldHeight();
        joystickCenter.set(TOUCH_CONTROL_MARGIN + JOYSTICK_BASE_RADIUS, TOUCH_CONTROL_MARGIN + JOYSTICK_BASE_RADIUS);
        actionButtonCenter.set(w - TOUCH_CONTROL_MARGIN - ACTION_BUTTON_RADIUS, TOUCH_CONTROL_MARGIN + ACTION_BUTTON_RADIUS);
    }

    private void updateBackButtonBounds() { backButton.set(14, hudViewport.getWorldHeight() - 56, 140, 44); }
    private boolean shouldShowTouchControls() { return isAndroidRuntime() || Gdx.input.isPeripheralAvailable(Input.Peripheral.MultitouchScreen); }
    private boolean isAndroidRuntime() { return Gdx.app.getType() == Application.ApplicationType.Android; }
    private boolean isPointerStillActive(int pointer) { return pointer >= 0 && pointer < MAX_TOUCH_POINTS && Gdx.input.isTouched(pointer); }
    private String normalize(String v) { return v == null ? "" : v.trim().toLowerCase(); }

    @Override public void resize(int width, int height) { viewport.update(width, height, false); hudViewport.update(width, height, true); updateTouchControlLayout(); updateBackButtonBounds(); }
}
