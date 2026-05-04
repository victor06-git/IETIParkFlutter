import 'dart:convert';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class Zone {
  final String name;
  final String type;
  final double x, y, width, height;
  final Color color;
  final String gameplayData;

  Zone({
    required this.name, required this.type,
    required this.x, required this.y,
    required this.width, required this.height,
    required this.color, required this.gameplayData,
  });

  factory Zone.fromJson(Map<String, dynamic> json) => Zone(
    name: json['name'] ?? '',
    type: json['type'] ?? '',
    x: (json['x'] ?? 0).toDouble(),
    y: (json['y'] ?? 0).toDouble(),
    width: (json['width'] ?? 0).toDouble(),
    height: (json['height'] ?? 0).toDouble(),
    gameplayData: json['gameplayData'] ?? '',
    color: _color(json['color'] ?? 'gray'),
  );

  static Color _color(String c) => switch (c.toLowerCase()) {
    'red' => Colors.red.withOpacity(0.5),
    'blue' => Colors.blue.withOpacity(0.5),
    'yellow' => Colors.yellow.withOpacity(0.5),
    'green' => Colors.green.withOpacity(0.5),
    _ => Colors.grey.withOpacity(0.5),
  };

  Rect get rect => Rect.fromLTWH(x, y, width, height);
}

/// Sprite estático del nivel (game_data.json → levels[0].sprites[])
class SpriteData {
  final String name;
  final String type;
  final String imageFile;
  final double x, y, width, height;
  final String animationId;

  SpriteData({
    required this.name, required this.type, required this.imageFile,
    required this.x, required this.y,
    required this.width, required this.height,
    required this.animationId,
  });

  factory SpriteData.fromJson(Map<String, dynamic> json) => SpriteData(
    name: json['name'] ?? '',
    type: json['type'] ?? '',
    imageFile: json['imageFile'] ?? '',
    x: (json['x'] ?? 0).toDouble(),
    y: (json['y'] ?? 0).toDouble(),
    width: (json['width'] ?? 0).toDouble(),
    height: (json['height'] ?? 0).toDouble(),
    animationId: json['animationId'] ?? '',
  );
}

/// Una capa del nivel con su tilemap y referencia a la textura.
class LayerData {
  final String name;
  final String tilesTexturePath; // relativo a levels/, ej: "media/Terrain_and_Props.png"
  final double x, y;
  final int tileWidth, tileHeight;
  final bool visible;
  final List<List<int>> tiles;

  LayerData({
    required this.name, required this.tilesTexturePath,
    required this.x, required this.y,
    required this.tileWidth, required this.tileHeight,
    required this.visible, required this.tiles,
  });
}

class AnimationData {
  final String id, name, mediaFile;
  final int startFrame, endFrame;
  final double fps, anchorX, anchorY;
  final bool loop;
  final int frameWidth, frameHeight;

  AnimationData({
    required this.id, required this.name, required this.mediaFile,
    required this.startFrame, required this.endFrame,
    required this.fps, required this.loop,
    required this.anchorX, required this.anchorY,
    required this.frameWidth, required this.frameHeight,
  });

  factory AnimationData.fromJson(Map<String, dynamic> json, Map<String, (int, int)> mediaSizes) {
    final mediaFile = json['mediaFile'] as String? ?? '';
    final size = mediaSizes[mediaFile];
    return AnimationData(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      mediaFile: mediaFile,
      startFrame: json['startFrame'] ?? 0,
      endFrame: json['endFrame'] ?? 0,
      fps: (json['fps'] ?? 12.0).toDouble(),
      loop: json['loop'] ?? true,
      anchorX: (json['anchorX'] ?? 0.5).toDouble(),
      anchorY: (json['anchorY'] ?? 0.5).toDouble(),
      frameWidth: size?.$1 ?? 0,
      frameHeight: size?.$2 ?? 0,
    );
  }
}

class LevelData {
  final List<Zone> zones;
  final List<LayerData> layers;
  final List<SpriteData> sprites;
  final Map<String, AnimationData> animations;
  final String levelName;
  final double worldWidth;
  final double worldHeight;

  LevelData({
    required this.zones, required this.layers,
    required this.sprites,
    required this.animations, required this.levelName,
    required this.worldWidth, required this.worldHeight,
  });

  // Carga un nivel concreto por índice (0 = nivel 0, 1 = nivel 1)
  static Future<LevelData> loadLevel(int levelIndex) async {
    final gameDataStr = await rootBundle.loadString('assets/levels/game_data.json');
    final gameData = jsonDecode(gameDataStr);
    final levels = gameData['levels'] as List;
    final idx = levelIndex.clamp(0, levels.length - 1);
    final level = levels[idx] as Map<String, dynamic>;

    final viewportW = (level['viewportWidth'] ?? 320).toDouble();
    final viewportH = (level['viewportHeight'] ?? 180).toDouble();

    // Zonas
    final zonesFile = level['zonesFile'] as String?;
    final zones = <Zone>[];
    if (zonesFile != null) {
      try {
        final zonesStr = await rootBundle.loadString('assets/levels/$zonesFile');
        final zonesJson = jsonDecode(zonesStr);
        for (final z in (zonesJson['zones'] as List? ?? [])) {
          zones.add(Zone.fromJson(z));
        }
      } catch (e) { debugPrint('Error cargando zonas: $e'); }
    }

    // Capas
    final layerDefs = level['layers'] as List? ?? [];
    final layers = <LayerData>[];
    for (int i = 0; i < layerDefs.length; i++) {
      final ld = layerDefs[i];
      final tileMapFile = ld['tileMapFile'] as String?;
      if (tileMapFile == null) continue;
      try {
        final tmStr = await rootBundle.loadString('assets/levels/$tileMapFile');
        final tmJson = jsonDecode(tmStr);
        final tileMap = <List<int>>[];
        for (final row in (tmJson['tileMap'] as List? ?? [])) {
          tileMap.add((row as List).map((t) => t is int ? t : -1).toList());
        }
        layers.add(LayerData(
          name: ld['name'] ?? 'Layer $i',
          tilesTexturePath: ld['tilesSheetFile'] ?? '',
          x: (ld['x'] ?? 0).toDouble(),
          y: (ld['y'] ?? 0).toDouble(),
          tileWidth: ld['tilesWidth'] ?? 16,
          tileHeight: ld['tilesHeight'] ?? 16,
          visible: ld['visible'] ?? true,
          tiles: tileMap,
        ));
      } catch (e) { debugPrint('Error cargando capa $i: $e'); }
    }

    // Sprites
    final sprites = (level['sprites'] as List? ?? [])
        .map((s) => SpriteData.fromJson(s as Map<String, dynamic>))
        .toList();

    // Animaciones (compartidas entre niveles)
    final animations = <String, AnimationData>{};
    try {
      final animStr = await rootBundle.loadString('assets/levels/animations/animations.json');
      final animJson = jsonDecode(animStr);
      final mediaSizes = <String, (int, int)>{};
      for (final a in (gameData['mediaAssets'] as List? ?? [])) {
        final fileName = a['fileName'] as String? ?? '';
        final tw = a['tileWidth'] as int? ?? 0;
        final th = a['tileHeight'] as int? ?? 0;
        if (fileName.isNotEmpty && tw > 0 && th > 0) mediaSizes[fileName] = (tw, th);
      }
      for (final a in (animJson['animations'] as List? ?? [])) {
        final ad = AnimationData.fromJson(a, mediaSizes);
        animations[ad.id] = ad;
        animations[ad.name] = ad;
      }
    } catch (e) { debugPrint('Error cargando animaciones: $e'); }

    double ww = viewportW, wh = viewportH;
    for (final l in layers) {
      if (l.tiles.isEmpty) continue;
      ww = ww < l.x + l.tiles[0].length * l.tileWidth ? l.x + l.tiles[0].length * l.tileWidth : ww;
      wh = wh < l.y + l.tiles.length * l.tileHeight ? l.y + l.tiles.length * l.tileHeight : wh;
    }

    return LevelData(
      zones: zones, layers: layers, sprites: sprites, animations: animations,
      levelName: level['name'] as String? ?? 'Level $idx',
      worldWidth: ww, worldHeight: wh,
    );
  }

  // Compatibilidad: carga el nivel 0 por defecto
  static Future<LevelData> loadFromAssets() => loadLevel(0);
}