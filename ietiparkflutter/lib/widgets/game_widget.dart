import 'dart:async';
import 'package:flutter/material.dart';
import '../models/game_models.dart';
import '../services/websocket_service.dart';
import '../services/sprite_loader.dart';

const double kWorldW = 320.0;
const double kWorldH = 180.0;

class GameWidget extends StatefulWidget {
  const GameWidget({super.key});

  @override
  State<GameWidget> createState() => _GameWidgetState();
}

class _GameWidgetState extends State<GameWidget> {
  // Un LevelData por nivel (índice 0 y 1)
  final List<LevelData?> _levels = [null, null];
  final SpriteLoader _sprites = SpriteLoader();
  late WebSocketService _ws;
  Timer? _renderTimer;
  bool _assetsReady = false;
  int _animTick = 0;

  @override
  void initState() {
    super.initState();
    _ws = WebSocketService(serverUrl: 'wss://pico2.ieti.site');
    _ws.connect();
    _ws.addListener(_onWsUpdate);

    // Cargar ambos niveles y todos los assets al inicio
    _loadEverything();

    _renderTimer = Timer.periodic(const Duration(milliseconds: 50), (_) {
      _animTick++;
      if (mounted) setState(() {});
    });
  }

  Future<void> _loadEverything() async {
    final lv0 = await LevelData.loadLevel(0);
    final lv1 = await LevelData.loadLevel(1);
    _levels[0] = lv0;
    _levels[1] = lv1;
    await _loadAllAssets();
    if (mounted) setState(() => _assetsReady = true);
  }

  Future<void> _loadAllAssets() async {
    final paths = <String>{};

    // Capas de ambos niveles
    for (final lv in _levels) {
      if (lv == null) continue;
      for (final layer in lv.layers) {
        if (layer.tilesTexturePath.isNotEmpty) {
          paths.add('assets/levels/${layer.tilesTexturePath}');
        }
      }
    }

    // Gatos
    for (int i = 1; i <= 8; i++) {
      paths.add('assets/levels/media/idle_cat$i.png');
      paths.add('assets/levels/media/run_cat$i.png');
      paths.add('assets/levels/media/jump_cat$i.png');
    }

    // Assets nivel 0: árbol muerto/vivo y poción roja
    paths.add('assets/levels/media/tree_die1.png');
    paths.add('assets/levels/media/tree_alive1.png');
    paths.add('assets/levels/media/Icon1(2)(2).png');

    // Assets nivel 1: poción verde, botón, árbol
    paths.add('assets/levels/media/Icon7(2).png');   // poción verde
    paths.add('assets/levels/media/Icon7(5).png');   // botón animado
    paths.add('assets/levels/media/Icon7(6).png');   // botón estático

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
    if (!_assetsReady) {
      return const Center(child: CircularProgressIndicator());
    }

    final lvIdx = _ws.levelIndex.clamp(0, 1);
    final levelData = _levels[lvIdx];
    if (levelData == null) {
      return const Center(child: CircularProgressIndicator());
    }


    return LayoutBuilder(builder: (context, constraints) {
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
                levelData: levelData,
                levelIndex: lvIdx,
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
                platformX: _ws.platformX,
                platformY: _ws.platformY,
                platformWidth: _ws.platformWidth,
                platformHeight: _ws.platformHeight,
                platformActive: _ws.platformActive,
                buttonX: _ws.buttonX,
                buttonY: _ws.buttonY,
                buttonPressed: _ws.buttonPressed,
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
  final int levelIndex;
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
  final double platformX;
  final double platformY;
  final double platformWidth;
  final double platformHeight;
  final bool platformActive;
  final double buttonX;
  final double buttonY;
  final bool buttonPressed;

  static const double _potionFloorSize = 24.0;
  static const double _potionCarriedSize = 20.0;

  static const _bgOrder = ['bg3', 'bg2', 'bg'];
  static const _bgNames = {'bg', 'bg2', 'bg3'};

  static const _frameSizes = {
    'idle': (w: 16, h: 16, frames: 7),
    'run':  (w: 20, h: 20, frames: 7),
    'jump': (w: 20, h: 24, frames: 11),
  };

  // Textura de poción según nivel
  String get _potionTexture => levelIndex == 1
      ? 'assets/levels/media/Icon7(2).png'
      : 'assets/levels/media/Icon1(2)(2).png';
  double get _potionFrameSize => levelIndex == 1 ? 32.0 : 45.0;

  // Tipo de sprite de poción según nivel
  String get _potionSpriteType => levelIndex == 1 ? 'green_potion' : 'potion';

  GamePainter({
    required this.levelData,
    required this.levelIndex,
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
    required this.platformX,
    required this.platformY,
    required this.platformWidth,
    required this.platformHeight,
    required this.platformActive,
    required this.buttonX,
    required this.buttonY,
    required this.buttonPressed,
  });

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(scale, scale);

    _drawBg(canvas);
    _drawTiles(canvas);
    if (levelIndex == 1) _drawPlatform(canvas);
    _drawSprites(canvas);
    if (doorOpen) _drawTreeAlive(canvas);
    for (final p in remotePlayers) {
      if (p.hasPosition) _drawCat(canvas, p);
    }
    for (final p in remotePlayers) {
      if (p.hasPosition && p.hasPotion && !potionConsumed) _drawCarriedPotion(canvas, p);
    }

    canvas.restore();
    _drawUI(canvas, size);
  }

  void _drawBg(Canvas canvas) {
    final byName = {for (final l in levelData.layers) l.name: l};
    for (final name in _bgOrder) {
      final layer = byName[name];
      if (layer == null || !layer.visible) continue;
      final img = sprites.get('assets/levels/${layer.tilesTexturePath}');
      if (img == null) continue;
      canvas.drawImageRect(
        img,
        Rect.fromLTWH(0, 0, img.width.toDouble(), img.height.toDouble()),
        Rect.fromLTWH(0, 0, kWorldW, kWorldH),
        Paint(),
      );
    }
  }

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
          // En nivel 1, saltar los tiles de la plataforma (row=5, cols=9-13):
          // se pintan dinámicamente en _drawPlatform con la posición del servidor.
          if (levelIndex == 1 && layer.name == 'level' &&
              row == 5 && col >= 9 && col <= 13) continue;
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

  // Plataforma móvil del nivel 1: usa el mismo tileset que el nivel
  void _drawPlatform(Canvas canvas) {
    // Buscar la capa 'level' para usar su tileset
    LayerData? levelLayer;
    for (final l in levelData.layers) {
      if (l.name == 'level') { levelLayer = l; break; }
    }
    if (levelLayer == null) return;
    final img = sprites.get('assets/levels/${levelLayer.tilesTexturePath}');
    if (img == null) return;

    final tw = levelLayer.tileWidth.toDouble();
    final th = levelLayer.tileHeight.toDouble();
    if (tw <= 0 || th <= 0) return;
    final sheetCols = img.width ~/ tw.toInt();
    if (sheetCols <= 0) return;

    // Tiles de la plataforma (igual que PlayScreen.java: LEVEL2_PLATFORM_TILE_INDICES)
    const platformTiles = [205, 206, 207, 208, 209];
    for (int i = 0; i < platformTiles.length; i++) {
      final id = platformTiles[i];
      final src = Rect.fromLTWH(
        (id % sheetCols) * tw, (id ~/ sheetCols) * th, tw, th,
      );
      final dst = Rect.fromLTWH(platformX + i * tw, platformY, tw, th);
      canvas.drawImageRect(img, src, dst, Paint());
    }
  }

  void _drawSprites(Canvas canvas) {
    for (final sprite in levelData.sprites) {
      if (sprite.name.startsWith('cat')) continue;
      // Ocultar poción si fue recogida
      if (sprite.type == _potionSpriteType && potionTaken) continue;
      // Ocultar árbol muerto si ya está abierto
      if (sprite.type == 'dead_tree' && doorOpen) continue;
      // El botón del nivel 1 se pinta con posición dinámica del servidor
      if (sprite.type == 'button') continue;
      if (sprite.imageFile.isEmpty) continue;
      final img = sprites.get('assets/levels/${sprite.imageFile}');
      if (img == null) continue;

      final anim = levelData.animations[sprite.animationId];
      final anchorX = anim?.anchorX ?? 0.5;
      final anchorY = anim?.anchorY ?? 0.5;
      final fw = (anim != null && anim.frameWidth > 0) ? anim.frameWidth.toDouble() : sprite.width;
      final fh = (anim != null && anim.frameHeight > 0) ? anim.frameHeight.toDouble() : sprite.height;

      final isPotion = sprite.type == _potionSpriteType;
      final drawW = isPotion ? _potionFloorSize : fw;
      final drawH = isPotion ? _potionFloorSize : fh;

      final cols = (img.width / fw).floor().clamp(1, 9999);
      final startFrame = anim?.startFrame ?? 0;
      final endFrame   = anim?.endFrame   ?? startFrame;
      final totalFrames = (endFrame - startFrame + 1).clamp(1, 9999);
      final frame = startFrame + (animTick ~/ 3) % totalFrames;

      final src = Rect.fromLTWH((frame % cols) * fw, (frame ~/ cols) * fh, fw, fh);
      final dx = isPotion ? potionX - drawW * anchorX : sprite.x - drawW * anchorX;
      final dy = isPotion ? potionY - drawH * anchorY : sprite.y - drawH * anchorY;
      canvas.drawImageRect(img, src, Rect.fromLTWH(dx, dy, drawW, drawH), Paint());
    }

    // Botón del nivel 1 con posición dinámica del servidor
    if (levelIndex == 1) _drawButton(canvas);
  }

  void _drawButton(Canvas canvas) {
    // Botón animado cuando está presionado, estático cuando no
    final animName = buttonPressed ? 'button' : 'static_button';
    final anim = levelData.animations[animName];
    if (anim == null) return;
    final img = sprites.get('assets/levels/${anim.mediaFile}');
    if (img == null) return;

    final fw = anim.frameWidth > 0 ? anim.frameWidth.toDouble() : 32.0;
    final fh = anim.frameHeight > 0 ? anim.frameHeight.toDouble() : 32.0;
    final cols = (img.width / fw).floor().clamp(1, 9999);
    final startFrame = anim.startFrame;
    final endFrame   = anim.endFrame;
    final totalFrames = (endFrame - startFrame + 1).clamp(1, 9999);
    final frame = startFrame + (animTick ~/ 3) % totalFrames;

    final src = Rect.fromLTWH((frame % cols) * fw, (frame ~/ cols) * fh, fw, fh);
    // buttonX/Y del servidor es el centro del botón
    final dx = buttonX - fw * (anim.anchorX);
    final dy = buttonY - fh * (anim.anchorY);
    canvas.drawImageRect(img, src, Rect.fromLTWH(dx, dy, fw, fh), Paint());
  }

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
    final frame = treeOpening
        ? (startFrame + (animTick ~/ 4) % totalFrames)
        : endFrame;

    final src = Rect.fromLTWH((frame % cols) * fw, (frame ~/ cols) * fh, fw, fh);

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

  void _drawCarriedPotion(Canvas canvas, RemotePlayer carrier) {
    final img = sprites.get(_potionTexture);
    if (img == null) return;
    final fw = _potionFrameSize;
    final fh = _potionFrameSize;
    final cols = (img.width / fw).floor().clamp(1, 9999);
    final frame = (animTick ~/ 3) % 4;
    final src = Rect.fromLTWH((frame % cols) * fw, (frame ~/ cols) * fh, fw, fh);
    const size = _potionCarriedSize;
    final dx = carrier.x - size * 0.5;
    final dy = carrier.y - 30 - size * 0.5;
    canvas.drawImageRect(img, src, Rect.fromLTWH(dx, dy, size, size), Paint());
  }

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
    final src = Rect.fromLTWH((frame % cols) * fw, (frame ~/ cols) * fh, fw, fh);

    final anim = levelData.animations['${animType}_cat$catNum'];
    final anchorX = anim?.anchorX ?? 0.5;
    final anchorY = anim?.anchorY ?? 0.75;
    final drawX = p.x - fw * anchorX;
    final drawY = p.y - fh * anchorY;

    canvas.save();
    if (p.dir == 'LEFT') {
      canvas.translate(drawX + fw, drawY);
      canvas.scale(-1, 1);
      canvas.drawImageRect(img, src, Rect.fromLTWH(0, 0, fw, fh), Paint());
    } else {
      canvas.drawImageRect(img, src, Rect.fromLTWH(drawX, drawY, fw, fh), Paint());
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

  void _drawUI(Canvas canvas, Size size) {
    final tp = TextPainter(
      text: TextSpan(
        text: '${connected ? "Conectado" : "Desconectado"} | ${remotePlayers.length} jugadores | Nivel $levelIndex',
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
