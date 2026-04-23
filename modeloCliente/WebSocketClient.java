package com.mdominguez.ietiParkAppMobil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.github.czyzby.websocket.WebSocket;
import com.github.czyzby.websocket.WebSocketAdapter;
import com.github.czyzby.websocket.WebSocketHandler;
import com.github.czyzby.websocket.WebSockets;

/**
 * Cliente → Servidor:
 *   { "type": "JOIN",        "nickname": "xxx" }
 *   { "type": "MOVE",        "direction": "UP|LEFT|RIGHT", "timestamp": 123 }
 *   { "type": "GET_PLAYERS" }
 *
 * Servidor → Cliente:
 *   { "type": "WELCOME",     "msg": "..." }
 *   { "type": "JOIN_OK",     "nickname": "xxx" }
 *   { "type": "PLAYER_LIST", "players": ["p1","p2"] }
 *   { "type": "MOVE",        "nickname": "xxx", "direction": "UP", ... }
 */
public class WebSocketClient {

    // ==================== INTERFACES ====================

    public interface PlayerListListener {
        void onPlayerListUpdated(List<String> players);
    }

    public interface MessageListener {
        void onMessage(String type, JsonValue payload);
    }

    /** Notifica cambios en el estado de la conexión. */
    public interface ConnectionListener {
        /** @param connected true = conectado, false = desconectado */
        void onConnectionStateChanged(boolean connected);
    }

    // ==================== CONSTANTES DE RECONEXIÓN ====================

    private static final String SERVER_URL        = "wss://pico2.ieti.site";
    //private static final String SERVER_URL = "ws://10.0.2.2:8080"; // Descomentar si queremos localhost
    private static final float  RECONNECT_DELAY_MIN = 1f;   // segundos inicial
    private static final float  RECONNECT_DELAY_MAX = 30f;  // segundos máximo
    private static final float  RECONNECT_DELAY_FACTOR = 2f; // multiplicador (backoff exponencial)

    // ==================== CAMPOS ====================

    private WebSocket socket;
    private boolean connected      = false;
    private boolean intentionallyClosed = false; // true cuando el usuario llama a close()
    private boolean reconnecting   = false;

    private float reconnectDelayCurrent = RECONNECT_DELAY_MIN;
    private float reconnectTimer        = 0f;

    private String confirmedNickname = null;
    private final List<String> activePlayers = new ArrayList<>();
    private final JsonReader jsonReader = new JsonReader();
    private final java.util.Queue<String> pendingMessages = new java.util.ArrayDeque<>();

    private PlayerListListener playerListListener;
    private MessageListener    messageListener;
    private ConnectionListener connectionListener;

    private String confirmedCat = null;

    private final Map<String, String> activePlayerCats = new HashMap<>();


    // ==================== SETTERS DE LISTENERS ====================

    public void setPlayerListListener(PlayerListListener listener) {
        this.playerListListener = listener;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public String getConfirmedCat() { return confirmedCat; }

    public Map<String, String> getActivePlayerCats() {
        return new HashMap<>(activePlayerCats);
    }

    // ==================== CONEXIÓN ====================

    public void connect() {
        intentionallyClosed = false;
        reconnecting = false;
        reconnectTimer = 0f;
        doConnect();
    }

    private void doConnect() {
        // Cerrar socket anterior si existe
        if (socket != null) {
            try { WebSockets.closeGracefully(socket); } catch (Exception ignored) {}
            socket = null;
        }

        Gdx.app.log("WebSocketClient", "Conectando a " + SERVER_URL + " ...");

        socket = WebSockets.newSocket(SERVER_URL);
        socket.setSendGracefully(true);

        socket.addListener(new WebSocketAdapter() {

            @Override
            public boolean onOpen(WebSocket webSocket) {
                connected = true;
                reconnecting = false;
                reconnectDelayCurrent = RECONNECT_DELAY_MIN; // reset backoff
                Gdx.app.log("WebSocketClient", "Conectado al servidor");

                // Reenviar mensajes pendientes
                try {
                    while (!pendingMessages.isEmpty()) {
                        String m = pendingMessages.poll();
                        if (m == null) break;
                        webSocket.send(m);
                        Gdx.app.log("WebSocketClient", "Enviado (pendiente): " + m);
                    }
                } catch (Exception e) {
                    Gdx.app.error("WebSocketClient", "Error al enviar mensajes pendientes", e);
                }

                notifyConnectionState(true);
                return WebSocketHandler.FULLY_HANDLED;
            }

            @Override
            public boolean onClose(WebSocket webSocket, int code, String reason) {
                connected = false;
                Gdx.app.log("WebSocketClient", "Desconectado (code=" + code + "): " + reason);
                notifyConnectionState(false);

                if (!intentionallyClosed) {
                    scheduleReconnect();
                }
                return WebSocketHandler.FULLY_HANDLED;
            }

            @Override
            public boolean onMessage(WebSocket webSocket, String payload) {
                handleMessage(payload);
                return WebSocketHandler.FULLY_HANDLED;
            }

            @Override
            public boolean onError(WebSocket webSocket, Throwable error) {
                Gdx.app.error("WebSocketClient", "Error WS: " + error.getMessage());
                // onClose se disparará a continuación, que gestiona la reconexión
                return WebSocketHandler.FULLY_HANDLED;
            }
        });

        socket.connect();
    }

    /**
     * Debe llamarse desde el game loop (render) para que el timer de
     * reconexión funcione sin necesitar threads adicionales.
     * Llámalo desde GameApp.render() o desde PlayScreen/MenuScreen.render().
     */
    public void update(float delta) {
        if (!reconnecting || intentionallyClosed) return;

        reconnectTimer += delta;
        if (reconnectTimer >= reconnectDelayCurrent) {
            reconnectTimer = 0f;
            reconnecting = false;
            Gdx.app.log("WebSocketClient",
                "Intentando reconectar (próximo intervalo: "
                    + Math.min(reconnectDelayCurrent * RECONNECT_DELAY_FACTOR, RECONNECT_DELAY_MAX) + "s)...");
            // Aumentar el delay para el siguiente intento (backoff exponencial)
            reconnectDelayCurrent = Math.min(
                reconnectDelayCurrent * RECONNECT_DELAY_FACTOR,
                RECONNECT_DELAY_MAX
            );
            doConnect();
        }
    }

    private void scheduleReconnect() {
        if (intentionallyClosed) return;
        reconnecting = true;
        reconnectTimer = 0f;
        Gdx.app.log("WebSocketClient",
            "Reconexión programada en " + reconnectDelayCurrent + "s");
    }

    // ==================== MANEJO DE MENSAJES ====================

    private void handleMessage(String payload) {
        JsonValue root;
        try {
            root = jsonReader.parse(payload);
        } catch (Exception e) {
            Gdx.app.error("WebSocketClient", "JSON inválido: " + payload);
            return;
        }

        String type = root.getString("type", "");

        switch (type) {
            case "WELCOME":
                if (confirmedNickname != null && !confirmedNickname.isEmpty()
                    && confirmedCat != null && !confirmedCat.isEmpty()) {
                    sendJoin(confirmedNickname, confirmedCat);
                }
                break;

            case "JOIN_OK":
                confirmedNickname = root.getString("nickname", "");
                confirmedCat      = root.getString("cat", "");  // ← guardar cat confirmado
                notifyMessageListener(type, root);
                break;

            case "PLAYER_LIST":
                JsonValue playersArray = root.get("players");
                activePlayers.clear();
                activePlayerCats.clear(); // ← nuevo Map<String,String>
                if (playersArray != null) {
                    for (JsonValue item = playersArray.child; item != null; item = item.next) {
                        String nick = item.getString("nickname", "");
                        String cat  = item.getString("cat", "");
                        if (!nick.isEmpty()) {
                            activePlayers.add(nick);
                            activePlayerCats.put(nick, cat);
                        }
                    }
                }
                notifyPlayerList();
                notifyMessageListener(type, root);
                break;

            case "MOVE":
                String nick = root.getString("nickname", "?");
                String direction = root.getString("dir", "?"); // ← "dir" no "direction"
                Gdx.app.log("WebSocketClient", "MOVE de " + nick + ": " + direction);
                notifyMessageListener(type, root);
                break;

            case "DOOR_STATE":
                notifyMessageListener(type, root);
                break;

            default:
                Gdx.app.log("WebSocketClient", "Tipo de mensaje desconocido: " + type);
                break;
        }
    }

    // ==================== NOTIFICACIONES ====================

    private void notifyPlayerList() {
        if (playerListListener == null) return;
        final List<String> snapshot = new ArrayList<>(activePlayers);
        Gdx.app.postRunnable(() -> playerListListener.onPlayerListUpdated(snapshot));
    }

    private void notifyMessageListener(String type, JsonValue payload) {
        if (messageListener == null) return;
        final String typeCopy    = type;
        final String payloadCopy = payload.toString();
        Gdx.app.postRunnable(() -> {
            try {
                JsonValue parsed = new JsonReader().parse(payloadCopy);
                messageListener.onMessage(typeCopy, parsed);
            } catch (Exception e) {
                Gdx.app.error("WebSocketClient", "Error notificando mensaje", e);
            }
        });
    }

    private void notifyConnectionState(boolean isConnected) {
        if (connectionListener == null) return;
        Gdx.app.postRunnable(() -> connectionListener.onConnectionStateChanged(isConnected));
    }

    // ==================== MENSAJES SALIENTES ====================

    public void sendJoin(String nickname, String cat) {
        send("{\"type\":\"JOIN\","
            + "\"nickname\":\"" + escapeJson(nickname) + "\","
            + "\"cat\":\"" + escapeJson(cat) + "\"}");
    }

    // Lo implementaremos si no nos funciona el cambio en el server para detectar conexiones muertas
    public void sendResetPlayers() {
        send("{\"type\":\"RESET_PLAYERS\"}");
    }

    public void sendLeave() {
        if (confirmedNickname != null && !confirmedNickname.isEmpty()) {
            // Enviamos de forma síncrona y directa, sin encolar,
            // porque justo después nos desconectamos
            String msg = "{\"type\":\"LEAVE\",\"nickname\":\""
                + escapeJson(confirmedNickname) + "\"}";
            if (socket != null && connected) {
                try {
                    socket.send(msg);
                    Gdx.app.log("WebSocketClient", "LEAVE enviado: " + confirmedNickname);
                } catch (Exception e) {
                    Gdx.app.error("WebSocketClient", "Error enviando LEAVE", e);
                }
            }
            confirmedNickname = null; // limpiar nick local
        }
    }

    public void sendMove(String dir, float x, float y, String anim, int frame) {
        send("{\"type\":\"MOVE\","
            + "\"dir\":\"" + escapeJson(dir) + "\","
            + "\"x\":" + (int)x + ","
            + "\"y\":" + (int)y + ","
            + "\"anim\":\"" + escapeJson(anim) + "\","
            + "\"frame\":" + frame + "}");
    }

    public void sendGetPlayers() {
        send("{\"type\":\"GET_PLAYERS\"}");
    }

    private void send(String message) {
        if (socket != null && connected) {
            try {
                socket.send(message);
            } catch (Exception e) {
                Gdx.app.error("WebSocketClient", "Error enviando, encolando: " + message, e);
                pendingMessages.add(message);
            }
        } else {
            pendingMessages.add(message);
            Gdx.app.log("WebSocketClient", "Sin conexión, encolado: " + message);
            // Si no estamos ya intentando reconectar, programar una reconexión
            if (!reconnecting && !intentionallyClosed) {
                scheduleReconnect();
            }
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    // ==================== GETTERS ====================

    public boolean isConnected()           { return connected; }
    public boolean isReconnecting()        { return reconnecting; }
    public String  getConfirmedNickname()  { return confirmedNickname; }
    public List<String> getActivePlayers() { return new ArrayList<>(activePlayers); }

    /** Devuelve el delay actual de reconexión en segundos (útil para mostrar en UI). */
    public float getReconnectDelay()       { return reconnectDelayCurrent; }

    /** Progreso [0..1] del timer de reconexión actual (útil para barra de progreso). */
    public float getReconnectProgress() {
        if (!reconnecting || reconnectDelayCurrent <= 0f) return 0f;
        return Math.min(reconnectTimer / reconnectDelayCurrent, 1f);
    }

    // ==================== CIERRE ====================

    public void close() {
        intentionallyClosed = true;
        reconnecting = false;
        connected = false;
        if (socket != null) {
            WebSockets.closeGracefully(socket);
            socket = null;
        }
    }
}
