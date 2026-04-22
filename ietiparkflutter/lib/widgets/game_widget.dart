import 'dart:async';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../models/game_models.dart';
import '../services/websocket_service.dart';
import '../services/sprite_loader.dart';

class GameWidget extends StatefulWidget {
  const GameWidget({super.key});

  @override
  State<GameWidget> createState() => _GameWidgetState();
}

class _GameWidgetState extends State<GameWidget> {
  late Future<LevelData> _levelDataFuture;
  LevelData? _levelData;
  final SpriteLoader _sprites = SpriteLoader();
  late WebSocketService _ws;
  Timer? _renderTimer;
  bool _assetsReady = false;

  // Animación local: contador de ticks para animar frames
  int _animTick = 0;

  @override
  void initState() {
    super.initState();
    _levelDataFuture = LevelData.loadFromAssets();
    _levelDataFuture.then((data) async {
      _levelData = data;
      debugPrint('[GameWidget] LevelData cargado, cargando assets...');
      await _loadAllAssets(data);
      debugPrint('[GameWidget] Assets cargados, listo para renderizar');
      if (mounted) setState(() => _assetsReady = true);
    }).catchError((e) {
      debugPrint('[GameWidget] ERROR cargando nivel: $e');
    });

    _ws = WebSocketService(serverUrl: 'wss://pico2.ieti.site');
    _ws.connect();
    Future.delayed(const Duration(milliseconds: 500), () {
      _ws.sendJoin('flutter_viewer');
    });
    _ws.addListener(_onWsUpdate);

    // ~20fps render + animación tick
    _renderTimer = Timer.periodic(const Duration(milliseconds: 50), (_) {
      _animTick++;
      if (mounted) setState(() {});
    });
  }

  Future<void> _loadAllAssets(LevelData data) async {
    final paths = <String>{};

    // Fondos y tilemaps
    for (final layer in data.layers) {
      if (layer.tilesTexturePath.isNotEmpty) {
        paths.add('assets/levels/${layer.tilesTexturePath}');
      }
    }

    // Sprites de gatos: idle, run, jump para cat1-cat8
    for (int i = 1; i <= 8; i++) {
      paths.add('assets/levels/media/idle_cat$i.png');
      paths.add('assets/levels/media/run_cat$i.png');
      paths.add('assets/levels/media/jump_cat$i.png');
    }

    // Otros sprites (shop, tree, potion)
    paths.add('assets/levels/media/shop_anim.png');
    paths.add('assets/levels/media/tree1.png');
    paths.add('assets/levels/media/Icon1.png');

    await _sprites.loadAll(paths.toList());
  }

  void _onWsUpdate() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _renderTimer?.cancel();
    _ws.removeListener(_onWsUpdate);
    _ws.dispose();
    _sprites.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_assetsReady || _levelData == null) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 20),
            Text('Cargando assets...'),
          ],
        ),
      );
    }

    final players = _ws.remotePlayers.values.toList();
    final ld = _levelData!;

    return InteractiveViewer(
      minScale: 0.3,
      maxScale: 3.0,
      constrained: false,
      child: SizedBox(
        width: ld.worldWidth,
        height: ld.worldHeight,
        child: CustomPaint(
          painter: GamePainter(
            levelData: ld,
            sprites: _sprites,
            remotePlayers: players,
            connected: _ws.connected,
            viewerNickname: _ws.confirmedNickname,
            animTick: _animTick,
            doors: _ws.doors,
          ),
          size: Size(ld.worldWidth, ld.worldHeight),
        ),
      ),
    );
  }
}

class GamePainter extends CustomPainter {
  final LevelData levelData;
  final SpriteLoader sprites;
  final List<RemotePlayer> remotePlayers;
  final bool connected;
  final String? viewerNickname;
  final int animTick;
  final List<DoorState> doors;

  // Frame sizes por tipo de animación
  static const _frameSizes = {
    'idle': (w: 16, h: 16, frames: 7),
    'run': (w: 20, h: 20, frames: 7),
    'jump': (w: 20, h: 24, frames: 11),
  };

  GamePainter({
    required this.levelData,
    required this.sprites,
    required this.remotePlayers,
    required this.connected,
    this.viewerNickname,
    required this.animTick,
    required this.doors,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // 1. Fondos (capas de fondo)
    _drawBackgrounds(canvas);

    // 2. Tilemap principal (capa "level")
    _drawTileLayers(canvas);

    // 3. Sprites estáticos (shop, tree, potion)
    _drawStaticSprites(canvas);

    // 4. Puertas
    _drawDoors(canvas);

    // 5. Jugadores remotos (gatos con sprites reales)
    for (final p in remotePlayers) {
      if (p.nickname == viewerNickname) continue;
      _drawCatPlayer(canvas, p);
    }

    // 6. UI overlay
    _drawUI(canvas, size);
  }

  void _drawBackgrounds(Canvas canvas) {
    // Dibujar capas de fondo en orden: background3, background2, background
    for (final layer in levelData.layers.reversed) {
      if (!layer.name.startsWith('background')) continue;
      final path = 'assets/levels/${layer.tilesTexturePath}';
      final img = sprites.get(path);
      if (img == null) continue;
      canvas.drawImage(img, Offset(layer.x, layer.y), Paint());
    }
  }

  void _drawTileLayers(Canvas canvas) {
    for (final layer in levelData.layers) {
      if (layer.name.startsWith('background') || !layer.visible) continue;
      final path = 'assets/levels/${layer.tilesTexturePath}';
      final img = sprites.get(path);
      if (img == null) continue;

      final tw = layer.tileWidth;
      final th = layer.tileHeight;
      if (tw <= 0 || th <= 0) continue;

      final cols = img.width ~/ tw;
      if (cols <= 0) continue;

      for (int row = 0; row < layer.tiles.length; row++) {
        for (int col = 0; col < layer.tiles[row].length; col++) {
          final tileId = layer.tiles[row][col];
          if (tileId < 0) continue;

          final srcCol = tileId % cols;
          final srcRow = tileId ~/ cols;
          final src = Rect.fromLTWH(
            srcCol * tw.toDouble(),
            srcRow * th.toDouble(),
            tw.toDouble(),
            th.toDouble(),
          );
          final dst = Rect.fromLTWH(
            layer.x + col * tw.toDouble(),
            layer.y + row * th.toDouble(),
            tw.toDouble(),
            th.toDouble(),
          );
          canvas.drawImageRect(img, src, dst, Paint());
        }
      }
    }
  }

  void _drawStaticSprites(Canvas canvas) {
    // Shop
    _drawSpriteFrame(canvas, 'assets/levels/media/shop_anim.png',
        666, 335, 118, 126, (animTick ~/ 3) % 4);
    // Tree
    _drawSpriteFrame(canvas, 'assets/levels/media/tree1.png',
        1147, 337, 128, 128, 0);
    // Potion
    _drawSpriteFrame(canvas, 'assets/levels/media/Icon1.png',
        903, 316, 32, 32, 0);
  }

  void _drawSpriteFrame(Canvas canvas, String path, double x, double y,
      int frameW, int frameH, int frame) {
    final img = sprites.get(path);
    if (img == null) return;

    final cols = (img.width / frameW).floor();
    if (cols <= 0) return;
    final srcCol = frame % cols;
    final srcRow = frame ~/ cols;

    final src = Rect.fromLTWH(
      srcCol * frameW.toDouble(),
      srcRow * frameH.toDouble(),
      frameW.toDouble(),
      frameH.toDouble(),
    );
    // Anclar desde el centro-abajo del sprite
    final dst = Rect.fromLTWH(
      x - frameW / 2,
      y - frameH / 2,
      frameW.toDouble(),
      frameH.toDouble(),
    );
    canvas.drawImageRect(img, src, dst, Paint());
  }

  void _drawDoors(Canvas canvas) {
    for (final door in doors) {
      final paint = Paint()
        ..color = door.open
            ? Colors.green.withOpacity(0.3)
            : Colors.red.withOpacity(0.6);
      canvas.drawRect(
        Rect.fromLTWH(door.x, door.y, door.w, door.h),
        paint,
      );
      // Borde
      final border = Paint()
        ..color = door.open ? Colors.green : Colors.red
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2;
      canvas.drawRect(
        Rect.fromLTWH(door.x, door.y, door.w, door.h),
        border,
      );
      // Label
      final tp = TextPainter(
        text: TextSpan(
          text: door.open ? 'OPEN' : 'CLOSED',
          style: TextStyle(
            color: door.open ? Colors.green : Colors.red,
            fontSize: 8,
            fontWeight: FontWeight.bold,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(door.x + 2, door.y - 10));
    }
  }

  void _drawCatPlayer(Canvas canvas, RemotePlayer p) {
    // Determinar qué spritesheet usar según la animación
    final catNum = _extractCatNumber(p.cat);
    if (catNum == null) return;

    final animType = p.anim.isEmpty ? 'idle' : p.anim; // idle, run, jump
    final frameInfo = _frameSizes[animType] ?? _frameSizes['idle']!;
    final assetPath = 'assets/levels/media/${animType}_cat$catNum.png';
    final img = sprites.get(assetPath);
    if (img == null) return;

    final fw = frameInfo.w.toDouble();
    final fh = frameInfo.h.toDouble();
    final cols = (img.width / fw).floor();
    if (cols <= 0) return;

    // Frame animado basado en fps=12 y nuestro tick de 50ms
    final totalFrames = frameInfo.frames;
    final frame = (animTick ~/ 4) % totalFrames; // ~12fps con tick de 50ms

    final srcCol = frame % cols;
    final srcRow = frame ~/ cols;
    final src = Rect.fromLTWH(srcCol * fw, srcRow * fh, fw, fh);

    // Posición: el server envía coordenadas Y-down, anclar desde centro-abajo
    final drawX = p.x - fw / 2;
    final drawY = p.y - fh * 0.7; // anchorY ~0.7

    // Flip horizontal si mira a la izquierda
    canvas.save();
    if (p.dir == 'LEFT') {
      canvas.translate(drawX + fw, drawY);
      canvas.scale(-1, 1);
      canvas.drawImageRect(
        img, src, Rect.fromLTWH(0, 0, fw, fh), Paint(),
      );
    } else {
      canvas.drawImageRect(
        img, src, Rect.fromLTWH(drawX, drawY, fw, fh), Paint(),
      );
    }
    canvas.restore();

    // Nickname encima
    final tp = TextPainter(
      text: TextSpan(
        text: p.nickname,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 8,
          fontWeight: FontWeight.bold,
          backgroundColor: Colors.black54,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, Offset(p.x - tp.width / 2, drawY - 12));
  }

  int? _extractCatNumber(String cat) {
    // "cat1" → 1, "cat2" → 2, etc.
    if (cat.isEmpty) return null;
    final match = RegExp(r'cat(\d+)').firstMatch(cat);
    if (match != null) return int.tryParse(match.group(1)!);
    return null;
  }

  void _drawUI(Canvas canvas, Size size) {
    final status = connected ? '🟢 Conectado' : '🔴 Desconectado';
    final playerCount = remotePlayers.where((p) => p.nickname != viewerNickname).length;

    final text = '$status | Viewer: ${viewerNickname ?? "..."} | '
        '👥 $playerCount jugadores';

    final tp = TextPainter(
      text: TextSpan(
        text: text,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 11,
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