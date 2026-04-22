import 'dart:convert';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class Zone {
  final String name;
  final String type;
  final double x;
  final double y;
  final double width;
  final double height;
  final Color color;
  final String gameplayData;

  Zone({
    required this.name,
    required this.type,
    required this.x,
    required this.y,
    required this.width,
    required this.height,
    required this.color,
    required this.gameplayData,
  });

  factory Zone.fromJson(Map<String, dynamic> json) {
    return Zone(
      name: json['name'] ?? '',
      type: json['type'] ?? '',
      x: (json['x'] ?? 0).toDouble(),
      y: (json['y'] ?? 0).toDouble(),
      width: (json['width'] ?? 0).toDouble(),
      height: (json['height'] ?? 0).toDouble(),
      gameplayData: json['gameplayData'] ?? '',
      color: _getColorFromString(json['color'] ?? 'gray'),
    );
  }

  static Color _getColorFromString(String color) {
    switch (color.toLowerCase()) {
      case 'red':
        return Colors.red.withOpacity(0.5);
      case 'blue':
        return Colors.blue.withOpacity(0.5);
      case 'yellow':
        return Colors.yellow.withOpacity(0.5);
      case 'green':
        return Colors.green.withOpacity(0.5);
      default:
        return Colors.grey.withOpacity(0.5);
    }
  }

  Rect get rect => Rect.fromLTWH(x, y, width, height);
}

class TileMap {
  final List<List<int>> tiles;
  final int layerIndex;

  TileMap({required this.tiles, required this.layerIndex});

  factory TileMap.fromJson(Map<String, dynamic> json, int index) {
    List<List<int>> tileMap = [];
    if (json['tileMap'] != null) {
      for (var row in json['tileMap']) {
        List<int> tileRow = [];
        for (var tile in row) {
          tileRow.add(tile is int ? tile : -1);
        }
        tileMap.add(tileRow);
      }
    }
    return TileMap(tiles: tileMap, layerIndex: index);
  }

  int getTileAt(int x, int y) {
    if (y >= 0 && y < tiles.length && x >= 0 && x < tiles[y].length) {
      return tiles[y][x];
    }
    return -1;
  }
}

class AnimationData {
  final String id;
  final String name;
  final String mediaFile;
  final int startFrame;
  final int endFrame;
  final double fps;
  final bool loop;
  final double anchorX;
  final double anchorY;

  AnimationData({
    required this.id,
    required this.name,
    required this.mediaFile,
    required this.startFrame,
    required this.endFrame,
    required this.fps,
    required this.loop,
    required this.anchorX,
    required this.anchorY,
  });

  factory AnimationData.fromJson(Map<String, dynamic> json) {
    return AnimationData(
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
}

class LevelData {
  final List<Zone> zones;
  final List<TileMap> layers;
  final Map<String, AnimationData> animations;
  final String levelName;

  LevelData({
    required this.zones,
    required this.layers,
    required this.animations,
    required this.levelName,
  });

  static Future<LevelData> loadFromAssets() async {
    // Cargar zonas
    final zonesJsonString = await rootBundle.loadString('assets/levels/zones/level_000_zones.json');
    final zonesJson = jsonDecode(zonesJsonString);
    
    List<Zone> zones = [];
    if (zonesJson['zones'] != null) {
      zones = (zonesJson['zones'] as List)
          .map((z) => Zone.fromJson(z))
          .toList();
    }

    // Cargar tilemaps
    List<TileMap> layers = [];
    for (int i = 0; i <= 4; i++) {
      try {
        final layerString = await rootBundle.loadString('assets/levels/tilemaps/level_000_layer_00${i}.json');
        final layerJson = jsonDecode(layerString);
        layers.add(TileMap.fromJson(layerJson, i));
        debugPrint('✅ Capa $i cargada');
      } catch (e) {
        debugPrint('❌ Error cargando capa $i: $e');
      }
    }

    // Cargar animaciones
    Map<String, AnimationData> animations = {};
    try {
      final animationsString = await rootBundle.loadString('assets/levels/animations/animations.json');
      final animationsJson = jsonDecode(animationsString);
      if (animationsJson['animations'] != null) {
        for (var anim in animationsJson['animations']) {
          final animData = AnimationData.fromJson(anim);
          animations[animData.name] = animData;
          debugPrint('✅ Animación cargada: ${animData.name} -> ${animData.mediaFile}');
        }
      }
    } catch (e) {
      debugPrint('❌ Error cargando animaciones: $e');
    }

    debugPrint('📊 Resumen: ${zones.length} zonas, ${layers.length} capas, ${animations.length} animaciones');

    return LevelData(
      zones: zones,
      layers: layers,
      animations: animations,
      levelName: 'Nivel Principal',
    );
  }
}