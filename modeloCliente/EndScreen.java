package com.mdominguez.ietiParkAndroid;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public final class EndScreen extends ScreenAdapter {
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final Color BG = Color.valueOf("061007");
    private static final Color PRIMARY = Color.valueOf("35FF74");
    private static final Color DIM = Color.valueOf("21964A");
    private static final Color PANEL = Color.valueOf("0E1E12");
    private static final Color PANEL_DARK = Color.valueOf("07140A");

    private final GameApp game;
    private final boolean win;
    private final String nickname;
    private final Viewport viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 pointer = new Vector3();
    private final Rectangle menuButton = new Rectangle(440, 190, 400, 78);

    private final InputAdapter input = new InputAdapter() {
        @Override public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.ESCAPE) {
                goToMenu();
                return true;
            }
            return false;
        }

        @Override public boolean touchDown(int screenX, int screenY, int pointerId, int button) {
            viewport.unproject(pointer.set(screenX, screenY, 0));
            if (menuButton.contains(pointer.x, pointer.y)) {
                goToMenu();
            }
            return true;
        }
    };

    public EndScreen(GameApp game, boolean win, String nickname) {
        this.game = game;
        this.win = win;
        this.nickname = GameSession.sanitizeNickname(nickname);
    }

    @Override public void show() {
        Gdx.input.setInputProcessor(input);
        Gdx.input.setOnscreenKeyboardVisible(false);
    }

    @Override public void render(float delta) {
        Gdx.gl.glClearColor(BG.r, BG.g, BG.b, BG.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();

        ShapeRenderer shapes = game.getShapeRenderer();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL_DARK);
        shapes.rect(260, 140, 760, 440);
        shapes.setColor(PANEL);
        shapes.rect(290, 170, 700, 380);
        shapes.setColor(PRIMARY);
        shapes.rect(menuButton.x, menuButton.y, menuButton.width, menuButton.height);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(PRIMARY);
        shapes.rect(260, 140, 760, 440);
        shapes.rect(menuButton.x, menuButton.y, menuButton.width, menuButton.height);
        shapes.end();

        SpriteBatch batch = game.getBatch();
        BitmapFont font = game.getFont();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        font.getData().setScale(3.0f);
        font.setColor(PRIMARY);
        String title = win ? "VICTORIA" : "GAME OVER";
        layout.setText(font, title);
        font.draw(batch, layout, WORLD_WIDTH * 0.5f - layout.width * 0.5f, 500);

        font.getData().setScale(1.45f);
        font.setColor(DIM);
        String subtitle = win
            ? "Todos los gatos han cruzado el bosque."
            : "La partida ha terminado.";
        layout.setText(font, subtitle);
        font.draw(batch, layout, WORLD_WIDTH * 0.5f - layout.width * 0.5f, 410);

        font.getData().setScale(1.2f);
        font.setColor(DIM);
        String playerText = nickname == null || nickname.length() == 0 ? "" : "Jugador: " + nickname;
        layout.setText(font, playerText);
        font.draw(batch, layout, WORLD_WIDTH * 0.5f - layout.width * 0.5f, 355);

        font.getData().setScale(1.45f);
        font.setColor(Color.BLACK);
        layout.setText(font, "VOLVER AL MENU");
        font.draw(batch, layout, menuButton.x + menuButton.width * 0.5f - layout.width * 0.5f, menuButton.y + 50);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void goToMenu() {
        GameSession.get().disconnect();
        game.setScreen(new MenuScreen(game));
    }

    @Override public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
