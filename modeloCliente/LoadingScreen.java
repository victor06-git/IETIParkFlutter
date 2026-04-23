package com.mdominguez.ietiParkAppMobil;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class LoadingScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float MIN_SECONDS_ON_SCREEN = 0.85f;
    private static final float VISUAL_PROGRESS_SPEED = 3.2f;

    private static final Color BACKGROUND = Color.valueOf("050A06");
    private static final Color BAR_BG = Color.valueOf("0A1A0F");
    private static final Color BAR_FILL = Color.valueOf("35FF74");
    private static final Color TEXT = Color.valueOf("35FF74");
    private static final Color SUBTEXT = Color.valueOf("21964A");

    private final GameApp game;
    private final int levelIndex;
    private final Viewport viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
    private final GlyphLayout layout = new GlyphLayout();

    private float elapsedSeconds = 0f;
    private float visualProgress = 0f;

    public LoadingScreen(GameApp game, int levelIndex) {
        this.game = game;
        this.levelIndex = levelIndex;
    }

    @Override
    public void show() {
        game.queueReferencedAssetsForLevel(levelIndex);
        elapsedSeconds = 0f;
        visualProgress = 0f;
    }

    @Override
    public void render(float delta) {
        elapsedSeconds += delta;

        boolean done = game.getAssetManager().update(17);
        float actualProgress = MathUtils.clamp(game.getAssetManager().getProgress(), 0f, 1f);
        float maxProgressForTime = MathUtils.clamp(elapsedSeconds / MIN_SECONDS_ON_SCREEN, 0f, 1f);
        float targetProgress = Math.min(actualProgress, maxProgressForTime);
        visualProgress = Math.min(targetProgress, visualProgress + Math.max(0f, delta) * VISUAL_PROGRESS_SPEED);

        if (done && elapsedSeconds >= MIN_SECONDS_ON_SCREEN && visualProgress >= 0.999f) {
            game.setScreen(new PlayScreen(game, levelIndex));
            return;
        }

        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();

        renderBar(visualProgress);
        renderText(visualProgress);
    }

    private void renderBar(float progress) {
        float clamped = MathUtils.clamp(progress, 0f, 1f);
        float barWidth = 620f;
        float barHeight = 28f;
        float x = (WORLD_WIDTH - barWidth) * 0.5f;
        float y = WORLD_HEIGHT * 0.44f;

        ShapeRenderer shapes = game.getShapeRenderer();
        shapes.setProjectionMatrix(viewport.getCamera().combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(BAR_BG);
        shapes.rect(x, y, barWidth, barHeight);
        shapes.setColor(BAR_FILL);
        shapes.rect(x, y, barWidth * clamped, barHeight);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BAR_FILL);
        shapes.rect(x, y, barWidth, barHeight);
        shapes.end();
    }

    private void renderText(float progress) {
        float clamped = MathUtils.clamp(progress, 0f, 1f);

        SpriteBatch batch = game.getBatch();
        BitmapFont font = game.getFont();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        drawCenteredText(
            batch,
            font,
            "Loading " + game.getLevelName(levelIndex),
            WORLD_HEIGHT * 0.58f,
            2f,
            TEXT
        );
        drawCenteredText(
            batch,
            font,
            (int) (clamped * 100f) + "%",
            WORLD_HEIGHT * 0.40f,
            1.5f,
            SUBTEXT
        );

        batch.end();
    }

    private void drawCenteredText(SpriteBatch batch, BitmapFont font, String text, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text);
        float x = (WORLD_WIDTH - layout.width) * 0.5f;
        font.draw(batch, layout, x, y);
        font.getData().setScale(1f);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
