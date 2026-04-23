package com.mdominguez.ietiParkAppMobil;

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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.List;

public class MenuScreen extends ScreenAdapter implements WebSocketClient.PlayerListListener {

    private static final float WORLD_WIDTH  = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private static final Color BACKGROUND     = Color.valueOf("000000");
    private static final Color PRIMARY        = Color.valueOf("35FF74");
    private static final Color DIM            = Color.valueOf("146F34");
    private static final Color FIELD_BG       = Color.valueOf("0E1E12");
    private static final Color FIELD_BORDER   = Color.valueOf("21964A");
    private static final Color FIELD_ACTIVE   = Color.valueOf("35FF74");
    private static final Color PLAY_BG        = Color.valueOf("0E3320");
    private static final Color PLAY_BORDER    = Color.valueOf("35FF74");
    private static final Color PLAYER_COLOR   = Color.valueOf("23AA54");
    private static final Color FOOTER         = Color.valueOf("21964A");
    private static final Color STATUS_OK      = Color.valueOf("35FF74");
    private static final Color STATUS_KO      = Color.valueOf("FF4444");
    private static final Color WAITING_COLOR  = Color.valueOf("FFD700");

    private static final float FIELD_W        = 420f;
    private static final float FIELD_H        = 56f;
    private static final float PLAY_W         = 200f;
    private static final float PLAY_H         = 60f;
    private static final float BLINK_INTERVAL = 0.5f;

    private final GameApp game;
    private final Viewport viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
    private final Vector3 pointer = new Vector3();
    private final GlyphLayout layout = new GlyphLayout();

    private final StringBuilder nickname = new StringBuilder();
    private boolean fieldFocused = true;
    private float blinkAccumulator = 0f;
    private boolean cursorVisible = true;

    private final Array<String> activePlayers = new Array<>();

    // Estado de espera del JOIN_OK
    private boolean waitingForJoin = false;
    private String lastSentNickname = "";
    private float waitingTime = 0f;
    private static final float JOIN_TIMEOUT = 5f; // 5 segundos máximo de espera

    private final Rectangle fieldRect = new Rectangle();
    private final Rectangle playRect  = new Rectangle();

    private String selectedCat = null;

    private final InputAdapter inputAdapter = new InputAdapter() {

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                onPlayPressed();
                return true;
            }
            if (keycode == Input.Keys.BACKSPACE && nickname.length() > 0) {
                nickname.deleteCharAt(nickname.length() - 1);
                return true;
            }
            return false;
        }

        @Override
        public boolean keyTyped(char character) {
            if (nickname.length() < 16 && isPrintable(character)) {
                nickname.append(character);
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointerId, int button) {
            if (button != Input.Buttons.LEFT) return false;
            viewport.unproject(pointer.set(screenX, screenY, 0f));

            if (fieldRect.contains(pointer.x, pointer.y)) {
                fieldFocused = true;
                Gdx.input.setOnscreenKeyboardVisible(true);
                return true;
            }
            if (playRect.contains(pointer.x, pointer.y)) {
                onPlayPressed();
                return true;
            }
            fieldFocused = false;
            return false;
        }
    };

    public MenuScreen(GameApp game) {
        this.game = game;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(inputAdapter);

        // Configurar listener para mensajes del servidor
        game.getWsClient().setMessageListener(this::handleServerMessage);
        game.getWsClient().setPlayerListListener(this);

        //game.getWsClient().sendResetPlayers(); //activamos si necesitamos hacer un reset de conexiones muertas
        game.getWsClient().sendGetPlayers();
        updatePlayersFromClient();
        buildLayout();

        waitingForJoin = false;
    }

    @Override
    public void hide() {
        game.getWsClient().setPlayerListListener(null);
        game.getWsClient().setMessageListener(null);
    }

    // Manejar mensajes del servidor (JOIN_OK, etc.)
    private void handleServerMessage(String type, JsonValue payload) {
        if ("JOIN_OK".equals(type) && waitingForJoin) {
            String confirmedNick = payload.getString("nickname", "");
            String confirmedCat  = payload.getString("cat", "");
            game.setPlayerNickname(confirmedNick);
            game.setSelectedCat(confirmedCat); // ← guardar gato confirmado
            Gdx.app.postRunnable(() -> game.setScreen(new LoadingScreen(game, 0)));
            waitingForJoin = false;
        }
    }

    @Override
    public void onPlayerListUpdated(List<String> players) {
        activePlayers.clear();
        for (String p : players) activePlayers.add(p);
    }

    @Override
    public void render(float delta) {
        updateBlink(delta);

        // Timeout para JOIN
        if (waitingForJoin) {
            waitingTime += delta;
            if (waitingTime > JOIN_TIMEOUT) {
                Gdx.app.error("MenuScreen", "Timeout esperando JOIN_OK");
                waitingForJoin = false;
            }
        }

        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();

        ShapeRenderer shapes = game.getShapeRenderer();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        renderShapes(shapes);

        SpriteBatch batch = game.getBatch();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        renderTexts(batch);
        batch.end();
    }

    private void renderShapes(ShapeRenderer shapes) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(FIELD_BG);
        shapes.rect(fieldRect.x, fieldRect.y, fieldRect.width, fieldRect.height);

        // Botón PLAY deshabilitado visualmente si estamos esperando
        if (waitingForJoin) {
            shapes.setColor(0.2f, 0.2f, 0.2f, 0.5f);
        } else {
            shapes.setColor(PLAY_BG);
        }
        shapes.rect(playRect.x, playRect.y, playRect.width, playRect.height);

        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);

        shapes.setColor(fieldFocused ? FIELD_ACTIVE : FIELD_BORDER);
        shapes.rect(fieldRect.x, fieldRect.y, fieldRect.width, fieldRect.height);

        shapes.setColor(PLAY_BORDER);
        shapes.rect(playRect.x, playRect.y, playRect.width, playRect.height);

        shapes.end();
    }

    private void renderTexts(SpriteBatch batch) {
        BitmapFont font = game.getFont();
        float cx = WORLD_WIDTH * 0.5f;

        drawCentered(batch, font, "IETIPark 2", WORLD_HEIGHT * 0.88f, 3.0f, PRIMARY);

        boolean connected = game.getWsClient().isConnected();
        String statusText;
        Color statusColor;

        if (waitingForJoin) {
            statusText = "Conectando al servidor...";
            statusColor = WAITING_COLOR;
        } else if (game.getWsClient().isReconnecting()) {
            // Mostrar cuenta atrás
            int secsLeft = (int) Math.ceil(
                game.getWsClient().getReconnectDelay() * (1f - game.getWsClient().getReconnectProgress())
            );
            statusText = "Reconectando en " + secsLeft + "s...";
            statusColor = WAITING_COLOR;
        } else {
            statusText = connected ? "Servidor conectado" : "Servidor desconectado";
            statusColor = connected ? STATUS_OK : STATUS_KO;
        }
        drawCentered(batch, font, statusText, WORLD_HEIGHT * 0.78f, 1.3f, statusColor);

        drawCentered(batch, font, "Introduce tu nickname", fieldRect.y + fieldRect.height + 36f, 1.6f, DIM);

        String displayText = nickname.toString() + (fieldFocused && cursorVisible && !waitingForJoin ? "|" : "");
        if (displayText.isEmpty()) displayText = fieldFocused && cursorVisible && !waitingForJoin ? "|" : "";
        if (!fieldFocused && nickname.length() == 0) {
            drawCentered(batch, font, "Tu nick aquí...", fieldRect.y + fieldRect.height * 0.65f, 1.6f, DIM);
        } else {
            drawCentered(batch, font, displayText, fieldRect.y + fieldRect.height * 0.65f, 1.8f, FIELD_ACTIVE);
        }

        boolean salaLlena = pickAvailableCat() == null;

        String buttonText;
        if (waitingForJoin)  buttonText = "CONECTANDO...";
        else if (salaLlena)  buttonText = "SALA LLENA";
        else                 buttonText = "PLAY";

        Color buttonColor = salaLlena ? DIM : PRIMARY;
        drawCentered(batch, font, buttonText,
            playRect.y + playRect.height * 0.65f, 2.0f, buttonColor);

        float playersTopY = playRect.y - 40f;
        int count = activePlayers.size;
        drawCentered(batch, font, "Jugadores en sala: " + count, playersTopY, 1.4f, DIM);

        float listY = playersTopY - 36f;
        for (int i = 0; i < activePlayers.size; i++) {
            String player = activePlayers.get(i);
            String cat    = game.getWsClient().getActivePlayerCats().get(player);
            String label  = "• " + player + (cat != null ? " (" + cat + ")" : "");
            Color playerColor = player.equals(game.getPlayerNickname()) ? PRIMARY : PLAYER_COLOR;
            drawCentered(batch, font, label, listY, 1.3f, playerColor);
            listY -= 30f;
        }
        if (activePlayers.size == 0) {
            drawCentered(batch, font, "(ninguno todavía)", listY, 1.2f, DIM);
        }

        drawCentered(batch, font,
            "Escribe tu nick y pulsa PLAY o ENTER",
            36f, 1.1f, FOOTER);
    }

    private void onPlayPressed() {
        if (waitingForJoin) return;
        if (pickAvailableCat() == null) return;

        String nick = nickname.toString().trim();
        if (nick.isEmpty()) {
            fieldFocused = true;
            return;
        }

        if (!game.getWsClient().isConnected()) {
            Gdx.app.error("MenuScreen", "No hay conexión al servidor");
            return;
        }

        // Elegir gato disponible
        selectedCat = pickAvailableCat();
        if (selectedCat == null) {
            Gdx.app.log("MenuScreen", "Sala llena");
            return;
        }

        lastSentNickname = nick;
        game.setPlayerNickname(nick);
        game.getWsClient().sendJoin(nick, selectedCat); // ← con gato
        waitingForJoin = true;
        waitingTime = 0f;
    }

    private String pickAvailableCat() {
        java.util.Map<String, String> usedCats = game.getWsClient().getActivePlayerCats();
        java.util.Set<String> usedValues = new java.util.HashSet<>(usedCats.values());
        String[] allCats = {"cat1","cat2","cat3","cat4","cat5","cat6","cat7","cat8"};
        for (String cat : allCats) {
            if (!usedValues.contains(cat)) return cat;
        }
        return null;
    }

    private void buildLayout() {
        float cx = WORLD_WIDTH * 0.5f;
        float fieldY = WORLD_HEIGHT * 0.54f;
        fieldRect.set(cx - FIELD_W * 0.5f, fieldY, FIELD_W, FIELD_H);

        float playY = fieldY - FIELD_H - 40f;
        playRect.set(cx - PLAY_W * 0.5f, playY, PLAY_W, PLAY_H);
    }

    private void updateBlink(float delta) {
        blinkAccumulator += delta;
        if (blinkAccumulator >= BLINK_INTERVAL) {
            blinkAccumulator -= BLINK_INTERVAL;
            cursorVisible = !cursorVisible;
        }
    }

    private void updatePlayersFromClient() {
        activePlayers.clear();
        for (String p : game.getWsClient().getActivePlayers()) activePlayers.add(p);
    }

    private void drawCentered(SpriteBatch batch, BitmapFont font, String text, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, layout, (WORLD_WIDTH - layout.width) * 0.5f, y);
        font.getData().setScale(1f);
    }

    private boolean isPrintable(char c) {
        return c >= 32 && c < 127;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        buildLayout();
    }
}
