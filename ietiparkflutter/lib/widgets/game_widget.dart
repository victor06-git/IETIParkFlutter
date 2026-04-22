import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import '../models/game_models.dart';
import '../services/websocket_service.dart';

class GameWidget extends StatefulWidget {
  const GameWidget({super.key});

  @override
  State<GameWidget> createState() => _GameWidgetState();
}

class _GameWidgetState extends State<GameWidget> {
  late Future<LevelData> _levelData;
  Offset _cameraPosition = Offset.zero;

  // WebSocket
  late WebSocketService _ws;

  // Timer para refrescar el canvas con datos del server
  Timer? _renderTimer;

  @override
  void initState() {
    super.initState();
    _levelData = LevelData.loadFromAssets();
    _cameraPosition = const Offset(0, 0);

    // Conectar al server — cambia la URL si usas localhost
    _ws = WebSocketService(serverUrl: 'wss://pico2.ieti.site');
    // _ws = WebSocketService(serverUrl: 'ws://localhost:8080');
    _ws.connect();

    // Unirse como viewer para recibir broadcasts
    Future.delayed(const Duration(milliseconds: 500), () {
      _ws.sendJoin('flutter_viewer');
    });

    // Escuchar cambios del WebSocket
    _ws.addListener(_onWsUpdate);

    // Refrescar el canvas a ~30fps para animaciones suaves
    _renderTimer = Timer.periodic(const Duration(milliseconds: 33), (_) {
      if (mounted) setState(() {});
    });
  }

  void _onWsUpdate() {
    // El Timer ya hace setState periódico, pero si queremos
    // reaccionar inmediato a eventos importantes:
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _renderTimer?.cancel();
    _ws.removeListener(_onWsUpdate);
    _ws.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<LevelData>(
      future: _levelData,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                CircularProgressIndicator(),
                SizedBox(height: 20),
                Text('Cargando nivel...'),
              ],
            ),
          );
        }

        if (snapshot.hasError) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error, size: 50, color: Colors.red),
                const SizedBox(height: 20),
                Text('Error: ${snapshot.error}'),
                const SizedBox(height: 20),
                ElevatedButton(
                  onPressed: () {
                    setState(() {
                      _levelData = LevelData.loadFromAssets();
                    });
                  },
                  child: const Text('Reintentar'),
                ),
              ],
            ),
          );
        }

        final levelData = snapshot.data!;
        final players = _ws.remotePlayers.values.toList();

        return InteractiveViewer(
          minScale: 0.5,
          maxScale: 2.0,
          constrained: false,
          child: SizedBox(
            width: 2000,
            height: 600,
            child: CustomPaint(
              painter: GamePainter(
                zones: levelData.zones,
                layers: levelData.layers,
                animations: levelData.animations,
                remotePlayers: players,
                connected: _ws.connected,
                viewerNickname: _ws.confirmedNickname,
              ),
              size: const Size(2000, 600),
            ),
          ),
        );
      },
    );
  }
}

class GamePainter extends CustomPainter {
  final List<Zone> zones;
  final List<TileMap> layers;
  final Map<String, AnimationData> animations;
  final List<RemotePlayer> remotePlayers;
  final bool connected;
  final String? viewerNickname;

  // Colores para distinguir jugadores
  static const _playerColors = [
    Colors.orange,
    Colors.blue,
    Colors.green,
    Colors.purple,
    Colors.red,
    Colors.teal,
    Colors.pink,
    Colors.cyan,
  ];

  GamePainter({
    required this.zones,
    required this.layers,
    required this.animations,
    required this.remotePlayers,
    required this.connected,
    this.viewerNickname,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // Fondo
    final bgPaint = Paint()..color = Colors.grey[200]!;
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), bgPaint);

    _drawGrid(canvas, size);

    for (var layer in layers) {
      _drawTileLayer(canvas, layer);
    }

    for (var zone in zones) {
      _drawZone(canvas, zone);
    }

    // Dibujar cada jugador remoto
    for (int i = 0; i < remotePlayers.length; i++) {
      final p = remotePlayers[i];
      // No dibujar al viewer (nosotros mismos)
      if (p.nickname == viewerNickname) continue;
      final color = _playerColors[i % _playerColors.length];
      _drawRemotePlayer(canvas, p, color);
    }

    // UI overlay
    _drawUI(canvas, size);
  }

  void _drawGrid(Canvas canvas, Size size) {
    final gridPaint = Paint()
      ..color = Colors.grey[300]!
      ..strokeWidth = 0.5;
    for (double x = 0; x < size.width; x += 50) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), gridPaint);
    }
    for (double y = 0; y < size.height; y += 50) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }
  }

  void _drawTileLayer(Canvas canvas, TileMap layer) {
    const tileSize = 32.0;
    final colors = [
      Colors.green[100]!,
      Colors.green[200]!,
      Colors.brown[100]!,
      Colors.brown[200]!,
      Colors.orange[100]!,
    ];
    final layerColor = colors[layer.layerIndex % colors.length].withOpacity(0.7);
    final tilePaint = Paint()..color = layerColor;

    for (int y = 0; y < layer.tiles.length; y++) {
      for (int x = 0; x < layer.tiles[y].length; x++) {
        if (layer.tiles[y][x] > 0) {
          canvas.drawRect(
            Rect.fromLTWH(x * tileSize, y * tileSize, tileSize, tileSize),
            tilePaint,
          );
          final borderPaint = Paint()
            ..color = Colors.black.withOpacity(0.1)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 0.5;
          canvas.drawRect(
            Rect.fromLTWH(x * tileSize, y * tileSize, tileSize, tileSize),
            borderPaint,
          );
        }
      }
    }
  }

  void _drawZone(Canvas canvas, Zone zone) {
    canvas.drawRect(zone.rect, Paint()..color = zone.color);
    final borderPaint = Paint()
      ..color = Colors.black.withOpacity(0.3)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    canvas.drawRect(zone.rect, borderPaint);

    final tp = TextPainter(
      text: TextSpan(
        text: zone.name,
        style: const TextStyle(color: Colors.black, fontSize: 10, fontWeight: FontWeight.bold),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, Offset(zone.x + 5, zone.y + 5));
  }

  void _drawRemotePlayer(Canvas canvas, RemotePlayer p, Color color) {
    final px = p.x;
    final py = p.y;
    final facingRight = p.dir != 'LEFT';

    // Cuerpo
    final bodyPaint = Paint()..color = color;
    canvas.drawRect(Rect.fromLTWH(px - 20, py - 40, 40, 60), bodyPaint);

    // Cabeza
    canvas.drawCircle(Offset(px, py - 45), 18, bodyPaint);

    // Orejas
    final earPaint = Paint()..color = HSLColor.fromColor(color).withLightness(0.35).toColor();
    canvas.drawRect(Rect.fromLTWH(px - 25, py - 58, 12, 15), earPaint);
    canvas.drawRect(Rect.fromLTWH(px + 13, py - 58, 12, 15), earPaint);

    // Ojos
    canvas.drawCircle(
      Offset(px - (facingRight ? 8 : -8), py - 50), 5, Paint()..color = Colors.white,
    );
    canvas.drawCircle(
      Offset(px - (facingRight ? 7 : -7), py - 50), 3, Paint()..color = Colors.black,
    );

    // Nariz
    canvas.drawCircle(Offset(px, py - 43), 3, Paint()..color = Colors.pink);

    // Bigotes
    final whisker = Paint()..color = Colors.black..strokeWidth = 1;
    canvas.drawLine(Offset(px - 15, py - 45), Offset(px - 25, py - 48), whisker);
    canvas.drawLine(Offset(px - 15, py - 43), Offset(px - 25, py - 43), whisker);
    canvas.drawLine(Offset(px + 15, py - 45), Offset(px + 25, py - 48), whisker);
    canvas.drawLine(Offset(px + 15, py - 43), Offset(px + 25, py - 43), whisker);

    // Nickname encima
    final nameTp = TextPainter(
      text: TextSpan(
        text: '${p.nickname} (${p.anim})',
        style: const TextStyle(
          color: Colors.white,
          fontSize: 10,
          fontWeight: FontWeight.bold,
          backgroundColor: Colors.black54,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    nameTp.paint(canvas, Offset(px - nameTp.width / 2, py - 75));
  }

  void _drawUI(Canvas canvas, Size size) {
    final status = connected ? '🟢 Conectado' : '🔴 Desconectado';
    final playerCount = remotePlayers.where((p) => p.nickname != viewerNickname).length;
    final playerNames = remotePlayers
        .where((p) => p.nickname != viewerNickname)
        .map((p) => '${p.nickname} (${p.x.toInt()},${p.y.toInt()}) ${p.anim}')
        .join('\n  ');

    final text = '$status | Viewer: ${viewerNickname ?? "..."}\n'
        '👥 Jugadores activos: $playerCount\n'
        '  $playerNames\n'
        '📊 Zonas: ${zones.length} | Capas: ${layers.length} | Anims: ${animations.length}';

    final tp = TextPainter(
      text: TextSpan(
        text: text,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 12,
          fontWeight: FontWeight.bold,
          backgroundColor: Colors.black54,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, const Offset(10, 10));
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
