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

import java.util.List;

public class MenuScreen extends ScreenAdapter {
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final Color BG = Color.valueOf("061007");
    private static final Color PRIMARY = Color.valueOf("35FF74");
    private static final Color DIM = Color.valueOf("21964A");
    private static final Color PANEL = Color.valueOf("0E1E12");
    private static final Color PANEL_DARK = Color.valueOf("07140A");

    private final GameApp game;
    private final Viewport viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 pointer = new Vector3();
    private final Rectangle nicknameBox = new Rectangle(340, 440, 600, 64);
    private final Rectangle playButton = new Rectangle(490, 340, 300, 74);
    private final StringBuilder nickname = new StringBuilder("Player");
    private boolean editingNickname = true;
    private float refreshTimer = 0f;

    private final InputAdapter input = new InputAdapter() {
        @Override public boolean keyTyped(char character) {
            if (!editingNickname) return false;
            if (character == '\b') {
                if (nickname.length() > 0) nickname.deleteCharAt(nickname.length() - 1);
                return true;
            }
            if (character == '\r' || character == '\n') {
                startGame();
                return true;
            }
            if (nickname.length() < 16 && (Character.isLetterOrDigit(character) || character == '_' || character == '-')) {
                nickname.append(character);
                return true;
            }
            return false;
        }

        @Override public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                startGame();
                return true;
            }
            return false;
        }

        @Override public boolean touchDown(int screenX, int screenY, int pointerId, int button) {
            viewport.unproject(pointer.set(screenX, screenY, 0));
            editingNickname = nicknameBox.contains(pointer.x, pointer.y);
            if (playButton.contains(pointer.x, pointer.y)) {
                startGame();
                return true;
            }
            return true;
        }
    };

    public MenuScreen(GameApp game) { this.game = game; }

    @Override public void show() {
        Gdx.input.setInputProcessor(input);
        Gdx.input.setOnscreenKeyboardVisible(true);
        // En el menu entramos como visor: vemos la lista, pero no aparecemos como gato.
        GameSession.get().connectAsViewer();
    }

    @Override public void hide() { Gdx.input.setOnscreenKeyboardVisible(false); }

    @Override public void render(float delta) {
        refreshTimer += Math.max(0f, delta);
        Gdx.gl.glClearColor(BG.r, BG.g, BG.b, BG.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();

        ShapeRenderer shapes = game.getShapeRenderer();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL_DARK); shapes.rect(250, 125, 780, 470);
        shapes.setColor(PANEL); shapes.rect(nicknameBox.x, nicknameBox.y, nicknameBox.width, nicknameBox.height);
        shapes.setColor(PRIMARY); shapes.rect(playButton.x, playButton.y, playButton.width, playButton.height);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(editingNickname ? PRIMARY : DIM); shapes.rect(nicknameBox.x, nicknameBox.y, nicknameBox.width, nicknameBox.height);
        shapes.setColor(PRIMARY); shapes.rect(playButton.x, playButton.y, playButton.width, playButton.height);
        shapes.end();

        SpriteBatch batch = game.getBatch();
        BitmapFont font = game.getFont();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        draw(font, batch, "IETI PARK", 0, 635, 3f, PRIMARY, true);
        draw(font, batch, "Nickname", nicknameBox.x, nicknameBox.y + 105, 1.3f, DIM, false);
        draw(font, batch, nickname.toString() + (editingNickname && ((int)(refreshTimer * 2) % 2 == 0) ? "_" : ""), nicknameBox.x + 24, nicknameBox.y + 43, 1.75f, Color.WHITE, false);
        draw(font, batch, "PLAY", 0, playButton.y + 49, 2.1f, Color.valueOf("061007"), true);
        draw(font, batch, "Pulsa PLAY para entrar en la sala", 0, 300, 1.15f, DIM, true);
        draw(font, batch, "Jugadores en partida:", 350, 245, 1.1f, PRIMARY, false);
        drawPlayerList(font, batch, 350, 212);
        draw(font, batch, "Servidor: " + GameSession.SERVER_URL + "   WebSocket/WSS", 0, 72, 1.05f, DIM, true);
        batch.end();
    }


    private void drawPlayerList(BitmapFont font, SpriteBatch batch, float x, float y) {
        List<GameSession.PlayerState> players = GameSession.get().snapshotPlayers();
        if (players.isEmpty()) {
            draw(font, batch, "No hay jugadores conectados", x, y, 1.0f, DIM, false);
            return;
        }
        for (int i = 0; i < players.size() && i < GameSession.MAX_PLAYERS; i++) {
            GameSession.PlayerState player = players.get(i);
            String text = player.nickname + " (cat" + player.cat + " " + GameSession.catColor(player.cat) + ")";
            draw(font, batch, text, x, y - i * 26f, 1.0f, Color.WHITE, false);
        }
    }

    private void startGame() {
        String clean = GameSession.sanitizeNickname(nickname.toString());
        GameSession.get().connect(clean);
        game.setScreen(new LoadingScreen(game, 0, clean));
    }

    private void draw(BitmapFont font, SpriteBatch batch, String text, float x, float y, float scale, Color color, boolean centered) {
        font.getData().setScale(scale); font.setColor(color); layout.setText(font, text);
        float drawX = centered ? (WORLD_WIDTH - layout.width) * 0.5f : x;
        font.draw(batch, layout, drawX, y);
        font.getData().setScale(1f); font.setColor(Color.WHITE);
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
