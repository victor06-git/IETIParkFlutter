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
  int _animTick = 0;

  @override
  void initState() {
    super.initState();
    _levelDataFuture = LevelData.loadFromAssets();
    _levelDataFuture
        .then((data) async {
          _levelData = data;
          await _loadAllAssets(data);
          if (mounted) setState(() => _assetsReady = true);
        })
        .catchError((e) {
          debugPrint('[GameWidget] ERROR cargando nivel: $e');
        });

    _ws = WebSocketService(serverUrl: 'wss://pico2.ieti.site');
    _ws.connect();
    Future.delayed(const Duration(milliseconds: 500), () {
      _ws.sendJoin('flutter_viewer');
    });
    _ws.addListener(_onWsUpdate);

    _renderTimer = Timer.periodic(const Duration(milliseconds: 50), (_) {
      _animTick++;
      if (mounted) setState(() {});
    });
  }

  Future<void> _loadAllAssets(LevelData data) async {
    final paths = <String>{};

    // ── Capas del nivel ──────────────────────────────────────────────────────
    // FUENTE: game_data.json → levels[0].layers[*].tilesSheetFile
    // Ejemplos reales en tu JSON:
    //   "media/Terrain_and_Props.png"  (layer "level", tiles 16x16)
    //   "media/BG_1_1.png"             (layer "bg",  imagen única 320x180)
    //   "media/BG_2_1.png"             (layer "bg2", imagen única 320x180)
    //   "media/BG_3_1.png"             (layer "bg3", imagen única 320x180)
    for (final layer in data.layers) {
      if (layer.tilesTexturePath.isNotEmpty) {
        paths.add('assets/levels/${layer.tilesTexturePath}');
      }
    }

    // ── Sprites de gatos ─────────────────────────────────────────────────────
    // FUENTE: game_data.json → mediaAssets[*] donde mediaType == "spritesheet"
    // y fileName es "media/idle_catN.png", "media/run_catN.png", etc.
    for (int i = 1; i <= 8; i++) {
      paths.add('assets/levels/media/idle_cat$i.png');
      paths.add('assets/levels/media/run_cat$i.png');
      paths.add('assets/levels/media/jump_cat$i.png');
    }

    // ── Sprites estáticos del nivel ──────────────────────────────────────────
    // FUENTE: game_data.json → levels[0].sprites[*].imageFile
    // Los que existen en tu JSON:
    //   "media/shop_anim.png"   (sprite "shop",   x:?, y:? — no definido en sprites[], viene de animaciones)
    //   "media/tree1.png"       (sprite "tree",   x:262, y:153, w:128, h:128)
    //   "media/Icon1.png"       (sprite "potion", x:157, y:160, w:32, h:32)
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

    final painter = GamePainter(
      levelData: ld,
      sprites: _sprites,
      remotePlayers: players,
      connected: _ws.connected,
      viewerNickname: _ws.confirmedNickname,
      animTick: _animTick,
      doors: _ws.doors,
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        // Escala uniforme para que el mundo lógico llene el espacio disponible
        final scaleX = constraints.maxWidth / ld.worldWidth;
        final scaleY = constraints.maxHeight / ld.worldHeight;
        final scale = scaleX < scaleY ? scaleX : scaleY;
        final scaledW = ld.worldWidth * scale;
        final scaledH = ld.worldHeight * scale;

        return InteractiveViewer(
          minScale: 0.5,
          maxScale: 6.0,
          constrained: true,
          child: Center(
            child: SizedBox(
              width: scaledW,
              height: scaledH,
              child: CustomPaint(
                painter: painter,
                size: Size(scaledW, scaledH),
              ),
            ),
          ),
        );
      },
    );
  }
}

// ═════════════════════════════════════════════════════════════════════════════
//  GamePainter — orden de pintado corregido
// ═════════════════════════════════════════════════════════════════════════════
//
//  ORDEN DE CAPAS (de atrás hacia delante):
//    1. Fondos BG  — capas cuyo nombre es "bg3", "bg2", "bg"
//                    FUENTE: game_data.json → levels[0].layers donde name=="bg*"
//                    Imágenes únicas (NO tilemap), tileWidth==320, tileHeight==180
//    2. Tilemap    — capa "level" con tiles 16x16
//                    FUENTE: tilemaps/level_000_layer_000.json → tileMap[][]
//                    Textura: "media/Terrain_and_Props.png"
//    3. Sprites estáticos (shop, tree, potion)
//                    FUENTE: game_data.json → levels[0].sprites[*]
//    4. Puertas    — FUENTE: WebSocket mensaje tipo "DOOR_STATE"
//    5. Jugadores  — FUENTE: WebSocket mensajes tipo "MOVE" / "PLAYER_LIST"
//    6. UI overlay
//
// ═════════════════════════════════════════════════════════════════════════════

class GamePainter extends CustomPainter {
  final LevelData levelData;
  final SpriteLoader sprites;
  final List<RemotePlayer> remotePlayers;
  final bool connected;
  final String? viewerNickname;
  final int animTick;
  final List<DoorState> doors;

  // FUENTE: game_data.json → mediaAssets donde name=="idle_catN","run_catN","jump_catN"
  // tileWidth / tileHeight de cada spritesheet
  static const _frameSizes = {
    'idle': (w: 16, h: 16, frames: 7),
    'run': (w: 20, h: 20, frames: 7),
    'jump': (w: 20, h: 24, frames: 11),
  };

  // Nombres exactos de las capas de fondo tal como aparecen en game_data.json
  // levels[0].layers[*].name
  static const _bgLayerNames = {'bg', 'bg2', 'bg3'};

  // Orden de pintado de fondos: más lejano primero
  static const _bgOrder = ['bg3', 'bg2', 'bg'];

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
    // Escalar el canvas para que las coordenadas lógicas del juego
    // (worldWidth x worldHeight) coincidan con el tamaño físico asignado.
    // Así BG, tiles, sprites y jugadores usan todas las mismas coordenadas.
    final scaleX = size.width / levelData.worldWidth;
    final scaleY = size.height / levelData.worldHeight;
    canvas.save();
    canvas.scale(scaleX, scaleY);
    // ── 1. FONDOS ─────────────────────────────────────────────────────────────
    // Capas: "bg3" → "bg2" → "bg"  (de más lejano a más cercano)
    // FUENTE: game_data.json layers con name "bg3","bg2","bg"
    //         tilesSheetFile: "media/BG_3_1.png", "media/BG_2_1.png", "media/BG_1_1.png"
    //         Son IMÁGENES ÚNICAS (320x180), no tilemaps. Se dibujan con drawImage.
    _drawBackgrounds(canvas);

    // ── 2. TILEMAP PRINCIPAL ──────────────────────────────────────────────────
    // Capa "level": tiles 16x16 de "media/Terrain_and_Props.png"
    // FUENTE: tilemaps/level_000_layer_000.json → tileMap[][] (12 filas x 20 cols)
    //         Los valores -1 son celdas vacías (no se dibujan)
    _drawTileLayers(canvas);

    // ── 3. SPRITES ESTÁTICOS ─────────────────────────────────────────────────
    // FUENTE: game_data.json → levels[0].sprites[]
    //   "tree"   → imageFile:"media/tree1.png",  x:262, y:153, w:128, h:128
    //   "potion" → imageFile:"media/Icon1.png",   x:157, y:160, w:32,  h:32
    //   "shop"   → no está en sprites[], está implícito vía animaciones
    //              usa las coordenadas hardcodeadas del editor de niveles
    _drawStaticSprites(canvas);

    // ── 4. PUERTAS ────────────────────────────────────────────────────────────
    // FUENTE: WebSocket → mensaje tipo "DOOR_STATE"
    //         campos: index, x, y, w, h, open (bool)
    _drawDoors(canvas);

    // ── 5. JUGADORES REMOTOS ──────────────────────────────────────────────────
    // FUENTE: WebSocket → mensajes "MOVE" y "PLAYER_LIST"
    //         campos: nickname, cat ("cat1"…"cat8"), x, y, anim, frame, dir
    for (final p in remotePlayers) {
      if (p.nickname == viewerNickname) continue;
      _drawCatPlayer(canvas, p);
    }

    // Restaurar escala antes del UI (el texto se dibuja en coords de pantalla)
    canvas.restore();

    // ── 6. UI ─────────────────────────────────────────────────────────────────
    _drawUI(canvas, size);
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  FONDOS
  //  Las capas bg/bg2/bg3 tienen tileWidth==320, tileHeight==180:
  //  cada "tile" es toda la imagen → se pinta con drawImage, no drawImageRect.
  // ───────────────────────────────────────────────────────────────────────────
  void _drawBackgrounds(Canvas canvas) {
    // Construimos un mapa nombre→LayerData para acceder por nombre
    final byName = {for (final l in levelData.layers) l.name: l};

    for (final bgName in _bgOrder) {
      final layer = byName[bgName];
      if (layer == null || !layer.visible) continue;

      final path = 'assets/levels/${layer.tilesTexturePath}';
      final img = sprites.get(path);
      if (img == null) continue;

      // Escalar la imagen BG para cubrir todo el mundo (worldWidth x worldHeight)
      final dst = Rect.fromLTWH(
        layer.x,
        layer.y,
        levelData.worldWidth - layer.x,
        levelData.worldHeight - layer.y,
      );
      final src = Rect.fromLTWH(
        0,
        0,
        img.width.toDouble(),
        img.height.toDouble(),
      );
      canvas.drawImageRect(img, src, dst, Paint());
    }
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  TILEMAP PRINCIPAL
  //  Solo las capas que NO son fondo y son visibles.
  //  FUENTE: tilemaps/level_000_layer_000.json → tileMap[fila][col]
  //  Textura: media/Terrain_and_Props.png, tiles de 16x16 px
  // ───────────────────────────────────────────────────────────────────────────
  void _drawTileLayers(Canvas canvas) {
    for (final layer in levelData.layers) {
      // ✅ CORRECCIÓN: excluir fondos por su nombre real ("bg","bg2","bg3")
      // antes se usaba startsWith('background') que nunca coincidía
      if (_bgLayerNames.contains(layer.name) || !layer.visible) continue;

      final path = 'assets/levels/${layer.tilesTexturePath}';
      final img = sprites.get(path);
      if (img == null) continue;

      final tw = layer.tileWidth; // 16 px — de game_data.json → tilesWidth
      final th = layer.tileHeight; // 16 px — de game_data.json → tilesHeight
      if (tw <= 0 || th <= 0) continue;

      final cols = img.width ~/ tw;
      if (cols <= 0) continue;

      // Iterar el tileMap fila por fila
      // FUENTE: tilemaps/level_000_layer_000.json → tileMap[row][col]
      // Valor -1 = celda vacía, se salta
      for (int row = 0; row < layer.tiles.length; row++) {
        for (int col = 0; col < layer.tiles[row].length; col++) {
          final tileId = layer.tiles[row][col];
          if (tileId < 0) continue; // celda vacía

          // Calcular la fila/columna del tile dentro del spritesheet
          final srcCol = tileId % cols;
          final srcRow = tileId ~/ cols;

          final src = Rect.fromLTWH(
            srcCol * tw.toDouble(),
            srcRow * th.toDouble(),
            tw.toDouble(),
            th.toDouble(),
          );
          // Posición en el mundo
          // FUENTE: layer.x/y (de game_data.json → layers[*].x/y, ambos 0 en "level")
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

  // ───────────────────────────────────────────────────────────────────────────
  //  SPRITES ESTÁTICOS
  //  FUENTE: game_data.json → levels[0].sprites[]
  //  Coordenadas corregidas: las del JSON, NO las del código original
  // ───────────────────────────────────────────────────────────────────────────
  void _drawStaticSprites(Canvas canvas) {
    // Itera los sprites del JSON usando sus coordenadas y dimensiones exactas.
    // El canvas ya está escalado (canvas.scale en paint()), así que 1 unidad
    // lógica aquí = 1 px del viewport del editor (320x180). Todo queda alineado.
    for (final sprite in levelData.sprites) {
      if (sprite.imageFile.isEmpty) continue;
      final path = 'assets/levels/${sprite.imageFile}';
      final img = sprites.get(path);
      if (img == null) continue;

      // frame 0 del spritesheet (src = primer tile)
      // Para shop_anim usamos el frame animado
      final isAnim =
          sprite.name == 'shop_anim' || sprite.imageFile.contains('shop_anim');
      final frameW = img.width.toDouble(); // imagen completa como frame único
      final frameH = img.height.toDouble();

      // Si es un spritesheet con múltiples frames en horizontal, recortamos el primero
      // usando el width del sprite del JSON como ancho de frame
      final fw = sprite.width > 0 ? sprite.width : frameW;
      final fh = sprite.height > 0 ? sprite.height : frameH;
      final cols = (img.width / fw).floor().clamp(1, 9999);
      final frame = isAnim ? (animTick ~/ 3) % cols : 0;
      final srcCol = frame % cols;

      final src = Rect.fromLTWH(srcCol * fw, 0, fw, fh);
      final dst = Rect.fromLTWH(
        sprite.x,
        sprite.y,
        sprite.width,
        sprite.height,
      );
      canvas.drawImageRect(img, src, dst, Paint());
    }
  }

  void _drawSpriteFrame(
    Canvas canvas,
    String path, {
    required double x,
    required double y,
    required int frameW,
    required int frameH,
    required int frame,
    double? dstW,
    double? dstH,
  }) {
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
    final dst = Rect.fromLTWH(
      x,
      y,
      dstW ?? frameW.toDouble(),
      dstH ?? frameH.toDouble(),
    );
    canvas.drawImageRect(img, src, dst, Paint());
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  PUERTAS
  //  FUENTE: WebSocket → mensaje "DOOR_STATE"
  //          campos: index, x, y, w, h, open
  // ───────────────────────────────────────────────────────────────────────────
  void _drawDoors(Canvas canvas) {
    for (final door in doors) {
      final fillPaint = Paint()
        ..color = door.open
            ? Colors.green.withOpacity(0.3)
            : Colors.red.withOpacity(0.6);
      canvas.drawRect(Rect.fromLTWH(door.x, door.y, door.w, door.h), fillPaint);

      final borderPaint = Paint()
        ..color = door.open ? Colors.green : Colors.red
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2;
      canvas.drawRect(
        Rect.fromLTWH(door.x, door.y, door.w, door.h),
        borderPaint,
      );

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

  // ───────────────────────────────────────────────────────────────────────────
  //  JUGADORES / GATOS
  //  FUENTE: WebSocket → mensajes "MOVE" y "PLAYER_LIST"
  //  Spritesheet info: game_data.json → mediaAssets (idle/run/jump_catN)
  //    idle_catN → tileWidth:16,  tileHeight:16
  //    run_catN  → tileWidth:20,  tileHeight:20
  //    jump_catN → tileWidth:20,  tileHeight:24
  // ───────────────────────────────────────────────────────────────────────────
  void _drawCatPlayer(Canvas canvas, RemotePlayer p) {
    final catNum = _extractCatNumber(p.cat);
    if (catNum == null) return;

    final animType = p.anim.isEmpty ? 'idle' : p.anim;
    final frameInfo = _frameSizes[animType] ?? _frameSizes['idle']!;
    final assetPath = 'assets/levels/media/${animType}_cat$catNum.png';
    final img = sprites.get(assetPath);
    if (img == null) return;

    final fw = frameInfo.w.toDouble();
    final fh = frameInfo.h.toDouble();
    final cols = (img.width / fw).floor();
    if (cols <= 0) return;

    // Frame animado: ~12fps con tick de 50ms
    final frame = (animTick ~/ 4) % frameInfo.frames;
    final srcCol = frame % cols;
    final srcRow = frame ~/ cols;
    final src = Rect.fromLTWH(srcCol * fw, srcRow * fh, fw, fh);

    // p.x, p.y vienen del WebSocket (coordenadas del servidor, Y hacia abajo)
    // Anclamos el sprite desde centro-abajo (anchorY ~0.7)
    final drawX = p.x - fw / 2;
    final drawY = p.y - fh * 0.7;

    canvas.save();
    if (p.dir == 'LEFT') {
      // Flip horizontal: transladar al origen del sprite, escalar -1 en X
      canvas.translate(drawX + fw, drawY);
      canvas.scale(-1, 1);
      canvas.drawImageRect(img, src, Rect.fromLTWH(0, 0, fw, fh), Paint());
    } else {
      canvas.drawImageRect(
        img,
        src,
        Rect.fromLTWH(drawX, drawY, fw, fh),
        Paint(),
      );
    }
    canvas.restore();

    // Nickname encima del gato
    // FUENTE: WebSocket → RemotePlayer.nickname
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
    if (cat.isEmpty) return null;
    final match = RegExp(r'cat(\d+)').firstMatch(cat);
    return match != null ? int.tryParse(match.group(1)!) : null;
  }

  // ───────────────────────────────────────────────────────────────────────────
  //  UI OVERLAY
  //  FUENTE: WebSocketService.connected / confirmedNickname / remotePlayers
  // ───────────────────────────────────────────────────────────────────────────
  void _drawUI(Canvas canvas, Size size) {
    final status = connected ? 'Conectado' : 'Desconectado';
    final playerCount = remotePlayers
        .where((p) => p.nickname != viewerNickname)
        .length;
    final text =
        '$status | Viewer: ${viewerNickname ?? "..."} | $playerCount jugadores';

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
