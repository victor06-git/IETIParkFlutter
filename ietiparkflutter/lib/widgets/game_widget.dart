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
    // Árbol muerto/vivo y poción del nivel
    paths.add('assets/levels/media/tree_die(1).png');
    paths.add('assets/levels/media/tree_alive(1).png');
    paths.add('assets/levels/media/Icon1(2)(2).png');
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
                potionTaken: _ws.potionTaken,
                potionConsumed: _ws.potionConsumed,
                potionX: _ws.potionX,
                potionY: _ws.potionY,
                doorOpen: _ws.doorOpen,
                treeOpening: _ws.treeOpening,
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
  final bool potionTaken;
  final bool potionConsumed;
  final double potionX;
  final double potionY;
  final bool doorOpen;
  final bool treeOpening;

  // Tamaño de la poción en el suelo (igual que Android: POTION_FLOOR_SIZE=24)
  static const double _potionFloorSize = 24.0;
  // Tamaño de la poción sobre el portador (igual que Android: CARRIED_POTION_SIZE=20)
  static const double _potionCarriedSize = 20.0;

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
    required this.potionTaken,
    required this.potionConsumed,
    required this.potionX,
    required this.potionY,
    required this.doorOpen,
    required this.treeOpening,
  });

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(scale, scale);

    _drawBg(canvas);
    _drawTiles(canvas);
    _drawSprites(canvas);
    if (doorOpen) _drawTreeAlive(canvas);
    for (final p in remotePlayers) {
      if (p.hasPosition) _drawCat(canvas, p);
    }
    // Poción sobre el portador (igual que Android: drawPotionOverCarrier)
    for (final p in remotePlayers) {
      if (p.hasPosition && p.hasPotion && !potionConsumed) _drawCarriedPotion(canvas, p);
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
      if (sprite.type == 'potion' && potionTaken) continue;
      if (sprite.type == 'dead_tree' && doorOpen) continue;
      if (sprite.imageFile.isEmpty) continue;
      final img = sprites.get('assets/levels/${sprite.imageFile}');
      if (img == null) continue;

      final anim = levelData.animations[sprite.animationId];
      final anchorX = anim?.anchorX ?? 0.5;
      final anchorY = anim?.anchorY ?? 0.5;
      final fw = (anim != null && anim.frameWidth > 0) ? anim.frameWidth.toDouble() : sprite.width;
      final fh = (anim != null && anim.frameHeight > 0) ? anim.frameHeight.toDouble() : sprite.height;

      // La poción en el suelo se dibuja pequeña (igual que Android)
      final drawW = sprite.type == 'potion' ? _potionFloorSize : fw;
      final drawH = sprite.type == 'potion' ? _potionFloorSize : fh;

      final cols = (img.width / fw).floor().clamp(1, 9999);
      final startFrame = anim?.startFrame ?? 0;
      final endFrame   = anim?.endFrame   ?? startFrame;
      final totalFrames = (endFrame - startFrame + 1).clamp(1, 9999);
      final frame = startFrame + (animTick ~/ 3) % totalFrames;

      final srcRow = frame ~/ cols;
      final srcCol = frame % cols;
      final src = Rect.fromLTWH(srcCol * fw, srcRow * fh, fw, fh);
      // Para la poción usamos su posición del servidor, no la del JSON
      final dx = sprite.type == 'potion' ? potionX - drawW * anchorX : sprite.x - drawW * anchorX;
      final dy = sprite.type == 'potion' ? potionY - drawH * anchorY : sprite.y - drawH * anchorY;
      canvas.drawImageRect(img, src, Rect.fromLTWH(dx, dy, drawW, drawH), Paint());
    }
  }

  // Animación tree_alive cuando la poción cura el árbol
  void _drawTreeAlive(Canvas canvas) {
    final anim = levelData.animations['tree_alive'];
    if (anim == null) return;
    final img = sprites.get('assets/levels/${anim.mediaFile}');
    if (img == null) return;

    final fw = anim.frameWidth > 0 ? anim.frameWidth.toDouble() : 100.0;
    final fh = anim.frameHeight > 0 ? anim.frameHeight.toDouble() : 100.0;
    final cols = (img.width / fw).floor().clamp(1, 9999);
    final startFrame = anim.startFrame;
    final endFrame   = anim.endFrame;
    final totalFrames = (endFrame - startFrame + 1).clamp(1, 9999);

    // Si el árbol está abriendo, anima una vez; si ya está abierto, queda en el último frame
    final frame = treeOpening
        ? (startFrame + (animTick ~/ 4) % totalFrames)
        : endFrame;

    final srcRow = frame ~/ cols;
    final srcCol = frame % cols;
    final src = Rect.fromLTWH(srcCol * fw, srcRow * fh, fw, fh);

    // Buscar posición del sprite dead_tree para pintar encima
    SpriteData? treeSprite;
    for (final s in levelData.sprites) {
      if (s.type == 'dead_tree') { treeSprite = s; break; }
    }
    final treeAnim = treeSprite != null ? levelData.animations[treeSprite.animationId] : null;
    final ax = treeAnim?.anchorX ?? anim.anchorX;
    final ay = treeAnim?.anchorY ?? anim.anchorY;
    final tx = treeSprite?.x ?? 285.0;
    final ty = treeSprite?.y ?? 148.0;
    canvas.drawImageRect(img, src, Rect.fromLTWH(tx - fw * ax, ty - fh * ay, fw, fh), Paint());
  }

  // Poción pequeña sobre la cabeza del portador (igual que Android: drawPotionOverCarrier)
  void _drawCarriedPotion(Canvas canvas, RemotePlayer carrier) {
    final img = sprites.get('assets/levels/media/Icon1(2)(2).png');
    if (img == null) return;
    const fw = 45.0;
    const fh = 45.0;
    final cols = (img.width / fw).floor().clamp(1, 9999);
    final frame = (animTick ~/ 3) % 4;
    final src = Rect.fromLTWH((frame % cols) * fw, (frame ~/ cols) * fh, fw, fh);
    const size = _potionCarriedSize;
    // Centrada horizontalmente, 30px por encima del punto de anclaje del gato
    final dx = carrier.x - size * 0.5;
    final dy = carrier.y - 30 - size * 0.5;
    canvas.drawImageRect(img, src, Rect.fromLTWH(dx, dy, size, size), Paint());
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
    final dw = fw;
    final dh = fh;
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
          color: Colors.white, fontSize: 6,
          fontWeight: FontWeight.bold, backgroundColor: Colors.black54,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, Offset(p.x - tp.width / 2, drawY - 6));
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
