import 'dart:async';
import 'package:flutter/material.dart';
import '../models/game_models.dart';
import '../services/websocket_service.dart';
import '../services/sprite_loader.dart';

// Tamaño lógico fijo del viewport del editor
const double kWorldW = 320.0;
const double kWorldH = 180.0;

class GameWidget extends StatefulWidget {
  const GameWidget({super.key});

  @override
  State<GameWidget> createState() => _GameWidgetState();
}

class _GameWidgetState extends State<GameWidget> {
  LevelData? _levelData;
  final SpriteLoader _sprites = SpriteLoader();
  late WebSocketService _ws;
  Timer? _renderTimer;
  bool _assetsReady = false;
  int _animTick = 0;

  @override
  void initState() {
    super.initState();
    LevelData.loadFromAssets().then((data) async {
      _levelData = data;
      await _loadAllAssets(data);
      if (mounted) setState(() => _assetsReady = true);
    });

    _ws = WebSocketService(serverUrl: 'wss://pico2.ieti.site');
    _ws.connect();
    _ws.addListener(_onWsUpdate);

    _renderTimer = Timer.periodic(const Duration(milliseconds: 50), (_) {
      _animTick++;
      if (mounted) setState(() {});
    });
  }

  Future<void> _loadAllAssets(LevelData data) async {
    final paths = <String>{};
    for (final layer in data.layers) {
      if (layer.tilesTexturePath.isNotEmpty) {
        paths.add('assets/levels/${layer.tilesTexturePath}');
      }
    }
    for (int i = 1; i <= 8; i++) {
      paths.add('assets/levels/media/idle_cat$i.png');
      paths.add('assets/levels/media/run_cat$i.png');
      paths.add('assets/levels/media/jump_cat$i.png');
    }
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
      return const Center(child: CircularProgressIndicator());
    }

    return LayoutBuilder(builder: (context, constraints) {
      // Escala uniforme manteniendo aspecto 320x180
      final scale = (constraints.maxWidth / kWorldW)
          .clamp(0.0, constraints.maxHeight / kWorldH);
      final w = kWorldW * scale;
      final h = kWorldH * scale;

      return InteractiveViewer(
        minScale: 0.5,
        maxScale: 8.0,
        child: Center(
          child: SizedBox(
            width: w,
            height: h,
            child: CustomPaint(
              painter: GamePainter(
                levelData: _levelData!,
                sprites: _sprites,
                remotePlayers: _ws.remotePlayers.values.toList(),
                connected: _ws.connected,
                animTick: _animTick,
                scale: scale,
              ),
              size: Size(w, h),
            ),
          ),
        ),
      );
    });
  }
}

class GamePainter extends CustomPainter {
  final LevelData levelData;
  final SpriteLoader sprites;
  final List<RemotePlayer> remotePlayers;
  final bool connected;
  final int animTick;
  final double scale;

  static const _bgOrder = ['bg3', 'bg2', 'bg'];
  static const _bgNames = {'bg', 'bg2', 'bg3'};

  static const _frameSizes = {
    'idle': (w: 16, h: 16, frames: 7),
    'run':  (w: 20, h: 20, frames: 7),
    'jump': (w: 20, h: 24, frames: 11),
  };

  GamePainter({
    required this.levelData,
    required this.sprites,
    required this.remotePlayers,
    required this.connected,
    required this.animTick,
    required this.scale,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // Todo se pinta en coordenadas lógicas 320x180 escaladas uniformemente.
    // NO hay Y-flip. Las coordenadas del JSON son top-left, Y hacia abajo.
    canvas.save();
    canvas.scale(scale, scale);

    _drawBg(canvas);
    _drawTiles(canvas);
    _drawSprites(canvas);
    for (final p in remotePlayers) {
      if (p.hasPosition) _drawCat(canvas, p);
    }

    canvas.restore();
    _drawUI(canvas, size);
  }

  // ── Fondos ────────────────────────────────────────────────────────────────
  void _drawBg(Canvas canvas) {
    final byName = {for (final l in levelData.layers) l.name: l};
    for (final name in _bgOrder) {
      final layer = byName[name];
      if (layer == null || !layer.visible) continue;
      final img = sprites.get('assets/levels/${layer.tilesTexturePath}');
      if (img == null) continue;
      // Pintar la imagen cubriendo todo el viewport 320x180
      canvas.drawImageRect(
        img,
        Rect.fromLTWH(0, 0, img.width.toDouble(), img.height.toDouble()),
        Rect.fromLTWH(0, 0, kWorldW, kWorldH),
        Paint(),
      );
    }
  }

  // ── Tilemap ───────────────────────────────────────────────────────────────
  void _drawTiles(Canvas canvas) {
    for (final layer in levelData.layers) {
      if (_bgNames.contains(layer.name) || !layer.visible) continue;
      final img = sprites.get('assets/levels/${layer.tilesTexturePath}');
      if (img == null) continue;
      final tw = layer.tileWidth;
      final th = layer.tileHeight;
      if (tw <= 0 || th <= 0) continue;
      final sheetCols = img.width ~/ tw;
      if (sheetCols <= 0) continue;
      for (int row = 0; row < layer.tiles.length; row++) {
        for (int col = 0; col < layer.tiles[row].length; col++) {
          final id = layer.tiles[row][col];
          if (id < 0) continue;
          final src = Rect.fromLTWH(
            (id % sheetCols) * tw.toDouble(),
            (id ~/ sheetCols) * th.toDouble(),
            tw.toDouble(), th.toDouble(),
          );
          final dst = Rect.fromLTWH(
            layer.x + col * tw.toDouble(),
            layer.y + row * th.toDouble(),
            tw.toDouble(), th.toDouble(),
          );
          canvas.drawImageRect(img, src, dst, Paint());
        }
      }
    }
  }

  // ── Sprites estáticos ─────────────────────────────────────────────────────
  // Coordenadas JSON: x,y = punto de anclaje del sprite (no top-left).
  // anchorX/anchorY de la animación indican qué fracción del frame es el ancla.
  // top-left del frame = (x - fw*anchorX,  y - fh*anchorY)
  // NO hay Y-flip porque Flutter y el editor usan Y hacia abajo.
  void _drawSprites(Canvas canvas) {
    for (final sprite in levelData.sprites) {
      if (sprite.name.startsWith('cat')) continue;
      if (sprite.imageFile.isEmpty) continue;
      final img = sprites.get('assets/levels/${sprite.imageFile}');
      if (img == null) continue;

      final anim = levelData.animations[sprite.animationId];
      final anchorX = anim?.anchorX ?? 0.5;
      final anchorY = anim?.anchorY ?? 0.5;
      final fw = (anim != null && anim.frameWidth > 0)
          ? anim.frameWidth.toDouble()
          : sprite.width;
      final fh = (anim != null && anim.frameHeight > 0)
          ? anim.frameHeight.toDouble()
          : sprite.height;

      final cols = (img.width / fw).floor().clamp(1, 9999);
      final startFrame = anim?.startFrame ?? 0;
      final endFrame   = anim?.endFrame   ?? 0;
      final totalFrames = (endFrame - startFrame + 1).clamp(1, 9999);
      final isAnim = sprite.imageFile.contains('shop_anim');
      final frame  = isAnim
          ? startFrame + (animTick ~/ 3) % totalFrames
          : startFrame;

      final src = Rect.fromLTWH((frame % cols) * fw, 0, fw, fh);
      final drawX = sprite.x - fw * anchorX;
      final drawY = sprite.y - fh * anchorY;
      canvas.drawImageRect(img, src, Rect.fromLTWH(drawX, drawY, fw, fh), Paint());
    }
  }

  // ── Jugadores ─────────────────────────────────────────────────────────────
  void _drawCat(Canvas canvas, RemotePlayer p) {
    final catNum = RegExp(r'cat(\d+)').firstMatch(p.cat)?.group(1);
    if (catNum == null) return;
    final animType = p.anim.isEmpty ? 'idle' : p.anim;
    final info = _frameSizes[animType] ?? _frameSizes['idle']!;
    final img = sprites.get('assets/levels/media/${animType}_cat$catNum.png');
    if (img == null) return;

    final fw = info.w.toDouble();
    final fh = info.h.toDouble();
    final cols = (img.width / fw).floor();
    if (cols <= 0) return;

    final frame = (animTick ~/ 4) % info.frames;
    final src = Rect.fromLTWH(
      (frame % cols) * fw, (frame ~/ cols) * fh, fw, fh,
    );

    final anim = levelData.animations['${animType}_cat$catNum'];
    final anchorX = anim?.anchorX ?? 0.5;
    final anchorY = anim?.anchorY ?? 0.75;
    const dw = 32.0;
    const dh = 32.0;
    final drawX = p.x - dw * anchorX;
    final drawY = p.y - dh * anchorY;

    canvas.save();
    if (p.dir == 'LEFT') {
      canvas.translate(drawX + dw, drawY);
      canvas.scale(-1, 1);
      canvas.drawImageRect(img, src, Rect.fromLTWH(0, 0, dw, dh), Paint());
    } else {
      canvas.drawImageRect(img, src, Rect.fromLTWH(drawX, drawY, dw, dh), Paint());
    }
    canvas.restore();

    final tp = TextPainter(
      text: TextSpan(
        text: p.nickname,
        style: const TextStyle(
          color: Colors.white, fontSize: 8,
          fontWeight: FontWeight.bold, backgroundColor: Colors.black54,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, Offset(p.x - tp.width / 2, drawY - 10));
  }

  // ── UI ────────────────────────────────────────────────────────────────────
  void _drawUI(Canvas canvas, Size size) {
    final tp = TextPainter(
      text: TextSpan(
        text: '${connected ? "Conectado" : "Desconectado"} | ${remotePlayers.length} jugadores',
        style: const TextStyle(
          color: Colors.white, fontSize: 11,
          fontWeight: FontWeight.bold, backgroundColor: Colors.black54,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, const Offset(10, 10));
  }

  @override
  bool shouldRepaint(covariant CustomPainter old) => true;
}
