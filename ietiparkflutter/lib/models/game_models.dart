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

  AnimationData({
    required this.id, required this.name, required this.mediaFile,
    required this.startFrame, required this.endFrame,
    required this.fps, required this.loop,
    required this.anchorX, required this.anchorY,
  });

  factory AnimationData.fromJson(Map<String, dynamic> json) => AnimationData(
    id: json['id'] ?? '',
    name: json['name'] ?? '',
    mediaFile: json['mediaFile'] ?? '',
    startFrame: json['startFrame'] ?? 0,
    endFrame: json['endFrame'] ?? 0,
    fps: (json['fps'] ?? 12.0).toDouble(),
    loop: json['loop'] ?? true,
    anchorX: (json['anchorX'] ?? 0.5).toDouble(),
    anchorY: (json['anchorY'] ?? 0.5).toDouble(),
  );
}

class LevelData {
  final List<Zone> zones;
  final List<LayerData> layers;
  final Map<String, AnimationData> animations;
  final String levelName;
  final double worldWidth;
  final double worldHeight;

  LevelData({
    required this.zones, required this.layers,
    required this.animations, required this.levelName,
    required this.worldWidth, required this.worldHeight,
  });

  static Future<LevelData> loadFromAssets() async {
    // Cargar game_data.json
    final gameDataStr = await rootBundle.loadString('assets/levels/game_data.json');
    final gameData = jsonDecode(gameDataStr);
    final level = (gameData['levels'] as List).first;

    final viewportX = (level['viewportX'] ?? 0).toDouble();
    final viewportY = (level['viewportY'] ?? 0).toDouble();
    final viewportW = (level['viewportWidth'] ?? 320).toDouble();
    final viewportH = (level['viewportHeight'] ?? 180).toDouble();

    // Cargar zonas
    final zonesStr = await rootBundle.loadString('assets/levels/zones/level_000_zones.json');
    final zonesJson = jsonDecode(zonesStr);
    final zones = (zonesJson['zones'] as List? ?? [])
        .map((z) => Zone.fromJson(z)).toList();

    // Cargar capas con metadata del game_data
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
      } catch (e) {
        debugPrint('Error cargando capa $i: $e');
      }
    }

    // Cargar animaciones
    final animations = <String, AnimationData>{};
    try {
      final animStr = await rootBundle.loadString('assets/levels/animations/animations.json');
      final animJson = jsonDecode(animStr);
      for (final a in (animJson['animations'] as List? ?? [])) {
        final ad = AnimationData.fromJson(a);
        animations[ad.name] = ad;
      }
    } catch (e) {
      debugPrint('Error cargando animaciones: $e');
    }

    // Calcular world size
    double ww = viewportX + viewportW;
    double wh = viewportY + viewportH;
    for (final l in layers) {
      final cols = l.tiles.isEmpty ? 0 : l.tiles.map((r) => r.length).reduce((a, b) => a > b ? a : b);
      ww = [ww, l.x + cols * l.tileWidth].reduce((a, b) => a > b ? a : b);
      wh = [wh, l.y + l.tiles.length * l.tileHeight].reduce((a, b) => a > b ? a : b);
    }
    for (final z in zones) {
      ww = [ww, z.x + z.width].reduce((a, b) => a > b ? a : b);
      wh = [wh, z.y + z.height].reduce((a, b) => a > b ? a : b);
    }

    debugPrint(' ${zones.length} zonas, ${layers.length} capas, ${animations.length} anims, world: ${ww}x$wh');

    return LevelData(
      zones: zones, layers: layers, animations: animations,
      levelName: 'Tutorial level',
      worldWidth: ww, worldHeight: wh,
    );
  }
}