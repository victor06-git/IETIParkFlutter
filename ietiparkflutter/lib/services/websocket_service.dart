import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

/// Datos de un jugador remoto recibidos del server.
class RemotePlayer {
  String nickname;
  String cat;
  double x;
  double y;
  String anim;
  int frame;
  String dir;

  RemotePlayer({
    required this.nickname,
    this.cat = '',
    this.x = 0,
    this.y = 0,
    this.anim = 'idle',
    this.frame = 0,
    this.dir = 'RIGHT',
  });
}

/// Estado de una puerta gestionada por el server.
class DoorState {
  final int index;
  final double x, y, w, h;
  bool open;

  DoorState({
    required this.index,
    required this.x,
    required this.y,
    required this.w,
    required this.h,
    this.open = false,
  });
}

class WebSocketService extends ChangeNotifier {
  WebSocketChannel? _channel;
  bool _connected = false;
  String? _confirmedNickname;
  Timer? _reconnectTimer;

  final String serverUrl;
  final Map<String, RemotePlayer> remotePlayers = {};
  final List<DoorState> doors = [];

  bool get connected => _connected;
  String? get confirmedNickname => _confirmedNickname;

  WebSocketService({required this.serverUrl});

  void connect() {
    _doConnect();
  }

  void _doConnect() {
    try {
      _channel = WebSocketChannel.connect(Uri.parse(serverUrl));
      _connected = true;
      notifyListeners();
      debugPrint('[WS] Conectado a $serverUrl');

      _channel!.stream.listen(
        (data) => _handleMessage(data as String),
        onError: (error) {
          debugPrint('[WS] Error: $error');
          _onDisconnected();
        },
        onDone: () {
          debugPrint('[WS] Conexión cerrada');
          _onDisconnected();
        },
      );
    } catch (e) {
      debugPrint('[WS] Error conectando: $e');
      _onDisconnected();
    }
  }

  void _onDisconnected() {
    _connected = false;
    notifyListeners();
    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(const Duration(seconds: 3), () {
      debugPrint('[WS] Intentando reconectar...');
      _doConnect();
    });
  }

  void _handleMessage(String payload) {
    try {
      final msg = jsonDecode(payload) as Map<String, dynamic>;
      final type = msg['type'] as String? ?? '';

      switch (type) {
        case 'WELCOME':
          debugPrint('[WS] WELCOME recibido');
          // Si ya teníamos nickname, re-join automático
          if (_confirmedNickname != null) {
            sendJoin(_confirmedNickname!);
          }
          break;

        case 'JOIN_OK':
          _confirmedNickname = msg['nickname'] as String?;
          debugPrint('[WS] JOIN_OK: $_confirmedNickname');
          notifyListeners();
          break;

        case 'PLAYER_LIST':
          final players = msg['players'] as List<dynamic>? ?? [];
          // No borramos posiciones existentes, solo actualizamos la lista
          final currentNicks = <String>{};
          for (final p in players) {
            final nick = p['nickname'] as String? ?? '';
            final cat = p['cat'] as String? ?? '';
            if (nick.isEmpty) continue;
            currentNicks.add(nick);
            remotePlayers.putIfAbsent(nick, () => RemotePlayer(nickname: nick));
            remotePlayers[nick]!.cat = cat;
          }
          // Eliminar jugadores que ya no están
          remotePlayers.removeWhere((k, _) => !currentNicks.contains(k));
          notifyListeners();
          break;

        case 'MOVE':
          final nick = msg['nickname'] as String? ?? '';
          if (nick.isEmpty) break;
          final player = remotePlayers.putIfAbsent(
            nick,
            () => RemotePlayer(nickname: nick),
          );
          player.x = (msg['x'] ?? 0).toDouble();
          player.y = (msg['y'] ?? 0).toDouble();
          player.anim = msg['anim'] as String? ?? player.anim;
          player.frame = msg['frame'] as int? ?? player.frame;
          player.dir = msg['dir'] as String? ?? player.dir;
          player.cat = msg['cat'] as String? ?? player.cat;
          notifyListeners();
          break;

        case 'DOOR_STATE':
          final doorsList = msg['doors'] as List<dynamic>? ?? [];
          doors.clear();
          for (final d in doorsList) {
            doors.add(DoorState(
              index: d['index'] as int? ?? 0,
              x: (d['x'] ?? 0).toDouble(),
              y: (d['y'] ?? 0).toDouble(),
              w: (d['w'] ?? 0).toDouble(),
              h: (d['h'] ?? 0).toDouble(),
              open: d['open'] as bool? ?? false,
            ));
          }
          notifyListeners();
          break;
      }
    } catch (e) {
      debugPrint('[WS] Error parseando mensaje: $e');
    }
  }

  void _send(Map<String, dynamic> data) {
    if (_channel != null && _connected) {
      _channel!.sink.add(jsonEncode(data));
    }
  }

  /// Unirse como espectador (viewer) — solo observa, no envía MOVE.
  void sendJoin(String nickname, {String cat = 'viewer'}) {
    _send({'type': 'JOIN', 'nickname': nickname, 'cat': cat});
  }

  void sendGetPlayers() {
    _send({'type': 'GET_PLAYERS'});
  }

  void dispose() {
    _reconnectTimer?.cancel();
    _channel?.sink.close();
    super.dispose();
  }
}
