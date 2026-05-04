package com.mdominguez.ietiParkAndroid;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.github.czyzby.websocket.WebSocket;
import com.github.czyzby.websocket.WebSocketListener;
import com.github.czyzby.websocket.WebSockets;

import java.util.ArrayList;
import java.util.List;

public final class GameWebSocketClient {
    private final String url;
    private final String nickname;
    private final boolean viewer;
    private WebSocket socket;

    public GameWebSocketClient(String url, String nickname) {
        this(url, nickname, false);
    }

    public GameWebSocketClient(String url, String nickname, boolean viewer) {
        this.url = url;
        this.nickname = GameSession.sanitizeNickname(nickname);
        this.viewer = viewer;
    }

    public void connectAsync() {
        try {
            socket = WebSockets.newSocket(url);
            socket.setSendGracefully(true);
            socket.setSerializeAsString(true);
            socket.addListener(new WebSocketListener() {
                @Override
                public boolean onOpen(WebSocket webSocket) {
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            GameSession.get().onConnected();
                        }
                    });
                    if (viewer) {
                        sendRaw("{\"type\":\"JOIN\",\"nickname\":\"viewer\",\"client\":\"viewer\",\"viewer\":true}");
                    } else {
                        sendRaw("{\"type\":\"JOIN\",\"nickname\":\"" + escape(nickname) + "\",\"client\":\"libgdx\"}");
                    }
                    return WebSocketListener.FULLY_HANDLED;
                }

                @Override
                public boolean onClose(WebSocket webSocket, int closeCode, String reason) {
                    final String msg = reason == null || reason.trim().isEmpty()
                        ? "Desconectado del servidor"
                        : "Desconectado: " + reason;
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            GameSession.get().onDisconnected(msg);
                        }
                    });
                    return WebSocketListener.FULLY_HANDLED;
                }

                @Override
                public boolean onMessage(WebSocket webSocket, String packet) {
                    handleTextMessage(packet);
                    return WebSocketListener.FULLY_HANDLED;
                }

                @Override
                public boolean onMessage(WebSocket webSocket, byte[] packet) {
                    if (packet != null) {
                        handleTextMessage(new String(packet));
                    }
                    return WebSocketListener.FULLY_HANDLED;
                }

                @Override
                public boolean onError(WebSocket webSocket, final Throwable error) {
                    Gdx.app.error("GameWebSocketClient", "Error WebSocket", error);
                    Gdx.app.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            String detail = error == null ? "desconocido" : error.getMessage();
                            GameSession.get().onDisconnected("Error WebSocket: " + detail);
                        }
                    });
                    return WebSocketListener.FULLY_HANDLED;
                }
            });

            // La librería no ofrece connectAsync(). connect() abre la conexión y dispara callbacks.
            // En la práctica es válido llamarlo desde el flujo de pantalla, igual que en los ejemplos de la librería.
            socket.connect();
        } catch (final Exception ex) {
            Gdx.app.error("GameWebSocketClient", "No se pudo iniciar WebSocket", ex);
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    GameSession.get().onDisconnected("No se pudo conectar: " + ex.getMessage());
                }
            });
        }
    }

    public boolean isOpen() {
        return socket != null && socket.isOpen();
    }

    public void sendInput(float moveX, boolean jumpPressed, boolean jumpHeld) {
        int mx = moveX < -0.12f ? -1 : (moveX > 0.12f ? 1 : 0);
        sendRaw("{\"type\":\"INPUT\",\"moveX\":" + mx
            + ",\"jumpPressed\":" + jumpPressed
            + ",\"jumpHeld\":" + jumpHeld + "}");
    }

    public void closeGracefully() {
        try {
            if (isOpen()) {
                sendRaw("{\"type\":\"LEAVE\"}");
            }
            WebSockets.closeGracefully(socket);
        } catch (Exception ignored) {
        } finally {
            socket = null;
        }
    }

    private void sendRaw(String json) {
        try {
            if (socket != null && socket.isOpen()) {
                socket.send(json);
            }
        } catch (Exception ex) {
            Gdx.app.error("GameWebSocketClient", "No se pudo enviar: " + json, ex);
        }
    }

    private void handleTextMessage(String message) {
        try {
            JsonValue root = new JsonReader().parse(message);
            String type = root.getString("type", "");

            if ("JOIN_OK".equals(type)) {
                final String id = root.getString("id", "");
                final String nick = root.getString("nickname", nickname);
                final int cat = root.getInt("cat", 1);
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        GameSession.get().onJoinOk(id, nick, cat);
                    }
                });
                return;
            }

            if ("PLAYER_LIST".equals(type) || "STATE".equals(type)) {
                final List<GameSession.PlayerState> players = parsePlayers(root.get("players"));
                final GameSession.WorldState world = parseWorld(root.get("world"));
                Gdx.app.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        GameSession.get().onPlayerList(players);
                        if (world != null) {
                            GameSession.get().onWorldState(world);
                        }
                    }
                });
            }
        } catch (Exception ex) {
            Gdx.app.error("GameWebSocketClient", "Mensaje WS inválido: " + message, ex);
        }
    }

    private static List<GameSession.PlayerState> parsePlayers(JsonValue arr) {
        ArrayList<GameSession.PlayerState> list = new ArrayList<GameSession.PlayerState>();
        if (arr == null || !arr.isArray()) return list;
        for (JsonValue p = arr.child; p != null; p = p.next) {
            GameSession.PlayerState ps = new GameSession.PlayerState();
            ps.id = p.getString("id", p.getString("nickname", ""));
            ps.nickname = p.getString("nickname", ps.id);
            ps.cat = p.getInt("cat", 1);
            ps.x = p.getFloat("x", 40f);
            ps.y = p.getFloat("y", 145f);
            ps.vx = p.getFloat("vx", 0f);
            ps.vy = p.getFloat("vy", 0f);
            ps.anim = p.getString("anim", "idle");
            ps.facingRight = p.getBoolean("facingRight", true);
            ps.grounded = p.getBoolean("grounded", false);
            ps.viewer = p.getBoolean("viewer", false);
            ps.hasPotion = p.getBoolean("hasPotion", false);
            ps.crossedDoor = p.getBoolean("crossedDoor", false);
            list.add(ps);
        }
        return list;
    }

    private static GameSession.WorldState parseWorld(JsonValue node) {
        if (node == null || !node.isObject()) return null;
        GameSession.WorldState w = new GameSession.WorldState();
        w.potionTaken = node.getBoolean("potionTaken", false);
        w.doorOpen = node.getBoolean("doorOpen", false);
        w.treeOpening = node.getBoolean("treeOpening", false);
        w.potionConsumed = node.getBoolean("potionConsumed", false);
        w.potionCarrierId = node.getString("potionCarrierId", "");
        w.levelIndex = node.getInt("levelIndex", w.levelIndex);
        w.potionX = node.getFloat("potionX", w.potionX);
        w.potionY = node.getFloat("potionY", w.potionY);
        w.doorX = node.getFloat("doorX", w.doorX);
        w.doorY = node.getFloat("doorY", w.doorY);
        w.doorWidth = node.getFloat("doorWidth", w.doorWidth);
        w.doorHeight = node.getFloat("doorHeight", w.doorHeight);
        w.platformX = node.getFloat("platformX", w.platformX);
        w.platformY = node.getFloat("platformY", w.platformY);
        w.platformWidth = node.getFloat("platformWidth", w.platformWidth);
        w.platformHeight = node.getFloat("platformHeight", w.platformHeight);
        w.platformActive = node.getBoolean("platformActive", false);
        w.buttonX = node.getFloat("buttonX", w.buttonX);
        w.buttonY = node.getFloat("buttonY", w.buttonY);
        w.buttonPressed = node.getBoolean("buttonPressed", false);
        w.levelUnlocked = node.getBoolean("levelUnlocked", false);
        w.allPlayersPassed = node.getBoolean("allPlayersPassed", false);
        w.shouldChangeScreen = node.getBoolean("shouldChangeScreen", false);
        w.totalPlayers = node.getInt("totalPlayers", 0);
        w.passedPlayers = node.getInt("passedPlayers", 0);
        w.changeReason = node.getString("changeReason", "");
        return w;
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
